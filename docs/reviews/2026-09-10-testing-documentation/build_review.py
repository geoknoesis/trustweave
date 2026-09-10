"""Archive scoped testing/documentation evidence; do not convert planned gates into passes."""
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

out = Path(__file__).resolve().parent
root = out.parents[2]
build = Path(os.environ['LOCALAPPDATA']) / 'TrustWeave/gradle-build/trustweave'


def read(path):
    data = path.read_bytes()
    return data.decode('utf-16') if data.startswith((b'\xff\xfe', b'\xfe\xff')) else data.decode('utf-8-sig')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


log = read(root / '.gradle/testing-final-2.log')
assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
modules = ['observability', 'credentials/plugins/oidc4vp', 'wallet/plugins/cloud',
           'wallet/plugins/file', 'testkit', 'distribution/examples']
suites, counts = [], []
totals = dict(tests=0, failures=0, errors=0, skipped=0)
for module in modules:
    row = dict(module=module, tests=0, failures=0, errors=0, skipped=0)
    folder = out / 'test-results' / module.replace('/', '-')
    folder.mkdir(parents=True, exist_ok=True)
    for path in sorted((build / module / 'test-results/test').glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        item = dict(module=module, suite=suite.attrib['name'], **{k:int(suite.attrib[k]) for k in totals})
        suites.append(item)
        for key in totals:
            row[key] += item[key]
            totals[key] += item[key]
        shutil.copyfile(path, folder / path.name)
    assert row['tests'] > 0
    assert not row['failures'] and not row['errors'] and not row['skipped'], row
    row['scope'] = 'CloudRecoveryTest only' if module.endswith('/cloud') else 'DocumentationExampleTest and TrustWeaveTestFixtureTest only' if module == 'testkit' else 'Full module suite'
    counts.append(row)

checks = json.loads((root / '.gradle/testing-tool-checks.json').read_text())
assert all(item['exit_code'] == 0 for item in checks)
assert 'Ran 39 tests' in checks[0]['stderr']
required = json.loads((root / '.gradle/testing-required-results.json').read_text())
assert not required['failures'] and len(required['verified_tests']) == 19
junit_before = json.loads((root / '.gradle/testing-junit-inventory.json').read_text())
junit_after = json.loads((root / '.gradle/testing-junit-final.json').read_text())
assert len(junit_before['failures']) == 8 and not junit_after['failures']
docs = json.loads((root / '.gradle/testing-documentation.json').read_text())
assert not docs['errors'] and len(docs['source_examples']) == 5
coverage_file = build / 'observability/reports/kover/report.xml'
coverage = {c.attrib['type']: dict(covered=int(c.attrib['covered']), missed=int(c.attrib['missed'])) for c in ET.parse(coverage_file).getroot().findall('counter')}
for value in coverage.values():
    value['percent'] = round(100 * value['covered'] / (value['covered'] + value['missed']), 2)
assert coverage['LINE']['percent'] >= 97 and coverage['BRANCH']['percent'] >= 85
example_tasks = sorted(set(re.findall(r'^> Task :distribution:examples:(run\w+)', log, re.M)))
assert len(example_tasks) >= 20, example_tasks

evidence = out / 'evidence'
evidence.mkdir(exist_ok=True)
for name in ['testing-tool-checks.json', 'testing-required-results.json', 'testing-junit-inventory.json',
             'testing-junit-final.json', 'testing-documentation.json', 'testing-docker-prerequisite.json']:
    shutil.copyfile(root / '.gradle' / name, evidence / name)
shutil.copyfile(coverage_file, evidence / 'host-coverage.xml')
(evidence / 'validation.log').write_text(log, encoding='utf-8')

scores = json.loads((root / 'docs/reviews/2026-09-10-host-observability/scores.json').read_text())
scores['previous_overall'] = scores['overall']
for category in scores['categories']:
    category['previous'] = category['score']
    category.pop('assessment_note', None)
    if category['name'] == 'Testing and documentation':
        category['score'] = 9.0
        category['reason'] = 'Eight undiscovered regressions now execute; compiled JUnit and named-result gates, strict coverage policies, stronger host branch tests and five protected executable source examples have local evidence. Full current-tree SDK integration/merged coverage, wider snippet review and complete supported-profile independent conformance remain open.'
    else:
        category['assessment_note'] = 'Carried forward, not reassessed in this scoped review'
scores['overall'] = float((sum(Decimal(str(c['score'])) for c in scores['categories']) / 6).quantize(Decimal('.1'), rounding=ROUND_HALF_UP))
assert scores['overall'] == 9.0
scores['rubric'] = 'Same six equally weighted categories; only Testing and documentation reassessed. Engineering judgment, not certification.'
scores['arithmetic'] = '54.1 / 6 = 9.0167, rounded half-up to 9.0'
scores['scope'] = 'Scoped local SDK testing/documentation improvements; target 10.0 not achieved'
scores['reassessment_basis'] = ['Discovery defects were reproduced in actual compiled classes and corrected in source.',
    'Named JUnit XML gates prevent required regressions from disappearing or being skipped.',
    'Host coverage improved without production-code changes, exclusions or lowered global floors.',
    'Copyable fixture/host examples now compile, execute and have non-removable source contracts.',
    'Remaining full SDK, interoperability, snippet review and hosted evidence prevent a 10.0 score.']
(out / 'scores.json').write_text(json.dumps(scores, indent=2) + '\n', encoding='utf-8')

sources = [root / name for name in [
    'scripts/check-coverage-policy.py', 'scripts/test_check_coverage_policy.py',
    'scripts/check-junit-contract.py', 'scripts/test_check_junit_contract.py',
    'scripts/check-test-evidence.py', 'scripts/test_check_test_evidence.py',
    'scripts/check-documentation.py', 'scripts/test_check_documentation.py',
    'config/coverage-policy.json', 'config/host-coverage-policy.json', 'config/testing-contract.json',
    'config/documentation-contract.json', '.github/workflows/ci.yml', '.github/workflows/release-evidence.yml',
    '.github/workflows/docs-check.yml', 'docs/contributing/testing-guidelines.md',
    'docs/contributing/testing/integration-testing.md', 'docs/contributing/testing/acceptance.md',
    'docs/api-reference/advanced/testing-strategies.md', 'docs/operations/host/example.md', 'docs/operations/host/README.md',
    'settings.gradle.kts', 'build.gradle.kts', 'distribution/examples/build.gradle.kts']]
for module in modules:
    sources.extend(p for p in (root / module / 'src').rglob('*') if p.is_file())
    sources.append(root / module / 'build.gradle.kts')
validation = dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
    head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
    scope='Local uncommitted SDK; listed module scopes only. No full SDK, hosted CI, release or SaaS pass claimed.',
    tests=totals, python_tests=39, modules=counts, suites=suites,
    junit_contract=dict(classes=junit_after['classes_scanned'], methods=junit_after['test_methods'], invalid_before=8, invalid_after=0,
                        limits='Inventory of available compiled JVM classes; not a fresh full-SDK execution or coverage measurement'),
    required_results=dict(requirements=required['requirements'], verified_tests=len(required['verified_tests'])),
    host_coverage=coverage, host_baseline=dict(LINE=round(222/230*100, 2), BRANCH=round(110/148*100, 2)),
    documentation=dict(markdown_files=docs['markdown_files'], kotlin_blocks=docs['kotlin_blocks'], source_examples=5, errors=0),
    executed_example_tasks=example_tasks, lint='All five affected Kotlin modules passed, without expanding baselines',
    source_sha256={p.relative_to(root).as_posix():sha(p) for p in sorted(set(sources))},
    artifact_sha256={p.relative_to(out).as_posix():sha(p) for p in sorted(out.rglob('*')) if p.is_file() and ('evidence' in p.parts or 'test-results' in p.parts)},
    limitations=['Full cloud suite stopped at unavailable Docker-backed S3 test; no pass claimed for that run.',
                 'Cloud and testkit results are explicitly filtered; other listed modules have full suite results.',
                 'No fresh full-SDK merged coverage or full independent supported-profile conformance.',
                 'Five exact examples execute; remaining Markdown fragments are inventoried, not all compiled.',
                 'Hosted workflow changes are configured and parsed locally, not executed in hosted CI.'], scores=scores)
