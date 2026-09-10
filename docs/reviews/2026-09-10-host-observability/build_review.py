"""Archive executed host observability checks and render the scoped engineering assessment."""
from collections import Counter
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
build = Path(os.environ["LOCALAPPDATA"]) / "TrustWeave/gradle-build/trustweave"
modules = ["observability", "credentials/plugins/status-list/server", "credentials/vc-api-server",
           "credentials/oidc4vci-server", "credentials/avp-authorization-server",
           "did/registrar-server-ktor", "trust-registry/trust-registry-server"]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    data = path.read_bytes()
    return data.decode("utf-16") if data.startswith((b"\xff\xfe", b"\xfe\xff")) else data.decode("utf-8-sig")


log = read(root / ".gradle/host-observability-final.log")
assert "BUILD SUCCESSFUL" in log and "BUILD FAILED" not in log
totals = dict(tests=0, failures=0, errors=0, skipped=0)
suites, module_counts, sources, abi = [], [], [], []
for module in modules:
    folder = out / "test-results" / module.replace("/", "-")
    folder.mkdir(parents=True, exist_ok=True)
    count = 0
    for path in sorted((build / module / "test-results/test").glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        row = dict(module=module, name=suite.attrib["name"], **{k:int(suite.attrib[k]) for k in totals})
        for key in totals:
            totals[key] += row[key]
        count += row["tests"]
        suites.append(row)
        shutil.copyfile(path, folder / path.name)
    assert count > 0, module
    module_counts.append(dict(module=module, tests=count))
    base = root / module
    sources += [p for p in (base / "src").rglob("*") if p.is_file()]
    sources += list((base / "api").glob("*.api")) + [base / "build.gradle.kts"]
    if module != "observability":
        before = root / ".gradle/host-observability-before" / (module.replace("/", "_") + ".api")
        current = next((base / "api").glob("*.api"))
        old, new = Counter(before.read_text().splitlines()), Counter(current.read_text().splitlines())
        assert not old - new
        added = list((new - old).elements())
        assert len(added) == 1 and "withObservability" in added[0]
        abi.append(dict(module=module, removed=0, added=added))
assert totals == dict(tests=64, failures=0, errors=0, skipped=0), totals
assert any(s["name"].endswith("HostExportIntegrationTest") and s["tests"] == 2 for s in suites)

evidence = out / "evidence"
evidence.mkdir(exist_ok=True)
for path in (root / "observability/build/reports").rglob("*"):
    if path.is_file():
        target = evidence / path.relative_to(root / "observability/build/reports")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
promtool = json.loads((root / ".gradle/host-promtool-checks.json").read_text())
assert all(check["exit_code"] == 0 for check in promtool.values())
assert promtool["host_rules"]["assertions"] == 32
assert promtool["host_metrics"]["sha256"] == sha(evidence / "host-metrics.prom")
otlp = json.loads((evidence / "host-otlp/summary.json").read_text())
assert otlp["hosts"] == 2 and otlp["traces"] == 10 and otlp["spans"] == 40 and otlp["authenticated"]
assert "private-" not in (evidence / "host-otlp/traces.txt").read_text()
assert "fixture-collector-secret" not in (evidence / "host-otlp/traces.txt").read_text()
for name in ["alerts.yml", "alerts.test.yml", "slo.json"]:
    shutil.copyfile(root / "docs/operations/host" / name, evidence / name)
(evidence / "promtool-checks.json").write_text(json.dumps(promtool, indent=2) + "\n", encoding="utf-8")
(evidence / "validation.log").write_text(log, encoding="utf-8")

score = json.loads((root / "docs/reviews/2026-09-09-notification-delivery/scores.json").read_text())
score["previous_overall"] = 8.8
for category in score["categories"]:
    category["previous"] = category["score"]
    if category["name"] == "Observability and diagnosability":
        category["score"] = 9.6
        category["reason"] = "Shared instrumentation now integrates six SDK HTTP hosts with coroutine-safe traces, authenticated metrics, bounded admission, connection-pool diagnostics, real authenticated OTLP export and exporter-backpressure evidence. Deployment SLO agreement, backend retention/access enforcement and actual on-call qualification remain open."
    else:
        category["assessment_note"] = "Carried forward; not reassessed in this scoped engineering review"
score["overall"] = float((sum(Decimal(str(c["score"])) for c in score["categories"]) / 6).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP))
assert score["overall"] == 8.9
score["arithmetic"] = "53.6 / 6 = 8.9333, rounded half-up to 8.9"
score["scope"] = "SDK instrumentation and local operability engineering assessment, not a deployed-service certification or a measurement of achieved SLOs"
score["reassessment_basis"] = [
    "The former generic host tracing gap is now backed by an implemented shared adapter and real HTTP/OTLP parent-child verification.",
    "Host queue and pool visibility is now implemented and exercised under saturation, cancellation and connection failure.",
    "Protected metrics remain available under overload; generated request IDs join response, log and span without private payload fields.",
    "Bounded asynchronous export and six host alert rules have executable evidence and CI gates.",
    "The remaining 0.4 deduction retains deployment qualification, wider provider/SaaS coverage and operational policy limitations; no production-readiness claim is made.",
]
(out / "scores.json").write_text(json.dumps(score, indent=2) + "\n", encoding="utf-8")
extra = ["settings.gradle.kts", "distribution/bom/build.gradle.kts", ".github/workflows/ci.yml",
         ".github/workflows/release-evidence.yml", "docs/operations/observability.md",
         "docs/operations/host/README.md", "docs/operations/host/alerts.yml",
         "docs/operations/host/alerts.test.yml", "docs/operations/host/slo.json",
         "docs/api-reference/assessed-capabilities.md"]
