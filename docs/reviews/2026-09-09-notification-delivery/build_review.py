"""Archive executed notification evidence and render the scoped follow-up."""
from datetime import datetime, timezone
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
import yaml

out = Path(__file__).resolve().parent
root = out.parents[2]
source = root / "build/reports/intent-notifications"
validation = json.loads((source / "validation.json").read_text())
assert validation["status"] == "passed"
assert "error" not in validation
assert len(validation["scenarios"]) == 3
assert all(s["pending_observed"] and s["firing_delivered"] and s["recovery_delivered"]
           for s in validation["scenarios"])
for name, expected in validation["source_sha256"].items():
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == expected, name

evidence = out / "evidence"
evidence.mkdir(exist_ok=True)
harness = json.loads((root / ".gradle/notification-harness-tests.json").read_text())
assert harness["exit_code"] == 0 and "Ran 3 tests" in harness["stderr"]
shutil.copyfile(root / ".gradle/notification-harness-tests.json", evidence / "harness-tests.json")
for path in source.iterdir():
    if path.is_file():
        shutil.copyfile(path, evidence / path.name)
shutil.copyfile(root / "docs/operations/intent/alerts.yml", evidence / "alerts.yml")
rule_check = json.loads((root / ".gradle/notification-rule-check.json").read_text())
assert rule_check["exit_code"] == 0 and "SUCCESS" in rule_check["stdout"]
assert rule_check["rules_sha256"] == hashlib.sha256((evidence / "alerts.yml").read_bytes()).hexdigest()
shutil.copyfile(root / ".gradle/notification-rule-check.json", evidence / "rule-check.json")
rule_before = json.loads((root / ".gradle/notification-rule-before.json").read_text())
assert rule_before["exit_code"] != 0 and "got:[]" in rule_before["stderr"]
shutil.copyfile(root / ".gradle/notification-rule-before.json", evidence / "rule-before.json")
shutil.copyfile(root / "docs/operations/intent/alerts.test.yml", evidence / "alerts.test.yml")
assert rule_check["tests_sha256"] == hashlib.sha256((evidence / "alerts.test.yml").read_bytes()).hexdigest()
assert sum(len(t["alert_rule_test"]) for t in yaml.safe_load((evidence / "alerts.test.yml").read_text())["tests"]) == 37
shutil.copyfile(root / "credentials/plugins/verifiable-intent/build/qualification/intent-metrics.prom",
                evidence / "runtime-input.prom")
previous_dir = root / "docs/reviews/2026-09-08-observability"
previous = json.loads((previous_dir / "validation.json").read_text())
runtime_hashes = {p: h for p, h in previous["source_sha256"].items() if p.startswith("credentials/")}
assert runtime_hashes
assert all(hashlib.sha256((root / p).read_bytes()).hexdigest() == h for p, h in runtime_hashes.items())
scores = json.loads((previous_dir / "scores.json").read_text())
scores["previous_overall"] = 8.8
for category in scores["categories"]:
    category["previous"] = category["score"]
    if category["name"] == "Observability and diagnosability":
        category["reason"] = "Local real-engine notification firing, retry and recovery now pass. Production host traces, queue/pool telemetry, SLOs, access/retention controls and the actual on-call route remain unqualified. Score retained at 9.0."
(out / "scores.json").write_text(json.dumps(scores, indent=2) + "\n", encoding="utf-8")
checks = dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
              notification_result="passed", scenarios=3,
              harness_unit_tests=dict(tests=3, result="passed", scope="failure evidence, cancellation and corrupt cached archive"),
              previous_runtime_source_files_unchanged=len(runtime_hashes),
              previous_module_tests="96 passed in the preceding review; not rerun for this tooling/docs change",
              alert_rule_assertions=37, alert_rules_result="passed using native Prometheus 3.5.0 promtool",
              documentation={k: v for k, v in json.loads((root / ".gradle/notification-doc-check.json").read_text()).items() if k != "inventory"},
              limits=["Executed on Windows amd64; Linux CI path is configured but has not run in hosted CI",
                      "Synthetic metrics transitions replay a recorded runtime scrape; no new database outage",
                      "No production endpoint, pager, distributed trace or full SDK test run"],
              source_sha256={p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in [
                  "scripts/intent-notification-exercise.py", "scripts/test_intent_notification_exercise.py", ".github/workflows/ci.yml",
                  ".github/workflows/release-evidence.yml", "docs/operations/intent/README.md",
                  "docs/operations/observability.md", "docs/operations/intent/alerts.yml",
                  "docs/operations/intent/alerts.test.yml"]})