(out / 'validation.json').write_text(json.dumps(validation, indent=2) + '\n', encoding='utf-8')
rows = ''.join(f'<tr><th>{html.escape(m["module"])}</th><td>{m["tests"]}</td><td>{html.escape(m["scope"])}</td></tr>' for m in counts)
score_rows = ''.join(f'<tr><th>{html.escape(c["name"])}</th><td>{c["previous"]:.1f}</td><td>{c["score"]:.1f}</td></tr>' for c in scores['categories'])
page = f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TrustWeave testing and documentation reassessment</title><style>
*{{box-sizing:border-box}}body{{margin:0;background:#eef3f6;color:#173448;font:16px/1.65 system-ui,sans-serif}}main{{max-width:1080px;margin:auto;padding:32px 20px}}header{{padding:30px;background:#103448;color:white;border-radius:14px}}h1{{font-size:clamp(1.8rem,4vw,2.8rem);line-height:1.2}}h2{{font-size:1.35rem}}.panel{{background:white;padding:24px;margin:20px 0;border:1px solid #cfdee7;border-radius:12px}}.metrics{{display:flex;flex-wrap:wrap;gap:24px}}.metric strong{{display:block;font-size:2rem}}a{{color:#086783;overflow-wrap:anywhere}}table{{width:100%;border-collapse:collapse}}th,td{{text-align:left;padding:10px;border-bottom:1px solid #dae4e9;overflow-wrap:anywhere}}.notice{{border-left:5px solid #b87516}}code{{overflow-wrap:anywhere}}.muted{{color:#526b7a}}@media(max-width:600px){{main{{padding:12px}}header,.panel{{padding:18px}}th,td{{padding:7px;font-size:.85rem}}#execution,#execution tbody,#execution tr,#execution th,#execution td{{display:block}}#execution thead{{display:none}}#execution tr{{padding:8px 0;border-bottom:1px solid #dae4e9}}#execution th,#execution td{{border:0}}#execution td:nth-child(2)::before{{content:"Passing tests: ";font-weight:600}}#execution td:nth-child(3)::before{{content:"Scope: ";font-weight:600}}}}
</style></head><body><main><header><p>TRUSTWEAVE SDK &middot; 10 SEPTEMBER 2026</p><h1>Testing and documentation strengthened</h1><p>Ignored regression tests now execute, validation tools reject misleading evidence, and copyable examples stay synchronized with tested source.</p><div class="metrics"><div class="metric"><strong>8.5 &rarr; 9.0</strong>testing and documentation / 10</div><div class="metric"><strong>{totals['tests']} + 39</strong>Kotlin and validation-tool tests passed</div><div class="metric"><strong>9.0 / 10</strong>overall engineering score</div></div></header>
<section class="panel notice"><h2>Target: 10/10 remains open</h2><p>This review improves the existing combined category to <strong>9.0/10</strong>. It does not assign a perfect score to partial evidence. A current-tree full SDK integration/coverage run, complete independent conformance for supported profiles, wider snippet review and hosted release validation remain necessary. Docker did not respond locally; the blocked S3 integration run was stopped and is not counted as passing.</p><p><a href="../../contributing/testing/acceptance.md">Concrete acceptance checklist and remaining tasks</a> &middot; <a href="scores.json">Scoring rationale</a></p></section>
<section class="panel"><h2>Defects fixed</h2><p>The compiled JVM audit found <strong>eight annotated methods with non-void return types</strong>, which JUnit had ignored: six OIDC4VP signature/URL/error regressions, wallet recovery cancellation and typed encryption-key validation. Explicit Kotlin <code>Unit</code> signatures now make them discoverable, and their named XML results all pass.</p><p>The coverage checker previously accepted empty or invalid policies. It now rejects empty scopes, invalid or non-finite percentages, unknown metrics, duplicate scopes/counters, malformed counts and missing evidence. Existing global coverage floors are unchanged.</p><p>Documentation source markers could previously disappear or point to local-only sources. The checker now rejects missing/duplicated required markers, wrong fence languages, ignored/non-Kotlin/outside-repository sources and drift. Five required examples are recorded in a versioned contract.</p><p><a href="evidence/testing-junit-inventory.json">Before: actual invalid JVM descriptors</a> &middot; <a href="evidence/testing-junit-final.json">After: compiled audit</a> &middot; <a href="evidence/testing-required-results.json">Named executed regressions</a></p></section>
<section class="panel"><h2>Tests and coverage</h2><p>Seven new host edge-case tests cover all host labels, client/server status classification, unknown method cardinality, queue timeout and admission, exception/cancellation cleanup, original connection and rollback semantics, absent/closed pools, and authentication boundaries. Two complete documentation test files verify fixture isolation/cleanup and authenticated host metrics.</p><p>Measured host line coverage increases from <strong>96.52% to {coverage['LINE']['percent']:.2f}%</strong>, and branch coverage from <strong>74.32% to {coverage['BRANCH']['percent']:.2f}%</strong>. The new package floor is 97% lines and 85% branches. No production-code exclusions or reduced repository-wide thresholds were used. This is host coverage, not merged SDK coverage.</p><table id="execution"><thead><tr><th>Module</th><th>Passing tests</th><th>Executed scope</th></tr></thead><tbody>{rows}</tbody></table><p>Listed XML totals: {totals['tests']} tests, zero failures/errors/skips. All five affected Kotlin modules passed lint. The documentation task also executed <strong>{len(example_tasks)} registered example tasks</strong>. These counts exclude the interrupted Docker-backed run.</p><p>The validation tools have <strong>39 passing tests</strong>, including real javac-compiled JUnit fixtures and adversarial policy/XML/source-marker cases. The compiled audit covers {junit_after['test_methods']} available annotated methods; that inventory is not a claim that every SDK test ran again.</p><p><a href="evidence/host-coverage.xml">Kover XML</a> &middot; <a href="evidence/testing-tool-checks.json">Validation-tool results</a> &middot; <a href="evidence/validation.log">Successful scoped Gradle log</a> &middot; <a href="validation.json">Suites, filters, task names, source hashes and limits</a></p></section>
<section class="panel"><h2>Documentation and CI</h2><p>Three overlapping testing pages now use accurate commands, real source references and explicit evidence boundaries. The fixture and host examples are full compiled tests. Required source markers cannot be removed silently. The checker reports {docs['markdown_files']} Markdown files, {docs['kotlin_blocks']} inventoried Kotlin blocks, five synchronized examples and zero errors. Other blocks remain fragments or unqualified examples; they are not all compiler-verified.</p><p>CI and release workflows now test the validation tools, check current documentation, reject invalid compiled tests, require named passing regressions, enforce host coverage, and retain diagnostics. The docs workflow executes the new examples and triggers for nested Markdown. YAML and local commands were validated; hosted jobs were not run.</p><p><a href="../../contributing/testing-guidelines.md">Rewritten testing guide</a> &middot; <a href="../../operations/host/example.md">Executable host example</a> &middot; <a href="../../../config/testing-contract.json">Requirement-to-test contract</a> &middot; <a href="../../../config/documentation-contract.json">Required source examples</a></p></section>
<section class="panel"><h2>Updated scorecard</h2><table><thead><tr><th>Category</th><th>Before</th><th>Now</th></tr></thead><tbody>{score_rows}</tbody></table><p>The same six equal weights give 54.1 / 6 = 9.0167, rounded to <strong>9.0/10</strong>. Only testing/documentation is reassessed. Observability remains 9.6; the other categories carry forward unchanged. Scores are engineering judgment, not certification.</p><p><a href="../2026-09-10-host-observability/index.html">Previous observability assessment</a> &middot; <a href="../../operations/vi-cross-stack.md">Remaining independent conformance matrix</a> &middot; <a href="evidence/testing-docker-prerequisite.json">Docker prerequisite result</a></p></section><footer><p class="muted">Local uncommitted SDK work. No deployment, external messages, full SDK pass or published-artifact qualification is claimed.</p></footer></main></body></html>'''
(out / 'index.html').write_text(page, encoding='utf-8')
notice = '<section class="panel" id="testing-documentation-update"><strong>Testing/documentation update:</strong> Eight ignored regression tests now execute; stronger validation gates and executable documentation raise the combined category to 9.0 / 10 and overall engineering score to 9.0. The 10/10 target remains open. <a href="../2026-09-10-testing-documentation/index.html">Read the evidence and remaining acceptance tasks</a>.</section>'
for directory in ['2026-09-10-host-observability', '2026-09-09-notification-delivery', '2026-09-07-intent-validation']:
    path = root / 'docs/reviews' / directory / 'index.html'
    text = path.read_text(encoding='utf-8')
    if 'id="testing-documentation-update"' not in text:
        path.write_text(text.replace('<main>', '<main>' + notice, 1), encoding='utf-8')
print(json.dumps({'tests': totals, 'python_tests': 39, 'examples': len(example_tasks), 'score': 9.0}))
