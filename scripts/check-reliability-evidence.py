"""Enforce the component reliability profile; this is not a production SLO certification."""
import argparse
import hashlib
import json
import math
from pathlib import Path


def validate(root):
    values = {}
    for name in ("contention", "sustained", "history", "lock-timeout", "wal-recovery"):
        path = root / f"{name}.json"
        values[name] = json.loads(path.read_text(encoding="utf-8"))

    def number(name, field, low, high):
        value = values[name][field]
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not low <= value <= high:
            raise ValueError(f"{name}.{field} must be within [{low}, {high}], got {value}")

    for name in ("contention", "sustained"):
        number(name, "callers", 32, 32)
        number(name, "instances", 4, 4)
        number(name, "pool_limit", 8, 8)
        number(name, "invariant_violations", 0, 0)
    number("contention", "initial_requests", 4096, 4096)
    number("contention", "initial_accepted", 2048, 2048)
    number("contention", "followup_accepted", 512, 512)
    number("contention", "reserve_p99_ms", 0, 2000)
    number("sustained", "duration_ms", 60000, 120000)
    number("sustained", "completed_workflows", 1000, 1000000)
    number("sustained", "peak_connections", 1, 8)
    number("sustained", "hot_scope_percent", 80, 80)
    number("sustained", "workflow_p99_ms", 0, 5000)
    number("history", "legacy_rows", 100000, 100000)
    number("history", "new_requests", 100, 100)
    number("history", "admission_history_scans", 0, 0)
    number("lock-timeout", "elapsed_ms", 9000, 25000)
    number("lock-timeout", "unrelated_progress", 1, 1)
    number("lock-timeout", "rollback_retry_success", 1, 1)
    wal = values["wal-recovery"]
    for field in ("base_backup_verified", "corrupt_backup_rejected", "missing_wal_failed_closed"):
        if wal[field] is not True:
            raise ValueError(f"WAL evidence requires {field}")
    number("wal-recovery", "post_backup_records_recovered", 3, 3)
    number("wal-recovery", "after_target_records_excluded", 1, 1)
    number("wal-recovery", "crash_restart_ms", 1, 30000)
    number("wal-recovery", "restore_and_assertions_ms", 1, 90000)
    digests = [wal[key] for key in ("base_digest", "target_digest", "latest_digest")]
    if any(not isinstance(value, str) or len(value) != 43 for value in digests) or len(set(digests)) != 3:
        raise ValueError("Three distinct ledger snapshot digests are required")
    logs = {name: (root / name).read_text(encoding="utf-8") for name in ("wal-recovery.log", "wal-missing.log")}
    if "recovery stopping at restore point" not in logs["wal-recovery.log"]:
        raise ValueError("Missing PostgreSQL recovery-target confirmation")
    if "recovery ended before configured recovery target was reached" not in logs["wal-missing.log"]:
        raise ValueError("Missing PostgreSQL incomplete-WAL rejection")
    return {"passed": True, "profile": "PostgreSQL 16 SDK component reliability v1",
            "limits": "Fixed local/CI fixture; no production capacity, RPO/RTO, replica promotion or external journal qualification.",
            "sha256": {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                       for path in sorted(root.iterdir()) if path.is_file() and path.name != "qualification.json"}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path,
                        default=Path("credentials/plugins/verifiable-intent/build/reports/reliability"))
    args = parser.parse_args()
    try:
        report = validate(args.directory)
    except (OSError, KeyError, ValueError, TypeError) as error:
        raise SystemExit(str(error))
    (args.directory / "qualification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))
