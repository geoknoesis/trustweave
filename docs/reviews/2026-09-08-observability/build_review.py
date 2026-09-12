"""Archive the completed observability checks and render the scoped reassessment."""
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import html
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import yaml

out = Path(__file__).resolve().parent
root = out.parents[2]
build = Path(os.environ['LOCALAPPDATA']) / 'TrustWeave/gradle-build/trustweave/credentials/plugins'


def text(path):
    raw = path.read_bytes()
    return raw.decode('utf-16') if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else raw.decode('utf-8-sig')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


log = text(root / '.gradle/observability-final-3.log')
assert 'BUILD SUCCESSFUL' in log
assert 'SUCCESS' in text(root / '.gradle/observability-alerts.log')
totals = dict(tests=0, failures=0, errors=0, skipped=0)
suites = []
source_paths = []
for module in ['verifiable-intent', 'status-list/server']:
    folder = out / 'test-results' / module.replace('/', '-')
    folder.mkdir(parents=True, exist_ok=True)
    for path in sorted((build / module / 'test-results/test').glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        row = dict(module=module, name=suite.attrib['name'], **{k:int(suite.attrib[k]) for k in totals})
        suites.append(row)
        for key in totals:
            totals[key] += row[key]
        shutil.copyfile(path, folder / path.name)
    module_root = root / 'credentials/plugins' / module
    source_paths += [p for p in (module_root / 'src').rglob('*') if p.is_file()]
    source_paths += list((module_root / 'api').glob('*.api')) + [module_root/'build.gradle.kts']
assert totals == dict(tests=96, failures=0, errors=0, skipped=0), totals
for name in ['intent-operations.json', 'intent-metrics.prom']:
    shutil.copyfile(root / 'credentials/plugins/verifiable-intent/build/qualification' / name, out/name)
metric_check = json.loads((root/'.gradle/observability-metrics-check.json').read_text())
assert metric_check['exit_code'] == 0 and metric_check['input_sha256'] == sha(out/'intent-metrics.prom')
shutil.copyfile(root/'.gradle/observability-metrics-check.json',out/'metric-exposition-check.json')
shutil.copyfile(root / 'credentials/plugins/status-list/server/build/reports/status-list-diagnostics.log', out/'status-list-diagnostics.log')
events = (out/'status-list-diagnostics.log').read_text().splitlines()
assert len(events) == 20 and len({e.split('request_id=')[-1] for e in events}) == 20
assert all('secret' not in e and 'private-db' not in e for e in events)
for code in ['CONFIGURATION_FAILURE','STORAGE_FAILURE']:
    assert sum(f'error_code={code}' in e for e in events) == 10
(out/'validation.log').write_text(log, encoding='utf-8')
(out/'alerts.log').write_text(text(root/'.gradle/observability-alerts.log'),encoding='utf-8')
for name in ['alerts.yml','alerts.test.yml']:
    shutil.copyfile(root/'docs/operations/intent'/name,out/name)
alert_count = sum(len(t['alert_rule_test']) for t in yaml.safe_load((out/'alerts.test.yml').read_text())['tests'])
assert alert_count == 31
old = (root/'.gradle/observability-before.api').read_text().splitlines()
new = (root/'credentials/plugins/verifiable-intent/api/verifiable-intent.api').read_text().splitlines()
assert not (Counter(old)-Counter(new))
previous = json.loads((root/'docs/reviews/2026-09-07-intent-validation/validation.json').read_text())['score']
categories = previous['categories']
for c in categories:
    c['previous'] = c['score']
    if c['name'] == 'Observability and diagnosability':
        assert c['score'] == 8
        c['score'] = 9
        c['reason'] = 'Runtime ledger instrumentation, safe HTTP/log correlation, bounded concurrency tests, outage scrapes, freshness/availability alerts and operator runbooks now have executed local evidence. Deployed SLO, tracing and pager qualification remain.'
    else:
        c['assessment_note'] = 'Carried forward; not independently reassessed in this scoped review'
overall = float((sum(Decimal(str(c['score'])) for c in categories)/6).quantize(Decimal('0.1'),rounding=ROUND_HALF_UP))
assert overall == 8.8
score = dict(previous_overall=previous['overall'], overall=overall, categories=categories,
             rubric='Same six equally weighted categories; only observability reassessed. Engineering judgment, not certification.',
             arithmetic='53 / 6 = 8.8333, rounded half-up to 8.8')
(out/'scores.json').write_text(json.dumps(score,indent=2)+'\n',encoding='utf-8')
source_paths += [root/p for p in ['.github/workflows/ci.yml','.github/workflows/release-evidence.yml',
    'docs/operations/observability.md','docs/operations/intent/README.md','docs/operations/intent/alerts.yml','docs/operations/intent/alerts.test.yml']]
validation = dict(recorded_utc=datetime.now(timezone.utc).isoformat(),head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
    scope='Local dirty SDK tree; complete intent and status-list server module tests. No SaaS, full SDK build, hosted CI or deployment claimed.',
    tests=totals,suites=suites,alerts=dict(rules=9,assertions=alert_count,result='passed with pinned Prometheus 3.5.0 promtool'),
    metric_exposition='Fresh component scrape passed promtool check metrics',
    correlation=dict(concurrent_requests=20,unique_ids=20,matched_events=20,configuration_failures=10,sql_failures=10,redaction='passed'),
    bounded_metrics=dict(observations=80000,workers=8,series_growth=0),
    abi=dict(result='passed',removed_lines=0,added_lines=list((Counter(new)-Counter(old)).elements())),
    lint='Both affected modules passed; no baseline expansion',
    source_sha256={p.relative_to(root).as_posix():sha(p) for p in sorted(set(source_paths))},
    build_command='gradlew.bat :credentials:plugins:verifiable-intent:ktlintFormat :credentials:plugins:status-list:server:ktlintFormat :credentials:plugins:verifiable-intent:test :credentials:plugins:status-list:server:test :credentials:plugins:verifiable-intent:checkKotlinAbi :credentials:plugins:status-list:server:checkKotlinAbi :credentials:plugins:verifiable-intent:ktlintCheck :credentials:plugins:status-list:server:ktlintCheck --no-daemon --max-workers=1 --no-parallel "-Dorg.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8" -Pkotlin.compiler.execution.strategy=in-process',
    tool_image='prom/prometheus@sha256:63805ebb8d2b3920190daf1cb14a60871b16fd38bed42b857a3182bc621f4996',
    scores=score,
    limits=['Host must wire protected metrics export, bounded periodic health polling and a production SLF4J backend',
            'No external Alertmanager/pager delivery, deployment SLO or full distributed tracing validation',
            'Metrics are process-local; health gauges describe the most recent poll; no durable audit-log claim',
            'No fresh full-repository coverage or provider-custody qualification'])
(out/'validation.json').write_text(json.dumps(validation,indent=2)+'\n',encoding='utf-8')
rows=''.join(f'<tr><th>{html.escape(c["name"])}</th><td>{c["previous"]:.1f}</td><td>{c["score"]:.1f}</td></tr>' for c in categories)
page=f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>TrustWeave observability reassessment</title><style>
*{{box-sizing:border-box}}body{{margin:0;background:#eef3f6;color:#173448;font:16px/1.65 system-ui,sans-serif}}main{{max-width:1080px;margin:auto;padding:32px 20px}}header{{padding:30px;background:#103448;color:white;border-radius:14px}}h1{{font-size:clamp(1.8rem,4vw,2.8rem);line-height:1.2}}h2{{font-size:1.35rem}}.panel{{background:white;padding:24px;margin:20px 0;border:1px solid #cfdee7;border-radius:12px}}.metrics{{display:flex;flex-wrap:wrap;gap:30px}}.metric strong{{display:block;font-size:2.4rem}}a{{color:#086783;overflow-wrap:anywhere}}table{{width:100%;border-collapse:collapse}}th,td{{text-align:left;padding:12px;border-bottom:1px solid #dae4e9}}code{{overflow-wrap:anywhere}}.muted{{color:#526b7a}}@media(max-width:600px){{main{{padding:12px}}header,.panel{{padding:18px}}th,td{{padding:7px;font-size:.85rem}}}}</style></head><body><main>
<header><p>TRUSTWEAVE SDK · 8 SEPTEMBER 2026</p><h1>Observability and diagnosability improved</h1><div class="metrics"><div class="metric"><strong>8.0 → 9.0</strong>observability / 10</div><div class="metric"><strong>8.7 → 8.8</strong>overall / 10</div></div><p>Runtime instrumentation and verified diagnostics now replace the earlier reliance on a test-only metric implementation.</p></header>
<section class="panel"><h2>What changed</h2><ol><li><strong>Runtime ledger metrics.</strong> Schema initialization, reservation, reconciliation and health reads now emit bounded counts, cumulative latency histograms and in-flight gauges. Six fixed outcomes distinguish normal rejection, invalid input, SQL failure, unexpected failure and cancellation from success. Identifiers and exception text never become labels.</li><li><strong>Outage-safe export.</strong> The metrics snapshot makes no database calls. SQL failure does not disable scraping. Failed health polling removes stale pending/age gauges; a successful-poll timestamp detects a polling job that stops. Unknown legacy ages stay unknown.</li><li><strong>HTTP-to-log correlation.</strong> Both status-list routes generate a random request ID, return it in response headers and error bodies, and include it in redacted failure events. Incoming IDs are ignored. Configuration, storage, timeout and internal failures have fixed diagnostic codes.</li><li><strong>Actionable alerts.</strong> Nine rules cover SQL errors, backlog, latency, stale/failed health polls, unknown/old pending ages, failed scrapes, disappeared targets and missing instrumentation. Each points to an operator procedure.</li><li><strong>Repeatable checks.</strong> CI and release workflows validate real metric exposition with promtool and retain the metrics and correlation artifacts.</li></ol></section>
<section class="panel"><h2>Executed evidence</h2><table><tbody><tr><th>Affected-module tests</th><td>96 passed; 0 failures, errors or skips (92 intent + 4 status-list server)</td></tr><tr><th>Concurrency and bounds</th><td>80,000 observations on 8 workers; fixed series count, coherent cumulative buckets and no leaked in-flight work</td></tr><tr><th>Correlation</th><td>20 concurrent HTTP failures; 20 unique generated IDs; exactly one matching redacted log event each</td></tr><tr><th>Fault and recovery</th><td>Real PostgreSQL stopped; authorization returned 503 while metrics stayed available; failed health gauges cleared and recovered after independent restore</td></tr><tr><th>Alert rules</th><td>31 assertions passed, including firing/recovery and cancellation/rejection exclusion</td></tr><tr><th>Prometheus exposition</th><td>Fresh runtime scrape passed promtool check metrics</td></tr><tr><th>Lint and API</th><td>Both modules passed; additive diagnostics API; existing one-argument ledger constructor retained</td></tr></tbody></table><p><a href="validation.json">Source hashes and validation</a> · <a href="validation.log">Final build log</a> · <a href="status-list-diagnostics.log">Actual correlation events</a> · <a href="intent-metrics.prom">Actual metrics</a> · <a href="alerts.test.yml">Alert vectors</a> · <a href="alerts.log">Rule result</a></p></section>
<section class="panel"><h2>Why this earns 9.0, and what remains for 10</h2><p>The previous 8.0 score credited guidance, component metrics and three tested alerts. The implementation now instruments actual library operations, preserves diagnostic access during storage failure, verifies response-to-log correlation through a real logging backend, and detects gaps in the monitoring pipeline itself.</p><p>The remaining point requires evidence from the intended deployed host: agreed SLO thresholds and dashboards; queue, pool and distributed-trace coverage; configured log retention/access controls; and a notification delivered and resolved through the real Alertmanager/pager route. Instrumentation across other SDK/provider paths also remains broader work. No external notification was sent and no deployment qualification is claimed.</p><p>The collector starts no listeners or background threads. Hosts must wire protected metrics export, bounded periodic health polling and their SLF4J backend. The local component exercise uses pre-authorized requests and a quiesced restore; it does not qualify production throughput, PITR or payment-journal authentication.</p></section>
<section class="panel"><h2>Reassessment using the unchanged rubric</h2><table><thead><tr><th>Category</th><th>Previous</th><th>Current</th></tr></thead><tbody>{rows}</tbody></table><p>Only observability was reassessed; other categories are carried forward. 53 / 6 = 8.8333, rounded half-up to <strong>8.8 / 10</strong>. This is engineering judgment, not certification. No full-SDK coverage increase is claimed.</p></section>
<section class="panel"><h2>Operator handoff</h2><p>Share one diagnostics collector among ledger instances for the same database. Poll health using a monitoring role and a bounded scheduler; export the cached snapshot on the host's protected metrics route. Use Prometheus rate/increase for process-local counters and check health freshness before interpreting gauges. Do not treat a timeout as confirmed payment non-execution.</p><p><a href="../../operations/observability.md">Logging and telemetry contract</a> · <a href="../../operations/intent/README.md">Wiring and alert runbooks</a> · <a href="scores.json">Scores JSON</a> · <a href="../2026-09-07-intent-validation/index.html">Previous payment validation report</a></p><p class="muted">SLF4J log capture uses its documented <a href="https://www.slf4j.org/api/org/slf4j/simple/SimpleLogger.html">SimpleLogger configuration</a> in tests only. The runtime library does not select the application's backend.</p></section>
<footer><p class="muted">Local uncommitted SDK tree. No SaaS change, commit, hosted CI run, release or deployment performed in this follow-up.</p></footer></main></body></html>'''
(out/'index.html').write_text(page,encoding='utf-8')
print(json.dumps(dict(tests=totals,alert_assertions=alert_count,overall=overall,report=str(out/'index.html'))))
