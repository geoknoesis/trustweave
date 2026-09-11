"""Archive the completed module run and render its scoped review; does not run tests."""
import argparse
from datetime import datetime, timezone
import hashlib
import html
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--test-results', type=Path, required=True)
parser.add_argument('--log', type=Path, required=True)
parser.add_argument('--before-source', type=Path, required=True)
args = parser.parse_args()
out = Path(__file__).resolve().parent
root = out.parents[2]
module = root / 'credentials/plugins/verifiable-intent'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_text(path):
    raw = path.read_bytes()
    return raw.decode('utf-16') if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else raw.decode('utf-8-sig')


log = read_text(args.log)
assert 'BUILD SUCCESSFUL' in log
assert '> Task :credentials:plugins:verifiable-intent:test\n' in log.replace('\r\n', '\n')
totals = dict(tests=0, failures=0, errors=0, skipped=0)
suites = []
(out / 'test-results').mkdir(exist_ok=True)
for path in sorted(args.test_results.glob('TEST-*.xml')):
    suite = ET.parse(path).getroot()
    record = dict(name=suite.attrib['name'], **{key: int(suite.attrib[key]) for key in totals})
    for key in totals:
        totals[key] += record[key]
    suites.append(record)
    shutil.copyfile(path, out / 'test-results' / path.name)
assert totals == dict(tests=87, failures=0, errors=0, skipped=0), totals
assert any(s['name'].endswith('PaymentSchemaValidationTest') and s['tests'] == 36 for s in suites)
(out / 'validation.log').write_text(log, encoding='utf-8')
(out / 'before.log').write_text(read_text(out / 'before.log'), encoding='utf-8')
for name in ['intent-operations.json', 'vi-kotlin-immediate.json', 'vi-kotlin-autonomous.json']:
    shutil.copyfile(module / 'build/reports' / name, out / name)
