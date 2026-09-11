"""Require named regression tests in JUnit XML, rejecting missing, failed or skipped evidence.

Run after a successful Gradle test build. XML alone does not establish freshness;
CI must produce results from the current tree before this gate runs.
"""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

import importlib.util as _importlib_util
from pathlib import Path as _Path

_spec = _importlib_util.spec_from_file_location("build_root", _Path(__file__).with_name("build_root.py"))
_build_root = _importlib_util.module_from_spec(_spec)
_spec.loader.exec_module(_build_root)


def check(build_root, policy):
    if not isinstance(policy, dict) or not isinstance(policy.get('requirements'), list) or not policy['requirements']:
        raise ValueError('Nonempty requirements are required')
    failures, evidence, ids = [], [], set()
    base = Path(build_root).resolve()
    for requirement in policy['requirements']:
        identifier = requirement['id']
        if not isinstance(identifier, str) or not identifier or identifier in ids:
            raise ValueError('Requirement IDs must be nonempty and unique')
        ids.add(identifier)
        module, suite, names = requirement['module'], requirement['suite'], requirement['tests']
        if not isinstance(names, list) or not names or any(not isinstance(n, str) or not n for n in names) or len(set(names)) != len(names):
            raise ValueError(f'{identifier}: nonempty unique test names required')
        folder = (base / module / 'test-results/test').resolve()
        if not folder.is_relative_to(base) or '/' in suite or '\\' in suite:
            raise ValueError(f'{identifier}: evidence path escapes build root')
        report = folder / f'TEST-{suite}.xml'
        if not report.is_file():
            failures.append(f'{identifier}: missing suite {suite}')
            continue
        root = ET.parse(report).getroot()
        cases = root.findall('testcase')
        if root.tag != 'testsuite' or root.attrib.get('name') != suite:
            raise ValueError(f'{identifier}: unexpected JUnit suite')
        counts = {'tests': len(cases), 'failures': sum(c.find('failure') is not None for c in cases),
                  'errors': sum(c.find('error') is not None for c in cases),
                  'skipped': sum(c.find('skipped') is not None for c in cases)}
        if any(int(root.attrib[key]) != value for key, value in counts.items()):
            raise ValueError(f'{identifier}: inconsistent JUnit counters')
        if counts['failures'] or counts['errors']:
            failures.append(f'{identifier}: suite has failing tests')
        for name in names:
            # Gradle appends () to ordinary Jupiter display names. Parameterized
            # invocations must be listed by their full XML name in this contract.
            matches = [c for c in cases if c.attrib.get('name') in (name, name + '()')]
            if len(matches) != 1:
                failures.append(f'{identifier}: expected one executed test: {name}')
            elif any(matches[0].find(tag) is not None for tag in ('failure', 'error', 'skipped')):
                failures.append(f'{identifier}: failed or skipped required test: {name}')
            else:
                evidence.append({'id': identifier, 'suite': suite, 'test': name})
    return {'requirements': len(ids), 'verified_tests': evidence, 'failures': failures}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-root', type=Path, default=None)
    parser.add_argument('--policy', type=Path, default=Path('config/testing-contract.json'))
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    try:
        root = _build_root.require_results(args.build_root or _build_root.resolve())
        result = check(root, json.loads(args.policy.read_text(encoding='utf-8-sig')))
    except (ValueError, KeyError, TypeError, OSError, ET.ParseError) as error:
        result = {'failures': [f'Invalid test evidence: {error}']}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))
    raise SystemExit(bool(result['failures']))


if __name__ == '__main__':
    main()
