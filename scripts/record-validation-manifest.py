"""Bind retained validation to a source commit; never label a dirty tree a release candidate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def collect(root, build_root, allow_dirty=False, coverage_report=None, skip_policy=None):
    def git(*args):
        return subprocess.check_output(['git','-C',str(root),*args],text=True).strip()
    head=git('rev-parse','HEAD')
    expected=os.environ.get('GITHUB_SHA')
    if expected and expected != head:
        raise ValueError('GITHUB_SHA does not match checked-out HEAD')
    dirty=bool(git('status','--porcelain','--untracked-files=normal'))
    if dirty and not allow_dirty:
        raise ValueError('Release evidence requires a clean committed tree')
    reports=sorted(Path(build_root).glob('**/test-results/*/TEST-*.xml'))
    totals=dict(tests=0,failures=0,errors=0,skipped=0)
    skipped=[]
    artifacts={}
    for path in reports:
        suite=ET.parse(path).getroot()
        cases=suite.findall('testcase')
        counts=dict(tests=len(cases), failures=sum(c.find('failure') is not None for c in cases),
                    errors=sum(c.find('error') is not None for c in cases),
                    skipped=sum(c.find('skipped') is not None for c in cases))
        if suite.tag != 'testsuite' or not suite.attrib.get('name') or any(int(suite.attrib[k]) != v for k,v in counts.items()):
            raise ValueError('Invalid or inconsistent JUnit suite')
        for key, value in counts.items():
            totals[key]+=value
        for case in suite.findall('testcase'):
            if case.find('skipped') is not None:
                skipped.append(dict(suite=case.attrib.get('classname',suite.attrib['name']),
                                    display_suite=suite.attrib['name'],name=case.attrib['name']))
        artifacts[path.relative_to(build_root).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
    if skip_policy is not None:
        approved={}
        for item in skip_policy['allowed']:
            key=(item['suite'],item['name'])
            if key in approved or not isinstance(item['reason'],str) or not item['reason'].strip():
                raise ValueError('Invalid duplicate or unexplained skip policy')
            approved[key]=item['reason']
        for item in skipped:
            key=(item['suite'],item['name'])
            if key not in approved:
                raise ValueError(f'Unapproved skipped test: {key}')
            item['reason']=approved[key]
    if not totals['tests'] or totals['failures'] or totals['errors']:
        raise ValueError('Missing or failing JUnit evidence')
    coverage=Path(coverage_report) if coverage_report else Path(build_root)/'reports/kover/report.xml'
    if not coverage.is_file():
        raise ValueError('Merged coverage is required')
    artifacts[coverage.relative_to(build_root).as_posix()]=hashlib.sha256(coverage.read_bytes()).hexdigest()
    sources={}
    names=subprocess.check_output(['git','-C',str(root),'ls-files','-z']).decode().split('\0')
    for name in names:
        path=Path(root)/name
        if name and path.is_file() and path.suffix in {'.kt','.kts','.py','.json','.md','.yml','.yaml','.properties'}:
            sources[name]=hashlib.sha256(path.read_bytes()).hexdigest()
    return dict(head=head,tree=git('rev-parse','HEAD^{tree}'),dirty=dirty,release_candidate=not dirty,
        github_run_id=os.environ.get('GITHUB_RUN_ID'),tests=totals,skipped=skipped,
        source_sha256=sources,artifact_sha256=artifacts,
        limits='Record after successful gates; hashes and XML do not independently prove execution freshness or provider qualification.')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-root',type=Path,default=Path('build'))
    parser.add_argument('--allow-dirty',action='store_true')
    parser.add_argument('--coverage-report',type=Path)
    parser.add_argument('--skip-policy',type=Path,default=Path('config/test-skip-policy.json'))
    parser.add_argument('--output',type=Path,default=Path('build/reports/validation-manifest.json'))
    args=parser.parse_args()
    try:
        result=collect(Path.cwd(),args.build_root,args.allow_dirty,args.coverage_report,
                       json.loads(args.skip_policy.read_text(encoding='utf-8-sig')))
    except (ValueError,KeyError,OSError,ET.ParseError,subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:result[k] for k in ['head','dirty','release_candidate','tests']}))
