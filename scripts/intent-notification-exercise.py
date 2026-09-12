"""Exercise real Prometheus -> Alertmanager -> loopback webhook delivery.

Requires Python 3.11+, PyYAML 6.0.3 and network access for pinned tool downloads.
No production endpoint, Docker daemon, credentials or external recipient is used.
"""

import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import importlib.util as _importlib_util

_spec = _importlib_util.spec_from_file_location("build_root", Path(__file__).with_name("build_root.py"))
_build_root = _importlib_util.module_from_spec(_spec)
_spec.loader.exec_module(_build_root)
import platform
import shutil
import socket
import subprocess
import tarfile
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

import yaml


ROOT = Path(__file__).resolve().parents[1]
TOOLS = {
    "prometheus": ("3.5.0", {
        "windows": "b3c2607d1e80a277735fd3cde86432eb1b6843897cb82fb04fbf4c7ffb1d1c36",
        "linux": "e811827af26d822afb09a4f28314f61b618b12cff5369835a67f674d8b46f39a",
    }),
    "alertmanager": ("0.28.1", {
        "windows": "521de9569ab0570845c38889cceb26790696ab04b1cba0b0086994b785013ca8",
        "linux": "5ac7ab5e4b8ee5ce4d8fb0988f9cb275efcc3f181b4b408179fafee121693311",
    }),
}
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def install_tools(cache):
    """Verify archives on every run; extract only four exact regular files."""
    system = platform.system().lower()
    if system not in ("windows", "linux") or platform.machine().lower() not in ("amd64", "x86_64"):
        raise RuntimeError("This fixture currently supports Windows/Linux amd64")
    cache.mkdir(parents=True, exist_ok=True)
    binaries, evidence = {}, {}
    for tool, (version, hashes) in TOOLS.items():
        stem = f"{tool}-{version}.{system}-amd64"
        archive = cache / (stem + (".zip" if system == "windows" else ".tar.gz"))
        url = f"https://github.com/prometheus/{tool}/releases/download/v{version}/{archive.name}"
        if not archive.exists():
            print(f"Downloading {archive.name}", flush=True)
            with urllib.request.urlopen(url, timeout=60) as source:
                with tempfile.NamedTemporaryFile(dir=cache, delete=False) as dest:
                    temporary = Path(dest.name)
                    try:
                        shutil.copyfileobj(source, dest)
                    except BaseException:
                        dest.close()
                        temporary.unlink(missing_ok=True)
                        raise
            if sha(temporary) != hashes[system]:
                temporary.unlink()
                raise RuntimeError(f"Archive checksum mismatch: {tool}")
            temporary.replace(archive)
        if sha(archive) != hashes[system]:
            raise RuntimeError(f"Cached archive checksum mismatch: {archive}")
        evidence[tool] = dict(version=version, archive_sha256=hashes[system], source=url)
        for name in (tool, "promtool" if tool == "prometheus" else "amtool"):
            filename = name + (".exe" if system == "windows" else "")
            target = cache / stem / filename
            target.parent.mkdir(exist_ok=True)
            if system == "windows":
                with zipfile.ZipFile(archive) as package:
                    data = package.read(f"{stem}/{filename}")
            else:
                with tarfile.open(archive) as package:
                    member = package.getmember(f"{stem}/{filename}")
                    if not member.isfile():
                        raise RuntimeError("Expected a regular executable in archive")
                    with package.extractfile(member) as source:
                        data = source.read()
            target.write_bytes(data)
            target.chmod(0o755)
            binaries[name] = target.resolve()
    return binaries, evidence


class Fixture:
    def __init__(self, sample):
        self.sample = sample
        self.mode = "healthy"
        self.events = []
        self.rejected = False
        self.lock = threading.Lock()

    def set_mode(self, mode):
        with self.lock:
            self.mode = mode

    def metrics(self):
        with self.lock:
            mode = self.mode
        if mode == "unreachable":
            return 503, "fixture metrics route unavailable\n"
        # Use the runtime collector's actual series/types. Only synthetic health
        # gauges change; counters remain constant so historical SQL errors do not fire.
        values = {
            "trustweave_intent_health_poll_success": int(mode != "unhealthy"),
            "trustweave_intent_health_last_success_timestamp_seconds": int(time.time()),
            "trustweave_intent_reconciliation_pending": 0,
            "trustweave_intent_oldest_pending_known": 1,
            "trustweave_intent_oldest_pending_age_seconds": 0,
        }
        lines = []
        for line in self.sample.splitlines():
            name = line.split(" ", 1)[0]
            if mode == "missing" and name == "trustweave_intent_health_last_success_timestamp_seconds":
                continue
            if mode == "unhealthy" and name in values and name not in (
                "trustweave_intent_health_poll_success",
                "trustweave_intent_health_last_success_timestamp_seconds",
            ):
                continue
            lines.append(f"{name} {values[name]}" if name in values else line)
        return 200, "\n".join(lines) + "\n"

    def handler(self):
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                if self.path != "/metrics":
                    self.send_error(404)
                    return
                status, body = fixture.metrics()
                self.send_response(status)
                self.send_header("Content-Type", "text/plain; version=0.0.4")
                self.end_headers()
                self.wfile.write(body.encode())

            def do_POST(self):
                if self.path != "/notifications":
                    self.send_error(404)
                    return
                size = int(self.headers.get("Content-Length", "0"))
                if not 0 < size <= 65536:
                    self.send_error(413)
                    return
                payload = json.loads(self.rfile.read(size))
                with fixture.lock:
                    # One deliberate transient failure proves Alertmanager retry.
                    status = 503 if not fixture.rejected else 200
                    fixture.rejected = True
                    fixture.events.append(dict(received_utc=datetime.now(timezone.utc).isoformat(),
                                               http_status=status, payload=payload))
                self.send_response(status)
                self.end_headers()

        return Handler

    def notification(self, alertname, status, http_status=200):
        with self.lock:
            return next((event for event in self.events
                         if event["http_status"] == http_status
                         and event["payload"]["status"] == status
                         and any(alert["labels"]["alertname"] == alertname
                                 and alert["status"] == status
                                 for alert in event["payload"]["alerts"])), None)


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def get_json(url):
    with HTTP.open(url, timeout=3) as response:
        return json.load(response)


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