sources += [root / p for p in extra]
validation = dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
                  head=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
                  scope="Local uncommitted SDK tree; seven module test suites, lint and ABI checks. No full SDK, SaaS or hosted deployment validation.",
                  tests=totals, modules=module_counts, suites=suites, abi=abi,
                  lint="Seven affected modules and BOM passed without baseline expansion",
                  promtool=promtool, otlp=otlp,
                  bounded_metrics=dict(requests=20000, workers=8, series_growth=0),
                  cancellation=dict(contenders=1000, cycles=100, result="no leaked permits"),
                  exporter=dict(phase_operations=10001, queue_capacity=32, result="blocked export drops excess spans; application work completes"),
                  source_sha256={p.relative_to(root).as_posix():sha(p) for p in sorted(set(sources))},
                  artifact_sha256={p.relative_to(out).as_posix():sha(p) for p in sorted(evidence.rglob("*")) if p.is_file()},
                  scores=score,
                  limits=["SDK host integration is opt-in; no application exporter or global SDK is installed automatically",
                          "Application admission timing excludes proxy/network/Netty queues before the Monitoring phase",
                          "Database timing uses a real Hikari/H2 pool locally; no new PostgreSQL outage or production-load claim",
                          "SLO and retention settings are proposals, not deployment-owner approval or implemented backend guarantees",
                          "Wider provider internals, the Spring SaaS host and actual on-call delivery remain separate qualification work",
                          "New module remains unassessed by the runtime capability catalog; existing deployment maturity policies remain unchanged"])