reverse = []
for name in ['python-immediate.json', 'python-autonomous.json']:
    value = json.loads(read_text(out / name))
    (out / name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    reverse.extend(value['outcomes'])
assert len(reverse) == 8
before = ET.parse(out / 'before.xml').getroot()
assert before.attrib['tests'] == '36' and before.attrib['failures'] == '26'
operations = json.loads((out / 'intent-operations.json').read_text(encoding='utf-8'))
old_scores = json.loads((root / 'docs/reviews/2026-09-06-production-readiness/follow-up-review/remediation-scores.json').read_text())
sources = sorted(module.glob('src/**/*.kt')) + sorted(module.glob('src/test/resources/*'))
sources += [module / 'build.gradle.kts', module / 'api/verifiable-intent.api', module / 'CONFORMANCE.md']
validation = {
    'recorded_utc': datetime.now(timezone.utc).isoformat(),
    'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
    'scope': 'Local dirty SDK tree; complete intent module run, not a full SDK or SaaS build',
    'finding': {'id': 'TW-PV-01', 'severity': 'Medium', 'status': 'closed',
                'title': 'Immediate and autonomous payment schema bypass without amount-range constraints',
                'before': {'tests': 36, 'failures': 26, 'meaning': 'Malformed signed inputs incorrectly accepted'},
                'after': {'tests': 36, 'failures': 0, 'errors': 0, 'skipped': 0}},
    'command': 'gradlew.bat :credentials:plugins:verifiable-intent:ktlintFormat :credentials:plugins:verifiable-intent:test :credentials:plugins:verifiable-intent:ktlintCheck :credentials:plugins:verifiable-intent:checkKotlinAbi --no-daemon --max-workers=1 --no-parallel "-Dorg.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8" -Pkotlin.compiler.execution.strategy=in-process --console=plain',
    'result': 'BUILD SUCCESSFUL in 1m 30s', 'totals': totals, 'suites': suites,
    'lint': 'passed; no baseline expansion', 'abi': 'passed; no public API change from this fix',
    'python_to_kotlin': {'fixture_cases': 6, 'result': 'passed in module test'},
    'kotlin_to_python': reverse,
    'reference_revision': '356c29635f1c44df7de02edb58699ca9f29bece6',
    'operations': operations,
    'source_sha256': {p.relative_to(root).as_posix(): digest(p) for p in sources if p.is_file()},
    'chain_verifier_before_sha256': digest(args.before_source),
    'score': {'overall': old_scores['overall'], 'decision': 'Retained; this scoped fix does not close the remaining production qualification gates', 'categories': old_scores['categories']},
    'limits': ['No current full-repository coverage or regression claim', 'No hosted CI, publication or live custody validation',
               'Cross-stack agreement is limited to existing vectors', 'Restore is a quiesced component exercise, not production PITR or pager qualification'],
}
(out / 'validation.json').write_text(json.dumps(validation, indent=2) + '\n', encoding='utf-8')
rows = ''.join(f'<tr><th>{html.escape(s["name"].rsplit(".", 1)[1])}</th><td>{s["tests"]}</td><td>0</td><td>0</td></tr>' for s in suites)
scores = ''.join(f'<tr><th>{html.escape(c["name"])}</th><td>{c["score"]:.1f}</td></tr>' for c in old_scores['categories'])
page = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TrustWeave payment validation follow-up</title><style>
*{{box-sizing:border-box}}body{{margin:0;background:#eef3f6;color:#173448;font:16px/1.65 system-ui,sans-serif}}main{{max-width:1080px;margin:auto;padding:32px 20px}}
header{{padding:30px;background:#103448;color:white;border-radius:14px}}h1{{font-size:clamp(1.8rem,4vw,2.8rem);line-height:1.2}}h2{{font-size:1.35rem}}.panel{{background:white;padding:24px;margin:20px 0;border:1px solid #cfdee7;border-radius:12px}}
.metrics{{display:flex;flex-wrap:wrap;gap:28px}}.metric strong{{display:block;font-size:2rem}}a{{color:#086783;overflow-wrap:anywhere}}header a{{color:#b7ecf5}}table{{width:100%;border-collapse:collapse}}th,td{{text-align:left;padding:10px;border-bottom:1px solid #dae4e9}}th{{overflow-wrap:anywhere}}.table{{overflow-x:auto}}.muted{{color:#526b7a}}code{{overflow-wrap:anywhere}}@media(max-width:600px){{main{{padding:12px}}header,.panel{{padding:18px}}th,td{{padding:7px;font-size:.85rem}}}}
</style></head><body><main>
<header><p>TRUSTWEAVE SDK · 7 SEPTEMBER 2026</p><h1>Payment validation gap closed</h1><p>Fresh intent-module evidence for the local working tree. The broader production-readiness score remains 8.7 / 10.</p>
<div class="metrics"><div class="metric"><strong>26</strong>invalid cases reproduced</div><div class="metric"><strong>87 / 87</strong>module tests passed</div><div class="metric"><strong>0</strong>failures or skips</div></div></header>
<section class="panel"><h2>TW-PV-01 · Medium · Fixed and verified</h2><p>Immediate-payment verification checked only that an amount existed. Autonomous payments could also bypass the amount checks when the mandate had no optional amount-range constraint. Real signed chains containing negative, quoted, fractional, overflowing, null or collection amounts—and malformed currencies—were accepted.</p>
<p>The shared payment validation now requires a non-negative integer within signed 64-bit range and an uppercase three-letter currency string in both modes. Currency syntax validation does not establish ISO currency membership. Valid zero, positive and maximum integer amounts remain accepted.</p>
<p>All 36 signed regression vectors now pass: 6 valid boundary cases and 30 rejection cases. Before the fix, 26 of those rejection assertions failed. No public API or persisted schema changed. Callers sending previously tolerated malformed values must correct their inputs.</p>
<p><a href="before.xml">Reproduced failures</a> · <a href="test-results/TEST-org.trustweave.credential.vi.PaymentSchemaValidationTest.xml">Corrected results</a> · <a href="../../../credentials/plugins/verifiable-intent/CONFORMANCE.md">Supported profile</a></p></section>
<section class="panel"><h2>Complete module validation</h2><p>The full intent suite, module lint and API compatibility check passed. The 87 cases executed in this run; the build also reused unchanged compilation tasks. The earlier Docker interruption no longer blocks local module evidence.</p>
<div class="table"><table><thead><tr><th>Suite</th><th>Tests</th><th>Failures</th><th>Skipped</th></tr></thead><tbody>{rows}</tbody></table></div>
<p>Existing interoperability checks also passed: six Python-issued cases verified in Kotlin, plus eight Kotlin-issued positive/adversarial checks in the pinned Python reference. No expanded conformance coverage is claimed.</p><p><a href="validation.json">Commands, counts and source hashes</a> · <a href="validation.log">Build log</a> · <a href="python-immediate.json">Immediate reference results</a> · <a href="python-autonomous.json">Autonomous reference results</a></p></section>
<section class="panel"><h2>Database failure and restore exercised again</h2><p>The loopback component test sent 100 requests at concurrency 8: 50 were authorized and 50 rejected. It stopped its primary database, observed a 503/storage-failure signal, restored a pg_dump backup into a separate PostgreSQL instance, compared complete ledger rows, and rejected replay of all 50 restored transactions.</p>
<p>Measured load duration: {operations['load_ms']} ms; handler p95: {operations['load_handler_p95_ms']} ms; restore exercise: {operations['restore_ms']} ms. These are local component measurements with pre-authorized fixtures and a quiesced backup, not production throughput, RTO, RPO or PITR guarantees. Test containers were cleaned up.</p><p><a href="intent-operations.json">Raw measurements</a> · <a href="../../operations/intent/README.md">Production recovery procedure</a></p></section>
<section class="panel"><h2>Score retained: 8.7 / 10</h2><p>The same six-category rubric is retained from the preceding assessment. This fix and renewed module evidence do not establish the missing production qualifications. These scores are engineering judgment, not certification.</p><table><thead><tr><th>Category</th><th>/ 10</th></tr></thead><tbody>{scores}</tbody></table>
<p>Category sum 52 / 6 = 8.6667, rounded to 8.7. No new full-SDK coverage measurement was made.</p><ol><li>Qualify managed signing, live provider access and durable key-loss recovery against an identified non-production resource.</li><li>Complete independent interoperability for every supported profile, including merchant trust and stateful constraints.</li><li>Exercise the deployed host, payment journal, PITR system and pager with agreed load and availability targets.</li><li>Validate the exact committed SDK/SaaS pair in hosted CI and verify the actual published artifact.</li></ol></section>
<footer><p><a href="../2026-09-06-production-readiness/follow-up-review/remediation.html">Previous assessment and remaining findings</a> · <a href="../2026-09-05-code-review/round-2/remediation.html">Historical round-2 report</a></p><p class="muted">Local uncommitted SDK changes; SaaS was not modified in this follow-up. No deployment or publication performed.</p></footer>
</main></body></html>'''
(out / 'index.html').write_text(page, encoding='utf-8')
print(json.dumps({'totals': totals, 'reverse_cases': len(reverse), 'report': str(out / 'index.html')}))
