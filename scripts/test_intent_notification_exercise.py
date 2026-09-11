"""Failure reporting and cached executable integrity for the notification gate."""
import argparse
import importlib.util
import json
from pathlib import Path
import platform
import tempfile
import unittest
from unittest.mock import Mock, patch


spec = importlib.util.spec_from_file_location(
    "notification_exercise", Path(__file__).with_name("intent-notification-exercise.py")
)
exercise = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exercise)


class NotificationExerciseFailureTest(unittest.TestCase):
    def test_startup_alert_must_clear_before_five_healthy_observations(self):
        pending = [{"metric": {"alertname": "IntentTelemetryMissing", "alertstate": "pending"}}]
        query = Mock(side_effect=[pending, [], [], [], [], [], []])
        with patch.object(exercise.time, "sleep"):
            exercise.healthy_baseline(query, [])
        self.assertEqual(7, query.call_count)

    def test_persistent_startup_alert_times_out(self):
        query = Mock(return_value=[{"metric": {"alertname": "IntentTelemetryMissing"}}])
        with patch.object(exercise.time, "monotonic", side_effect=[0, 0, 31]), patch.object(exercise.time, "sleep"):
            with self.assertRaises(TimeoutError):
                exercise.healthy_baseline(query, [])
        self.assertEqual(1, query.call_count)

    def test_alert_after_startup_convergence_still_fails(self):
        query = Mock(side_effect=[[], [{"metric": {"alertname": "unexpected"}}]])
        with patch.object(exercise.time, "sleep"):
            with self.assertRaisesRegex(AssertionError, "unexpected"):
                exercise.healthy_baseline(query, [])

    def check_failure(self, error):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            output = folder / "reports"
            output.mkdir()
            report = output / "validation.json"
            report.write_text('{"status":"passed","scenarios":["old run"]}')
            args = argparse.Namespace(output=output, cache=folder / "cache", sample=folder / "unused.prom")
            with patch.object(exercise, "install_tools", side_effect=error):
                with self.assertRaises(type(error)):
                    exercise.exercise(args)
            result = json.loads(report.read_text())
            self.assertEqual("failed", result["status"])
            self.assertEqual([], result["scenarios"])
            self.assertEqual(f"{type(error).__name__}: {error}", result["error"])
            self.assertIn("finished_utc", result)

    def test_bootstrap_failure_replaces_stale_success(self):
        self.check_failure(RuntimeError("tool download unavailable"))

    def test_interruption_is_recorded_and_propagated(self):
        self.check_failure(KeyboardInterrupt("cancelled fixture"))

    def test_cached_archive_is_verified_before_any_extraction_or_download(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            system = platform.system().lower()
            extension = ".zip" if system == "windows" else ".tar.gz"
            (folder / f"prometheus-3.5.0.{system}-amd64{extension}").write_bytes(b"corrupted cache")
            with patch.object(exercise.urllib.request, "urlopen") as download:
                with self.assertRaisesRegex(RuntimeError, "Cached archive checksum mismatch"):
                    exercise.install_tools(folder)
                download.assert_not_called()
            self.assertFalse(any(p.is_dir() for p in folder.iterdir()))


if __name__ == "__main__":
    unittest.main()