def wait_for(description, predicate, processes, timeout=200):
    print(description, flush=True)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if any(process.poll() is not None for process in processes):
            raise RuntimeError("Monitoring process exited; inspect archived process logs")
        try:
            result = predicate()
            if result:
                return result
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(1)
    raise TimeoutError(description)


def healthy_baseline(query, processes):
    # A successful scrape can precede the next rule evaluation. Bootstrap
    # absence/unavailability alerts must converge before testing steady health.
    wait_for("Waiting for healthy startup rule evaluation", lambda: not query("ALERTS"), processes, 30)
    for _ in range(5):
        alerts = query("ALERTS")
        if alerts:
            raise AssertionError(f"Healthy fixture unexpectedly generated an alert: {json.dumps(alerts)}")
        time.sleep(1)


def exercise(args):
    args.output.mkdir(parents=True, exist_ok=True)
    result = dict(status="failed", started_utc=datetime.now(timezone.utc).isoformat(),
                  scope="Local synthetic notification pipeline; no production recipient or database",
                  scenarios=[], source_sha256={})
    fixture = None
    try:
        binaries, result["tools"] = install_tools(args.cache)
        rules_path = ROOT / "docs/operations/intent/alerts.yml"
        for path in (Path(__file__), rules_path, args.sample):
            result["source_sha256"][str(path.resolve().relative_to(ROOT))] = sha(path)
        sample = args.sample.read_text(encoding="utf-8")
        for metric in ("trustweave_intent_health_poll_success",
                       "trustweave_intent_health_last_success_timestamp_seconds"):
            if not any(line.startswith(metric + " ") for line in sample.splitlines()):
                raise ValueError(f"Runtime sample is missing {metric}")
        fixture = Fixture(sample)
        rules = yaml.safe_load(rules_path.read_text())
        annotations = {r["alert"]: r["annotations"] for g in rules["groups"] for r in g["rules"]}
        with ExitStack() as stack:
            temporary = Path(stack.enter_context(tempfile.TemporaryDirectory(prefix="tw-notifications-")))
            server = ThreadingHTTPServer(("127.0.0.1", 0), fixture.handler())
            server.daemon_threads = True
            stack.callback(server.server_close)
            threading.Thread(target=server.serve_forever, daemon=True).start()
            stack.callback(server.shutdown)
            fixture_address = f"127.0.0.1:{server.server_port}"
            prom_port, am_port = free_port(), free_port()
            while prom_port == am_port:
                am_port = free_port()
            prom_url = f"http://127.0.0.1:{prom_port}"
            am_url = f"http://127.0.0.1:{am_port}"
            am_config = {
                "route": {"receiver": "local-exercise", "group_by": ["alertname", "job", "instance"],
                          "group_wait": "1s", "group_interval": "2s", "repeat_interval": "1h"},
                "receivers": [{"name": "local-exercise", "webhook_configs": [{
                    "url": f"http://{fixture_address}/notifications", "send_resolved": True}]}],
            }
            prom_config = {
                "global": {"scrape_interval": "1s", "evaluation_interval": "1s", "scrape_timeout": "1s"},
                "rule_files": [str(rules_path.resolve())],
                "alerting": {"alertmanagers": [{"static_configs": [{"targets": [f"127.0.0.1:{am_port}"]}]}]},
                "scrape_configs": [{"job_name": "trustweave-intent", "static_configs": [{"targets": [fixture_address]}]}],
            }
            processes = []
            flags = subprocess.CREATE_NO_WINDOW if platform.system() == "Windows" else 0
            for tool, config, port in (("alertmanager", am_config, am_port), ("prometheus", prom_config, prom_port)):
                config_path = args.output / f"{tool}.json"
                config_path.write_text(json.dumps(config, indent=2) + "\n")
                check = [str(binaries["amtool" if tool == "alertmanager" else "promtool"]),
                         "check-config" if tool == "alertmanager" else "check"]
                if tool == "prometheus":
                    check.append("config")
                checked = subprocess.run([*check, str(config_path.resolve())], capture_output=True,
                                         text=True, timeout=30, creationflags=flags)
                (args.output / f"{tool}-config-check.log").write_text(checked.stdout + checked.stderr)
                checked.check_returncode()
                command = [str(binaries[tool]), f"--config.file={config_path.resolve()}",
                           f"--web.listen-address=127.0.0.1:{port}"]
                if tool == "prometheus":
                    command += [f"--storage.tsdb.path={temporary / tool}", "--storage.tsdb.retention.time=1h"]
                else:
                    command += [f"--storage.path={temporary / tool}", "--cluster.listen-address="]
                log = stack.enter_context((args.output / f"{tool}.log").open("w"))
                process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, creationflags=flags)
                stack.callback(stop, process)
                processes.append(process)

            def query(expression):
                return get_json(prom_url + "/api/v1/query?" + urllib.parse.urlencode({"query": expression}))["data"]["result"]

            wait_for("Waiting for a successful runtime-contract scrape", lambda: query('up{job="trustweave-intent"} == 1'), processes, 60)
            wait_for("Waiting for Alertmanager readiness", lambda: get_json(am_url + "/api/v2/status"), processes, 30)
            healthy_baseline(query, processes)
            for mode, alertname in (("unhealthy", "IntentHealthUnavailable"),
                                    ("missing", "IntentTelemetryMissing"),
                                    ("unreachable", "IntentHostUnavailable")):
                started = time.monotonic()
                fixture.set_mode(mode)
                pending = wait_for(f"{alertname}: waiting for pending", lambda: query(
                    f'ALERTS{{alertname="{alertname}",alertstate="pending"}}'), processes, 30)
                if fixture.notification(alertname, "firing"):
                    raise AssertionError("Notification arrived while alert was pending")
                fired = wait_for(f"{alertname}: waiting for delivered firing notification (full 2-minute hold)",
                                 lambda: fixture.notification(alertname, "firing"), processes)
                firing_seconds = round(time.monotonic() - started, 3)
                fixture.set_mode("healthy")
                resolved = wait_for(f"{alertname}: waiting for delivered recovery notification",
                                    lambda: fixture.notification(alertname, "resolved"), processes, 90)
                first = next(a for a in fired["payload"]["alerts"] if a["labels"]["alertname"] == alertname)
                last = next(a for a in resolved["payload"]["alerts"] if a["labels"]["alertname"] == alertname)
                assert first["fingerprint"] == last["fingerprint"]
                assert first["labels"] == last["labels"]
                assert first["labels"]["job"] == "trustweave-intent"
                assert first["labels"]["instance"] == fixture_address
                assert first["labels"]["severity"] == "critical"
                assert first["annotations"] == annotations[alertname]
                assert last["annotations"] == annotations[alertname]
                assert first["startsAt"] == last["startsAt"]
                assert last["endsAt"] > last["startsAt"]
                assert fired["payload"]["receiver"] == "local-exercise"
                assert firing_seconds >= 120, firing_seconds
                wait_for(f"{alertname}: confirming alert cleared from Prometheus",
                         lambda: not query(f'ALERTS{{alertname="{alertname}"}}'), processes, 30)
                result["scenarios"].append(dict(alert=alertname, pending_observed=bool(pending),
                    firing_delivered=True, recovery_delivered=True, fingerprint=first["fingerprint"],
                    firing_seconds=firing_seconds, total_seconds=round(time.monotonic() - started, 3)))
            rejected = fixture.notification("IntentHealthUnavailable", "firing", 503)
            retried = fixture.notification("IntentHealthUnavailable", "firing")
            assert rejected and retried and rejected["received_utc"] < retried["received_utc"]
            assert rejected["payload"]["alerts"][0]["fingerprint"] == retried["payload"]["alerts"][0]["fingerprint"]
            assert not query("ALERTS"), "An unexpected alert remains after recovery"
            result["receiver_retry"] = "Initial HTTP 503 followed by accepted firing and resolved delivery"
            result["status"] = "passed"
    except BaseException as error:
        result["status"] = "failed"
        result["error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        if fixture:
            (args.output / "notifications.json").write_text(json.dumps(fixture.events, indent=2) + "\n")
        result["finished_utc"] = datetime.now(timezone.utc).isoformat()
        (args.output / "validation.json").write_text(json.dumps(result, indent=2) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    # Evidence now lives in the module's Gradle build directory, which this repository relocates
    # on Windows, so resolve it the way the build does rather than assuming a layout.
    parser.add_argument(
        "--sample",
        type=Path,
        default=_build_root.resolve(ROOT) / "credentials/plugins/verifiable-intent/qualification/intent-metrics.prom",
    )
    parser.add_argument("--cache", type=Path, default=ROOT / ".gradle/intent-monitoring-tools")
    parser.add_argument("--output", type=Path, default=ROOT / "build/reports/intent-notifications")
    exercise(parser.parse_args())
