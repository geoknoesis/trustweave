import datetime
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("osv", Path(__file__).with_name("check-osv-baseline.py"))
osv = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(osv)


def report(*vulns, version="1"):
    return {
        "results": [
            {
                "packages": [
                    {"package": {"name": name, "version": version}, "vulnerabilities": [{"id": vid, "aliases": aliases}]}
                    for name, vid, aliases in vulns
                ]
            }
        ]
    }


TODAY = datetime.date(2026, 10, 8)


def triaged(vid="GHSA-1", packages=("a@1",), status="affected", **overrides):
    entry = {
        "id": vid,
        "packages": list(packages),
        "status": status,
        "reason": "because",
        "reviewed": "2026-10-01",
        "expires": "2026-12-31",
    }
    entry.update(overrides)
    return {k: v for k, v in entry.items() if v is not None}


def v3(*entries):
    return {"schema": 3, "advisories": list(entries)}


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

    def check(self, rep, baseline, *extra):
        return self.run_check(rep, baseline, "--today", TODAY.isoformat(), *extra)

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

    # --- version-aware matching -------------------------------------------------------------------

    def test_bumped_version_that_is_still_reported_resurfaces(self):
        baseline = {"advisories": [{"id": "GHSA-1", "packages": ["io.netty:netty-common@4.1.115.Final"]}]}
        self.assertEqual(0, self.run_check(report(("io.netty:netty-common", "GHSA-1", []), version="4.1.115.Final"), baseline))
        self.assertEqual(1, self.run_check(report(("io.netty:netty-common", "GHSA-1", []), version="4.1.120.Final"), baseline))

    def test_bump_to_fixed_version_leaves_a_stale_version_note_only(self):
        baseline = {"advisories": [{"id": "GHSA-1", "packages": ["a@1"]}, {"id": "GHSA-2", "packages": ["b@1", "b@2"]}]}
        rep = {
            "results": [
                {
                    "packages": [
                        {"package": {"name": "a", "version": "1"}, "vulnerabilities": [{"id": "GHSA-1"}]},
                        {"package": {"name": "b", "version": "1"}, "vulnerabilities": [{"id": "GHSA-2"}]},
                    ]
                }
            ]
        }
        self.assertEqual(["GHSA-2 b@2"], osv.stale_versions(osv.report_advisories(rep), baseline))
        self.assertEqual(0, self.run_check(rep, baseline, "--strict-stale"))

    def test_any_recorded_version_matches(self):
        baseline = {"advisories": [{"id": "GHSA-1", "packages": ["a@1", "a@2"]}]}
        self.assertEqual(0, self.run_check(report(("a", "GHSA-1", []), version="2"), baseline))
        self.assertEqual(1, self.run_check(report(("a", "GHSA-1", []), version="3"), baseline))

    def test_label_without_version_matches_any_version(self):
        for label in ("a", "a@?"):
            baseline = {"advisories": [{"id": "GHSA-1", "packages": [label]}]}
            self.assertEqual(0, self.run_check(report(("a", "GHSA-1", []), version="9"), baseline), label)

    def test_report_package_without_version_matches_recorded_version(self):
        baseline = {"advisories": [{"id": "GHSA-1", "packages": ["a@1"]}]}
        self.assertEqual(0, self.run_check(report(("a", "GHSA-1", []), version="?"), baseline))

    def test_resurfaced_version_is_explained(self):
        baseline = {"advisories": [{"id": "GHSA-1", "packages": ["a@1"]}]}
        found = osv.report_advisories(report(("a", "GHSA-1", []), version="2"))
        fresh = osv.new_advisories(found, baseline)
        self.assertEqual({"a@2"}, fresh["GHSA-1"]["packages"])
        self.assertIn("baselined at 1", osv._describe_fresh(fresh["GHSA-1"], baseline))

    # --- triage -----------------------------------------------------------------------------------

    def test_valid_triage_passes_and_summarises(self):
        baseline = v3(triaged(), triaged("GHSA-2", status="not-reachable", expires=None))
        rep = report(("a", "GHSA-1", []), ("a", "GHSA-2", []))
        self.assertEqual(0, self.check(rep, baseline))

    def test_expired_triage_fails(self):
        rep = report(("a", "GHSA-1", []))
        self.assertEqual(1, self.check(rep, v3(triaged(expires="2026-10-07"))))
        self.assertEqual(0, self.check(rep, v3(triaged(expires="2026-10-08"))))

    def test_expired_triage_fails_even_for_a_stale_entry(self):
        rep = report(("a", "GHSA-1", []))
        baseline = v3(triaged(), triaged("GHSA-2", expires="2026-01-01"))
        self.assertEqual(1, self.check(rep, baseline))

    def test_expired_not_reachable_entry_fails_when_it_carries_an_expiry(self):
        self.assertEqual(1, self.check(report(("a", "GHSA-1", [])), v3(triaged(status="not-reachable", expires="2026-09-01"))))

    def test_expiry_and_new_advisory_are_both_reported(self):
        self.assertEqual(1, self.check(report(("a", "GHSA-1", []), ("z", "GHSA-9", [])), v3(triaged(expires="2026-10-07"))))

    def test_schema_below_three_has_no_triage_checks(self):
        self.assertEqual(0, self.check(report(("a", "GHSA-1", [])), {"schema": 2, "advisories": [{"id": "GHSA-1", "packages": ["a@1"]}]}))

    def test_malformed_triage_is_an_error(self):
        rep = report(("a", "GHSA-1", []))
        bad = [
            triaged(status="maybe"),
            triaged(status=None),
            triaged(reason=""),
            triaged(reason="TODO: triage before committing"),
            triaged(reviewed=None),
            triaged(reviewed="yesterday"),
            triaged(reviewed="2026-10-09"),  # in the future
            triaged(expires=None),  # affected needs an expiry
            triaged(status="accepted-risk", expires=None),
            triaged(status="needs-review", expires=None),
            triaged(expires="soon"),
            triaged(expires="2028-01-01"),  # more than a year after reviewed
        ]
        for entry in bad:
            self.assertEqual(2, self.check(rep, v3(entry)), entry)

    def test_optional_expiry_for_not_reachable_and_false_positive(self):
        for status in ("not-reachable", "false-positive"):
            errors, expired, _ = osv.validate_triage(v3(triaged(status=status, expires=None)), TODAY)
            self.assertEqual(([], []), (errors, expired))

    def test_expiring_soon_is_reported_but_passes(self):
        _, expired, expiring = osv.validate_triage(v3(triaged(expires="2026-10-20")), TODAY)
        self.assertEqual(0, len(expired))
        self.assertEqual(1, len(expiring))

    def test_update_keeps_triage_when_versions_are_unchanged(self):
        old = v3(triaged("GHSA-1", ("a@1",)))
        updated = osv.updated_baseline(osv.report_advisories(report(("a", "GHSA-1", []))), old)
        self.assertEqual(3, updated["schema"])
        entry = updated["advisories"][0]
        for key in ("status", "reason", "reviewed", "expires"):
            self.assertEqual(old["advisories"][0][key], entry[key])

    def test_update_demands_retriage_for_a_new_version_and_the_check_rejects_it(self):
        old = v3(triaged("GHSA-1", ("a@1",), status="not-reachable", reason="test only"))
        updated = osv.updated_baseline(osv.report_advisories(report(("a", "GHSA-1", []), version="2")), old)
        entry = updated["advisories"][0]
        self.assertEqual("needs-review", entry["status"])
        self.assertTrue(entry["reason"].startswith("TODO"))
        self.assertIn("test only", entry["reason"])
        self.assertNotIn("reviewed", entry)
        self.assertEqual(2, self.check(report(("a", "GHSA-1", []), version="2"), updated))

    def test_update_marks_a_new_advisory_as_needs_review(self):
        updated = osv.updated_baseline(osv.report_advisories(report(("a", "GHSA-9", []))), v3())
        self.assertEqual("needs-review", updated["advisories"][0]["status"])
        self.assertEqual(2, self.check(report(("a", "GHSA-9", [])), updated))

    def test_checked_in_baseline_is_fully_triaged(self):
        baseline = json.loads(Path(osv.DEFAULT_BASELINE).read_text())
        self.assertGreaterEqual(baseline["schema"], osv.TRIAGE_SCHEMA)
        errors, expired, _ = osv.validate_triage(baseline, TODAY)
        self.assertEqual([], errors)
        self.assertEqual([], expired)
        for item in baseline["advisories"]:
            self.assertIn(item["status"], osv.STATUSES, item["id"])
            self.assertFalse(item["reason"].startswith("TODO"), item["id"])

    def test_checked_in_baseline_accepts_its_own_packages(self):
        """Every recorded package/version of every entry matches itself (a smoke test of version-aware matching)."""
        baseline = json.loads(Path(osv.DEFAULT_BASELINE).read_text())
        rep = {
            "results": [
                {
                    "packages": [
                        {
                            "package": {"name": p.rpartition("@")[0], "version": p.rpartition("@")[2], "ecosystem": "Maven"},
                            "vulnerabilities": [{"id": item["id"], "aliases": item.get("aliases", [])}],
                        }
                        for item in baseline["advisories"]
                        for p in item["packages"]
                    ]
                }
            ]
        }
        self.assertEqual({}, osv.new_advisories(osv.report_advisories(rep), baseline))
        self.assertEqual([], osv.stale_entries(osv.report_advisories(rep), baseline))


if __name__ == "__main__":
    unittest.main()