(out / "checks.json").write_text(json.dumps(checks, indent=2) + "\n", encoding="utf-8")
rows = "".join(f'<tr><th>{html.escape(s["alert"])}</th><td data-label="Pending">Passed</td><td data-label="Firing after injection">{s["firing_seconds"]:.1f}s</td><td data-label="Recovery">Delivered</td></tr>'
               for s in validation["scenarios"])
score_rows = "".join(f'<tr><th>{html.escape(c["name"])}</th><td>{c["score"]:.1f}</td></tr>' for c in scores["categories"])
page = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TrustWeave notification delivery verification</title><style>
*{{box-sizing:border-box}}body{{margin:0;background:#eef3f6;color:#173448;font:16px/1.6 system-ui,sans-serif}}main{{max-width:1080px;margin:auto;padding:32px 20px}}
header{{padding:30px;background:#103448;color:white;border-radius:14px}}h1{{font-size:clamp(1.8rem,4vw,2.8rem);line-height:1.2}}h2{{font-size:1.35rem}}.panel{{background:white;padding:24px;margin:20px 0;border:1px solid #cfdee7;border-radius:12px}}
.metrics{{display:flex;flex-wrap:wrap;gap:28px}}.metric strong{{display:block;font-size:2rem}}a{{color:#086783;overflow-wrap:anywhere}}table{{width:100%;border-collapse:collapse}}th,td{{text-align:left;padding:10px;border-bottom:1px solid #dae4e9;overflow-wrap:anywhere}}.table{{overflow-x:auto}}.muted{{color:#526b7a}}code{{overflow-wrap:anywhere}}@media(max-width:600px){{main{{padding:12px}}header,.panel{{padding:18px}}th,td{{padding:7px;font-size:.85rem}}}}
@media(max-width:600px){{.table table,.table tbody,.table tr,.table th,.table td{{display:block}}.table thead{{display:none}}.table tr{{padding:10px 0;border-bottom:1px solid #dae4e9}}.table th,.table td{{border:0}}.table td{{display:flex;justify-content:space-between;gap:12px}}.table td:before{{content:attr(data-label);font-weight:600}}}}
</style></head><body><main>
<header><p>TRUSTWEAVE SDK &middot; 9 SEPTEMBER 2026</p><h1>Notification delivery and recovery verified</h1>
<p>The local monitoring chain now proves that real alerts reach a receiver, retry after a delivery failure, and resolve after recovery.</p>
<div class="metrics"><div class="metric"><strong>3 / 3</strong>delivery scenarios passed</div><div class="metric"><strong>9.0 / 10</strong>observability retained</div><div class="metric"><strong>8.8 / 10</strong>overall retained</div></div></header>
<section class="panel"><h2>Gap closed: rules now have delivery evidence</h2>
<p>The preceding review verified alert expressions and runtime telemetry, but did not exercise an Alertmanager receiver. The new executable gate starts checksum-pinned Prometheus 3.5.0 and Alertmanager 0.28.1, replays the collector's recorded metric contract, and injects three synthetic failures.</p>
<p>All nine repository rules load without test-time rewrites. The three exercised alerts retain their full two-minute pending period. Each test checks pending state, a delivered firing notification, a delivered resolved notification, stable fingerprints, severity, job/instance labels and matching runbook annotations. The receiver deliberately returns HTTP 503 once; Alertmanager successfully retries the same alert.</p>
<div class="table"><table><thead><tr><th>Scenario</th><th>Pending</th><th>Firing after injection</th><th>Recovery</th></tr></thead><tbody>{rows}</tbody></table></div>
<p>These timings are local fixture observations, including the configured pending period. They are not deployment SLOs.</p></section>
<section class="panel"><h2>Defect fixed: an incomplete scrape could look healthy</h2>
<p>The missing-telemetry rule checked only the health-success flag. If a host exported that flag but dropped its last-success timestamp, the missing-telemetry alert remained silent and the freshness alert had no timestamp to evaluate.</p>
<p>The rule now requires both metrics for the same job and instance. Six new assertions cover healthy and pending states, firing, recovery, and isolation from a healthy second host. The previous rule fails the new firing assertion; the corrected rule passes all 37 assertions. The live pipeline also verifies firing and recovery when only the timestamp disappears.</p>
<p><a href="evidence/rule-before.json">Reproduced failure with the previous rule</a> &middot; <a href="evidence/rule-check.json">Corrected rule results</a> &middot; <a href="evidence/alerts.test.yml">Regression cases</a></p></section>
<section class="panel"><h2>Implementation and validation</h2>
<p><a href="../../../scripts/intent-notification-exercise.py">The repeatable exercise</a> validates official archive checksums before execution, binds all listeners to loopback, disables Alertmanager cluster gossip, bounds transition waits, and stops its subprocesses when finished. It retains generated configurations, logs, source hashes and webhook payloads. CI and release-evidence workflows run the gate and retain failure diagnostics.</p>
<p>The local Windows run passed all three scenarios and the receiver retry. Both native configuration checks passed, including all nine rules. All 37 promtool rule assertions passed. Three harness tests also passed: bootstrap failure replaces stale success evidence, interruption is recorded and propagated, and a corrupt cached executable archive is rejected before extraction. The documentation checker reported zero errors. Python compilation and both workflow YAML parses passed.</p>
<p>The {len(runtime_hashes)} runtime module source/build/API files recorded by the preceding review are unchanged. Its 96 passing module tests remain historical evidence; they were not rerun for this tooling and documentation change. Docker's local engine was unavailable, so this exercise used native binaries. Linux hosted CI is configured but has not been executed here.</p>
<p><a href="evidence/validation.json">Executed scenarios and tool hashes</a> &middot; <a href="evidence/notifications.json">Received webhook payloads</a> &middot; <a href="evidence/prometheus.log">Prometheus log</a> &middot; <a href="evidence/alertmanager.log">Alertmanager log</a> &middot; <a href="checks.json">Verification and source hashes</a></p></section>
<section class="panel"><h2>Score reassessment</h2><p>Observability remains <strong>9.0 / 10</strong>. This closes a local delivery-validation gap and adds a regression gate. The remaining deployment evidence is still required before awarding the final point. Other categories are carried forward without a new assessment.</p>
<table><thead><tr><th>Category</th><th>/ 10</th></tr></thead><tbody>{score_rows}</tbody></table>
<p>The unchanged six-category total is 53 / 6 = 8.8333, rounded to <strong>8.8 / 10</strong>. Scores are engineering judgment, not production certification. <a href="scores.json">Machine-readable scores</a>.</p>
<ol><li>Instrument and verify the deployed host's queue, connection pool and distributed trace context.</li><li>Agree deployment SLOs and validate alert thresholds under representative load.</li><li>Verify protected telemetry access, retention and redaction in the deployed backends.</li><li>Observe firing and resolved delivery through the intended on-call route with its real routing, authentication and escalation settings.</li></ol>
<p>The receiver is synthetic and local. No external recipient, production service or new database outage was involved.</p></section>
<footer><p><a href="../../operations/intent/README.md#notification-delivery-exercise">Run the exercise</a> &middot; <a href="../2026-09-08-observability/index.html">Previous observability review</a> &middot; <a href="../2026-09-07-intent-validation/index.html">Payment validation review</a></p><p class="muted">Local uncommitted SDK changes. No deployment, publication or SaaS change.</p></footer>
</main></body></html>'''
(out / "index.html").write_text(page, encoding="utf-8")
notice = '<section class="panel" id="notification-update"><strong>9 September update:</strong> A missing freshness timestamp now triggers an alert. Real local notification firing, delivery retry and recovery passed verification. Scores remain 9.0 for observability and 8.8 overall. <a href="../2026-09-09-notification-delivery/index.html">Read the latest follow-up</a>. The assessment below is preserved as historical evidence.</section>'
for directory in (previous_dir, root / "docs/reviews/2026-09-07-intent-validation"):
    path = directory / "index.html"
    original = path.read_text(encoding="utf-8")
    if 'id="notification-update"' not in original:
        original = re.sub(r'<section class="panel"><strong>8 September update:.*?</section>', '', original, count=1, flags=re.S)
        path.write_text(original.replace('<main>', '<main>' + notice, 1), encoding="utf-8")
print("Archived notification evidence and generated scoped review")
