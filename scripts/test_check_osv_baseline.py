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
    def run_check(self, rep, baseline, *extra, sbom=None):
        with tempfile.TemporaryDirectory() as tmp:
            r, b = Path(tmp, "r.json"), Path(tmp, "b.json")
            r.write_text(json.dumps(rep))
            b.write_text(json.dumps(baseline))
            args = ["--report", str(r), "--baseline", str(b), *extra]
            if sbom is not None:
                s = Path(tmp, "bom.json")
                s.write_text(json.dumps({"components": [{"name": f"c{i}"} for i in range(sbom)]}))
                args += ["--sbom", str(s)]
            return osv.main(args)

    def test_known_advisory_passes(self):
        self.assertEqual(0, self.run_check(report(("a", "GHSA-1", [])), {"advisories": [{"id": "GHSA-1"}]}))

    def test_alias_match_passes(self):
        self.assertEqual(0, self.run_check(report(("a", "GHSA-1", ["CVE-1"])), {"advisories": [{"id": "CVE-1"}]}))

    def test_new_advisory_fails(self):
        self.assertEqual(1, self.run_check(report(("a", "GHSA-2", [])), {"advisories": [{"id": "GHSA-1"}]}))

    def test_stale_entry_does_not_fail(self):
        self.assertEqual(
            0, self.run_check(report(("a", "GHSA-2", [])), {"advisories": [{"id": "GHSA-1"}, {"id": "GHSA-2"}]})
        )

    def test_same_advisory_in_new_package_fails(self):
        baseline = {"advisories": [{"id": "GHSA-1", "packages": ["io.netty:netty-common@1"]}]}
        self.assertEqual(0, self.run_check(report(("netty-common", "GHSA-1", [])), baseline))
        self.assertEqual(0, self.run_check(report(("io.netty:netty-common", "GHSA-1", [])), baseline))
        self.assertEqual(1, self.run_check(report(("netty-common", "GHSA-1", []), ("other-lib", "GHSA-1", [])), baseline))

    def test_legacy_entry_without_packages_matches_any_package(self):
        self.assertEqual(0, self.run_check(report(("anything", "GHSA-1", [])), {"advisories": [{"id": "GHSA-1"}]}))

    def test_ecosystem_mismatch_fails(self):
        rep = report(("a", "GHSA-1", []))
        rep["results"][0]["packages"][0]["package"]["ecosystem"] = "npm"
        baseline = {"advisories": [{"id": "GHSA-1", "packages": ["a@1"], "ecosystems": ["Maven"]}]}
        self.assertEqual(1, self.run_check(rep, baseline))

    def test_empty_report_with_nonempty_baseline_is_error(self):
        baseline = {"advisories": [{"id": "GHSA-1"}]}
        self.assertEqual(2, self.run_check({"results": []}, baseline))
        self.assertEqual(0, self.run_check({"results": []}, baseline, "--allow-empty"))

    def test_report_without_results_key_is_error(self):
        self.assertEqual(2, self.run_check({}, {"advisories": []}))

    def test_sbom_below_floor_is_error(self):
        baseline = {"advisories": [{"id": "GHSA-1"}]}
        rep = report(("a", "GHSA-1", []))
        self.assertEqual(2, self.run_check(rep, baseline, sbom=10))
        self.assertEqual(0, self.run_check(rep, baseline, sbom=250))
        self.assertEqual(0, self.run_check(rep, baseline, "--min-packages", "5", sbom=10))

    def test_sbom_floor_follows_recorded_package_count(self):
        baseline = {"package_count": 1000, "advisories": [{"id": "GHSA-1"}]}
        self.assertEqual(2, self.run_check(report(("a", "GHSA-1", [])), baseline, sbom=300))

    def test_strict_stale(self):
        baseline = {"advisories": [{"id": "GHSA-1"}, {"id": "GHSA-2"}]}
        rep = report(("a", "GHSA-1", []))
        self.assertEqual(0, self.run_check(rep, baseline))
        self.assertEqual(1, self.run_check(rep, baseline, "--strict-stale"))

    def test_update_records_package_count_and_ecosystem(self):
        found = osv.report_advisories(report(("a", "GHSA-1", [])))
        updated = osv.updated_baseline(found, {"advisories": []}, 321)
        self.assertEqual(321, updated["package_count"])
        self.assertEqual(["a@1"], updated["advisories"][0]["packages"])

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
