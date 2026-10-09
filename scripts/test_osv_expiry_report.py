import datetime
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("expiry", Path(__file__).with_name("osv-expiry-report.py"))
expiry = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(expiry)

TODAY = datetime.date(2026, 10, 8)


def item(vid, expires):
    return {"id": vid, "packages": ["a:b@1"], "status": "affected", "expires": expires}


class ExpiryReportTest(unittest.TestCase):
    def test_nothing_near_expiry_gives_no_report(self):
        self.assertEqual((None, 0), expiry.build_report({"advisories": [item("A", "2027-01-01")]}, TODAY))

    def test_lists_expiring_and_expired_sorted(self):
        baseline = {"advisories": [item("LATE", "2026-11-01"), item("GONE", "2026-10-01"), item("FAR", "2027-06-01")]}
        text, count = expiry.build_report(baseline, TODAY)
        self.assertEqual(2, count)
        self.assertLess(text.index("GONE"), text.index("LATE"))
        self.assertIn("(EXPIRED)", text)
        self.assertNotIn("FAR", text)
        self.assertIn(expiry.MARKER, text)

    def test_window_boundary_is_inclusive(self):
        edge = (TODAY + datetime.timedelta(days=30)).isoformat()
        self.assertEqual(1, expiry.build_report({"advisories": [item("E", edge)]}, TODAY)[1])

    def test_malformed_expires_values_are_reported_not_dropped(self):
        for raw in ("soon", "2026-13-45", "20261201", "", 20261201, ["2026-12-01"], "2026-1-5"):
            text, count = expiry.build_report({"advisories": [item("BAD", raw)]}, TODAY)
            self.assertEqual(1, count, raw)
            self.assertIn("BAD", text)
            self.assertIn("not a YYYY-MM-DD date", text)

    def test_missing_expires_is_ignored_and_malformed_counts_with_valid_rows(self):
        baseline = {"advisories": [{"id": "NOEXP", "status": "not-reachable"}, item("GONE", "2026-10-01"), item("BAD", "x")]}
        text, count = expiry.build_report(baseline, TODAY)
        self.assertEqual(2, count)
        self.assertNotIn("NOEXP", text)
        self.assertIn("GONE", text)
        self.assertIn("BAD", text)

    def test_unparseable_baseline_exits_2_without_writing(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "expiry.md"
            for content in ("{not json", "[]", '{"advisories": 5}', ""):
                base = Path(tmp) / "b.json"
                base.write_text(content)
                self.assertEqual(2, expiry.main(["--baseline", str(base), "--output", str(out), "--today", "2026-10-08"]), content)
                self.assertFalse(out.exists())
            missing = Path(tmp) / "missing.json"
            self.assertEqual(2, expiry.main(["--baseline", str(missing), "--output", str(out), "--today", "2026-10-08"]))

    def test_main_writes_file_only_when_needed(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "b.json"
            out = Path(tmp) / "out" / "expiry.md"
            base.write_text(json.dumps({"advisories": [item("A", "2026-10-20")]}))
            expiry.main(["--baseline", str(base), "--output", str(out), "--today", "2026-10-08"])
            self.assertTrue(out.is_file())
            base.write_text(json.dumps({"advisories": [item("A", "2027-10-01")]}))
            expiry.main(["--baseline", str(base), "--output", str(out), "--today", "2026-10-08"])
            self.assertFalse(out.exists())


if __name__ == "__main__":
    unittest.main()
