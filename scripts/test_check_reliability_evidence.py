import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("reliability", Path(__file__).with_name("check-reliability-evidence.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReliabilityEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        data = {
            "contention": dict(callers=32, instances=4, pool_limit=8, invariant_violations=0,
                               initial_requests=4096, initial_accepted=2048, followup_accepted=512, reserve_p99_ms=200),
            "sustained": dict(callers=32, instances=4, pool_limit=8, invariant_violations=0, duration_ms=60500,
                              completed_workflows=2000, peak_connections=8, hot_scope_percent=80, workflow_p99_ms=500),
            "history": dict(legacy_rows=100000, new_requests=100, admission_history_scans=0),
            "lock-timeout": dict(elapsed_ms=10100, unrelated_progress=1, rollback_retry_success=1),
            "wal-recovery": dict(base_backup_verified=True, corrupt_backup_rejected=True, missing_wal_failed_closed=True, post_backup_records_recovered=3,
                                 after_target_records_excluded=1, crash_restart_ms=1000, restore_and_assertions_ms=2000,
                                 base_digest="a" * 43, target_digest="b" * 43, latest_digest="c" * 43),
        }
        for name, value in data.items():
            (self.root / f"{name}.json").write_text(json.dumps(value), encoding="utf-8")
        (self.root / "wal-recovery.log").write_text("recovery stopping at restore point", encoding="utf-8")
        (self.root / "wal-missing.log").write_text("recovery ended before configured recovery target was reached", encoding="utf-8")

    def change(self, scenario, field, value):
        path = self.root / f"{scenario}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data[field] = value
        path.write_text(json.dumps(data), encoding="utf-8")

    def test_complete_profile_passes_and_hashes_every_evidence_file(self):
        report = module.validate(self.root)
        self.assertTrue(report["passed"])
        self.assertEqual(7, len(report["sha256"]))

    def test_locate_uses_the_centralized_qualification_directory(self):
        repository = self.root / "repository"
        build_root = self.root / "central-build"
        expected = build_root / module.MODULE / "qualification" / "reliability"
        expected.mkdir(parents=True)
        (expected / module.REQUIRED).write_text("{}", encoding="utf-8")
        with patch.object(module._build_root, "resolve", return_value=build_root):
            self.assertEqual(expected, module.locate(repository))

    def test_missing_scenario_fails(self):
        (self.root / "history.json").unlink()
        with self.assertRaises(OSError):
            module.validate(self.root)

    def test_missing_wal_cannot_be_reported_as_success(self):
        self.change("wal-recovery", "missing_wal_failed_closed", False)
        with self.assertRaises(ValueError):
            module.validate(self.root)

    def test_capacity_and_latency_regressions_fail(self):
        for field, value in (("completed_workflows", 999), ("duration_ms", 59999), ("peak_connections", 9), ("workflow_p99_ms", 5001)):
            with self.subTest(field=field):
                path = self.root / "sustained.json"
                saved = path.read_bytes()
                self.change("sustained", field, value)
                with self.assertRaises(ValueError):
                    module.validate(self.root)
                path.write_bytes(saved)

    def test_non_numeric_and_non_finite_values_fail(self):
        for value in (True, "100", float("nan"), float("inf")):
            self.change("contention", "reserve_p99_ms", value)
            with self.assertRaises(ValueError):
                module.validate(self.root)

    def test_stale_base_snapshot_and_missing_database_log_fail(self):
        self.change("wal-recovery", "target_digest", "a" * 43)
        with self.assertRaises(ValueError):
            module.validate(self.root)
        self.change("wal-recovery", "target_digest", "b" * 43)
        (self.root / "wal-missing.log").write_text("started normally", encoding="utf-8")
        with self.assertRaises(ValueError):
            module.validate(self.root)
