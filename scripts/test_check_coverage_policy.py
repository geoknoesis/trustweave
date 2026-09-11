import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("coverage_policy", Path(__file__).with_name("check-coverage-policy.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

class CoveragePolicyTest(unittest.TestCase):
    def run_check(self, xml, scopes):
        with tempfile.TemporaryDirectory() as folder:
            report = Path(folder) / "report.xml"
            report.write_text(xml, encoding="utf-8")
            return checker.check(report, {"scopes": scopes})

    def test_floor_boundary_and_regression(self):
        xml = '<report><counter type="LINE" covered="56" missed="44"/></report>'
        self.assertEqual([], self.run_check(xml, {"*": {"LINE": 56}}))
        self.assertTrue(self.run_check(xml, {"*": {"LINE": 56.1}}))

    def test_missing_scope_counter_and_empty_evidence_fail(self):
        self.assertTrue(self.run_check('<report/>', {"missing": {"LINE": 1}}))
        self.assertTrue(self.run_check('<report/>', {"*": {"LINE": 1}}))
        self.assertTrue(self.run_check('<report><counter type="LINE" covered="0" missed="0"/></report>', {"*": {"LINE": 0}}))

    def test_empty_or_invalid_policy_cannot_disable_gate(self):
        for policy in [{}, {"scopes": {}}, {"scopes": []}, {"scopes": {"*": {}}}, {"scopes": {"": {"LINE": 50}}}]:
            with self.subTest(policy=policy), self.assertRaises(ValueError):
                checker.validate_policy(policy)

    def test_invalid_floors_fail_closed(self):
        for floor in [float("nan"), float("inf"), -1, 101, True, "50", None]:
            with self.subTest(floor=floor), self.assertRaises(ValueError):
                checker.validate_policy({"scopes": {"*": {"LINE": floor}}})

    def test_unknown_metric_is_rejected(self):
        with self.assertRaises(ValueError):
            checker.validate_policy({"scopes": {"*": {"LINES": 50}}})

    def test_wrong_root_and_duplicate_evidence_are_rejected(self):
        for xml in ['<notreport/>', '<report><package name="p"/><package name="p"/></report>',
                    '<report><counter type="LINE" covered="100" missed="0"/><counter type="LINE" covered="1" missed="99"/></report>']:
            with self.subTest(xml=xml), self.assertRaises(ValueError):
                self.run_check(xml, {"*": {"LINE": 50}})

    def test_malformed_counters_are_rejected(self):
        for value in ["-1", "NaN", "1.5", "1_000", " 1", "\u0661"]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.run_check(f'<report><counter type="LINE" covered="{value}" missed="1"/></report>', {"*": {"LINE": 0}})

    def test_scoped_counters_do_not_use_aggregate(self):
        xml = '<report><package name="p"><counter type="BRANCH" covered="1" missed="9"/></package><counter type="BRANCH" covered="90" missed="10"/></report>'
        self.assertTrue(self.run_check(xml, {"p": {"BRANCH": 80}}))
        self.assertEqual([], self.run_check(xml, {"*": {"BRANCH": 80}}))

if __name__ == "__main__":
    unittest.main()
