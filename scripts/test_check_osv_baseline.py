import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("osv", Path(__file__).with_name("check-osv-baseline.py"))
osv = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(osv)


def report(*vulns):
    return {
        "results": [
            {
                "packages": [
                    {"package": {"name": name, "version": "1"}, "vulnerabilities": [{"id": vid, "aliases": aliases}]}
                    for name, vid, aliases in vulns
                ]
            }
        ]
    }


class OsvBaselineTest(unittest.TestCase):
    def run_check(self, rep, baseline):
        with tempfile.TemporaryDirectory() as tmp:
            r, b = Path(tmp, "r.json"), Path(tmp, "b.json")
            r.write_text(json.dumps(rep))
            b.write_text(json.dumps(baseline))
            return osv.main(["--report", str(r), "--baseline", str(b)])

    def test_known_advisory_passes(self):
        self.assertEqual(0, self.run_check(report(("a", "GHSA-1", [])), {"advisories": [{"id": "GHSA-1"}]}))

    def test_alias_match_passes(self):
        self.assertEqual(0, self.run_check(report(("a", "GHSA-1", ["CVE-1"])), {"advisories": [{"id": "CVE-1"}]}))

    def test_new_advisory_fails(self):
        self.assertEqual(1, self.run_check(report(("a", "GHSA-2", [])), {"advisories": [{"id": "GHSA-1"}]}))

    def test_stale_entry_does_not_fail(self):
        self.assertEqual(0, self.run_check(report(), {"advisories": [{"id": "GHSA-1"}]}))

    def test_missing_report_is_error(self):
        self.assertEqual(2, osv.main(["--report", "/nonexistent/osv.json"]))

    def test_update_preserves_reason(self):
        found = osv.report_advisories(report(("a", "GHSA-1", ["CVE-1"]), ("b", "GHSA-9", [])))
        updated = osv.updated_baseline(found, {"advisories": [{"id": "CVE-1", "reason": "documented"}]})
        reasons = {a["id"]: a["reason"] for a in updated["advisories"]}
        self.assertEqual("documented", reasons["GHSA-1"])
        self.assertTrue(reasons["GHSA-9"].startswith("TODO"))

    def test_checked_in_baseline_lists_didcomm_advisories(self):
        baseline = json.loads(Path(osv.DEFAULT_BASELINE).read_text())
        ids = osv.baseline_ids(baseline)
        for vid in ("GHSA-gvpg-vgmx-xg6w", "GHSA-xwmg-2g98-w7v9", "GHSA-493p-pfq6-5258"):
            self.assertIn(vid, ids)


if __name__ == "__main__":
    unittest.main()