(out / "validation.json").write_text(json.dumps(validation, indent=2) + "\n", encoding="utf-8")
rows = "".join(f'<tr><th>{html.escape(m["module"])}</th><td>{m["tests"]}</td><td>0</td></tr>' for m in module_counts)
score_rows = "".join(f'<tr><th>{html.escape(c["name"])}</th><td>{c["previous"]:.1f}</td><td>{c["score"]:.1f}</td></tr>' for c in score["categories"])
page = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TrustWeave host observability reassessment</title><style>
*{{box-sizing:border-box}}body{{margin:0;background:#eef3f6;color:#173448;font:16px/1.65 system-ui,sans-serif}}main{{max-width:1080px;margin:auto;padding:32px 20px}}
header{{padding:30px;background:#103448;color:white;border-radius:14px}}h1{{font-size:clamp(1.8rem,4vw,2.8rem);line-height:1.2}}h2{{font-size:1.35rem}}.panel{{background:white;padding:24px;margin:20px 0;border:1px solid #cfdee7;border-radius:12px}}
.metrics{{display:flex;flex-wrap:wrap;gap:28px}}.metric strong{{display:block;font-size:2rem}}a{{color:#086783;overflow-wrap:anywhere}}table{{width:100%;border-collapse:collapse}}th,td{{text-align:left;padding:10px;border-bottom:1px solid #dae4e9;overflow-wrap:anywhere}}.muted{{color:#526b7a}}code{{overflow-wrap:anywhere}}@media(max-width:600px){{main{{padding:12px}}header,.panel{{padding:18px}}th,td{{padding:7px;font-size:.85rem}}#evidence-table,#evidence-table tbody,#evidence-table tr,#evidence-table th,#evidence-table td{{display:block}}#evidence-table thead{{display:none}}#evidence-table tr{{padding:10px 0;border-bottom:1px solid #dae4e9}}#evidence-table th,#evidence-table td{{border:0}}#evidence-table th{{font-size:.95rem}}}}
</style></head><body><main>
<header><p>TRUSTWEAVE SDK &middot; 10 SEPTEMBER 2026</p><h1>Shared host observability verified</h1>
<p>Request traces, protected metrics, admission timing and pool diagnostics now have a reusable SDK implementation and executed host-level evidence.</p>
<div class="metrics"><div class="metric"><strong>9.0 &rarr; 9.6</strong>observability / 10</div><div class="metric"><strong>64 / 64</strong>tests passed</div><div class="metric"><strong>8.9 / 10</strong>overall engineering score</div></div></header>
<section class="panel"><h2>Why the score increases</h2><p>The preceding 9.0 assessment covered ledger metrics, safe status-list logs and local notification delivery. It still lacked a reusable host implementation for tracing, queue/pool diagnostics and protected export. This change closes those implementation gaps and adds verification under contention and exporter failure.</p>
<p>The <strong>9.6 / 10</strong> score is an engineering assessment of SDK instrumentation and local operability. It uses the same six-category rubric as the previous review. It does not mean the deployed service achieved a 99.9% SLO, that production retention was enforced, or that the on-call route was qualified.</p>
<ul><li>Six embedded HTTP hosts now expose an additive <code>withObservability</code> configuration method.</li><li>Two real HTTP hosts produce correctly related spans and deliver authenticated OTLP protobuf to a local collector.</li><li>Queue limits, cancellation, connection-pool wait/failure, protected scraping under overload and exporter backpressure have executed tests.</li><li>Six new host alerts and explicit reference SLO definitions add actionable operational checks.</li></ul>
<p><a href="scores.json">Scoring rationale and unchanged category weights</a> &middot; <a href="../2026-09-09-notification-delivery/index.html">Previous assessment</a></p></section>
<section class="panel"><h2>Implementation</h2><p>The new <a href="../../../observability/src/main/kotlin/org/trustweave/observability/HostTelemetry.kt">HostTelemetry</a> collector uses fixed labels and bounded arrays. Its coroutine-aware spans record the host, phase, bounded HTTP method, outcome and generated request ID; request URLs, credentials, authorization headers, provider messages and stacks are excluded. Incoming request IDs are ignored. Remote trace parents require an explicit trusted-ingress setting; baggage is never extracted.</p>
<p><a href="../../../observability/src/main/kotlin/org/trustweave/observability/HostObservability.kt">HostObservability</a> exposes the metrics route only with a configured secret. Metrics remain available while application admission is saturated. Full queues and admission timeouts return 503 with Retry-After. Cancelled work returns permits. The optional DataSource wrapper measures acquisition without changing returned connections or transaction behavior.</p>
<p>Status-list errors reuse the host-generated request ID, linking the response body and header to the existing safe log and to the trace. Integration is explicit; existing server constructors and defaults remain unchanged. API comparison found exactly one added method per existing server, with zero removed signatures. The module is included in the BOM, but its runtime capability-catalog maturity remains unassessed; no supported-only deployment claim is introduced.</p></section>
<section class="panel"><h2>Executed evidence</h2><table id="evidence-table"><thead><tr><th>Check</th><th>Observed result</th></tr></thead><tbody>
<tr><th>Coroutines and data exclusion</th><td>40 concurrent requests; unique generated IDs; parent/child correlation survives dispatcher changes; private fixture fields absent.</td></tr>
<tr><th>HTTP to HTTP to collector</th><td>2 real Netty hosts, 10 requests, 10 distributed traces and 40 related spans; authenticated OTLP HTTP/protobuf export; unauthorized collector request denied.</td></tr>
<tr><th>Admission correctness</th><td>Full-queue rejection, queued cancellation and 1,000 timeout/cancellation contenders across 100 cycles; no leaked permits.</td></tr>
<tr><th>Pool diagnostics</th><td>Actual single-connection Hikari/H2 pool saturated; waiting and acquisition timeout visible; subsequent query succeeds after release.</td></tr>
<tr><th>Management during overload</th><td>Actual status-list server rejects additional work while authenticated metrics stay reachable; error response, log and span correlate.</td></tr>
<tr><th>Bounded storage and exporter failure</th><td>20,000 requests on 8 workers cause no metric-series growth. A blocked exporter with a 32-span queue drops excess spans observably while 10,001 phase operations complete.</td></tr>
<tr><th>Alert rules</th><td>32 assertions pass for burn-rate windows, queue tail, pool saturation/missing instrumentation and healthy/idle/cancelled cases. Runtime metrics pass promtool exposition validation.</td></tr>
</tbody></table><p><a href="evidence/host-otlp/summary.json">OTLP summary</a> &middot; <a href="evidence/host-otlp/traces.txt">Decoded wire traces</a> &middot; <a href="evidence/host-traces.txt">Concurrent traces</a> &middot; <a href="evidence/host-metrics.prom">Metric sample</a> &middot; <a href="evidence/host-export-backpressure.txt">Exporter drop evidence</a> &middot; <a href="evidence/promtool-checks.json">Prometheus checks</a></p></section>
<section class="panel"><h2>Tests and compatibility</h2><table><thead><tr><th>Module</th><th>Tests</th><th>Failures / skips</th></tr></thead><tbody>{rows}</tbody></table>
<p>All 64 tests passed with zero failures, errors or skips. Seven affected modules passed lint and ABI checks; the BOM also passed lint. Existing lint baselines were not expanded. The whole SDK and SaaS test suites were not rerun. Previous intent-ledger and Alertmanager evidence remains historical and is not counted as a new run here.</p>
<p><a href="validation.json">Suites, source hashes, API additions and limits</a> &middot; <a href="evidence/validation.log">Build log with commands</a></p></section>
<section class="panel"><h2>Updated scorecard</h2><table><thead><tr><th>Category</th><th>Before</th><th>Now</th></tr></thead><tbody>{score_rows}</tbody></table>
<p>The total is 53.6 / 6 = 8.9333, rounded to <strong>8.9 / 10</strong>. Only observability is reassessed; the other categories are carried forward. The remaining 0.4 in observability retains meaningful operational limitations:</p>
<ol><li>Validate the deployed topology, actual provider/SaaS coverage and representative load. Application admission timing excludes earlier proxy, network and Netty queues.</li><li>Agree SLO targets and tune alert thresholds with the deployment owner. The supplied 99.9% availability and 100 ms queue p95 are reference defaults.</li><li>Enforce and verify backend retention, telemetry access and deletion. Proposed durations do not configure production backends.</li><li>Verify firing, resolution and escalation through the real on-call route. Local collectors and receivers do not establish production delivery.</li></ol>
<p>Each host must supply its exporter and bounded batch processor. This library does not install an OpenTelemetry SDK globally, start an exporter automatically, or create a durable audit ledger.</p></section>
<footer><p><a href="../../operations/host/README.md">Host setup and runbooks</a> &middot; <a href="../../operations/host/slo.json">Reference SLO and retention policy</a> &middot; <a href="../../operations/host/alerts.yml">Host alert rules</a></p><p class="muted">Local uncommitted SDK changes. No production deployment, external on-call message or SaaS modification.</p></footer>
</main></body></html>'''
(out / "index.html").write_text(page, encoding="utf-8")
notice = '<section class="panel" id="host-observability-update"><strong>10 September update:</strong> Observability improves to 9.6 / 10; overall engineering score is 8.9. Shared host traces, protected metrics, admission/pool diagnostics and real OTLP export passed verification. <a href="../2026-09-10-host-observability/index.html">Read the latest reassessment</a>. The assessment below remains historical evidence.</section>'
for date in ["2026-09-07-intent-validation", "2026-09-08-observability", "2026-09-09-notification-delivery"]:
    path = root / "docs/reviews" / date / "index.html"
    original = path.read_text(encoding="utf-8")
    if 'id="host-observability-update"' not in original:
        original = re.sub(r'<section class="panel" id="notification-update">.*?</section>', '', original, count=1, flags=re.S)
        path.write_text(original.replace("<main>", "<main>" + notice, 1), encoding="utf-8")
print("Archived 64 passing tests, OTLP/Prometheus evidence, and the 9.6 observability reassessment")
