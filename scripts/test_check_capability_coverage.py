import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("capability", Path(__file__).with_name("check-capability-coverage.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

REPOSITORY = Path(__file__).resolve().parents[1]


class CapabilityCoverageTest(unittest.TestCase):
    def fixture(self, folder, catalog, ratchet, modules):
        root = Path(folder)
        (root / "common/src/main/resources").mkdir(parents=True)
        (root / "config").mkdir(parents=True)
        (root / checker.CATALOG).write_text(json.dumps(catalog), encoding="utf-8")
        (root / checker.RATCHET).write_text(json.dumps(ratchet), encoding="utf-8")
        checker.declared_modules = lambda _root, modules=modules: set(modules)
        return root

    def entry(self, maturity="experimental"):
        return {"maturity": maturity, "operations": [], "formats": []}

    def test_assessed_and_recorded_modules_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(
                folder,
                {"a": self.entry()},
                {"recorded": 1, "unassessed": ["b"]},
                {"a", "b"},
            )
            failures, assessed, unassessed, drift = checker.check(root)
            self.assertEqual(([], 1, 1, 0), (failures, assessed, unassessed, drift))

    def test_a_new_unrecorded_module_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder, {"a": self.entry()}, {"recorded": 0, "unassessed": []}, {"a", "new"})
            failures, _, _, _ = checker.check(root)
            self.assertTrue(any("new: declares no maturity" in failure for failure in failures), failures)

    def test_growth_beyond_the_recorded_count_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder, {}, {"recorded": 1, "unassessed": ["a"]}, {"a", "b"})
            failures, _, unassessed, _ = checker.check(root)
            self.assertEqual(2, unassessed)
            self.assertTrue(any("grew from 1 to 2" in failure for failure in failures), failures)

    def test_assessing_a_module_reports_progress_instead_of_failing(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder, {}, {"recorded": 2, "unassessed": ["a", "b"]}, {"a"})
            failures, _, unassessed, drift = checker.check(root)
            self.assertEqual(1, unassessed)
            self.assertEqual(1, drift)
            self.assertTrue(any("no longer exists" in failure for failure in failures), failures)

    def test_a_module_cannot_be_both_assessed_and_unassessed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder, {"a": self.entry()}, {"recorded": 1, "unassessed": ["a"]}, {"a"})
            failures, _, _, _ = checker.check(root)
            self.assertTrue(any("still listed as unassessed" in failure for failure in failures), failures)

    def test_unknown_maturity_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder, {"a": self.entry("production")}, {"recorded": 0, "unassessed": []}, {"a"})
            failures, _, _, _ = checker.check(root)
            self.assertTrue(any("is not one of" in failure for failure in failures), failures)

    def test_inconsistent_ratchet_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder, {}, {"recorded": 5, "unassessed": ["a"]}, {"a"})
            with self.assertRaises(ValueError):
                checker.check(root)


class RepositoryCapabilityTest(unittest.TestCase):
    def test_this_repository_passes_its_own_ratchet(self):
        # Reloaded so the stubbed declared_modules above does not leak into this check.
        fresh = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(fresh)
        failures, _, _, _ = fresh.check(REPOSITORY)
        self.assertEqual([], failures)


if __name__ == "__main__":
    unittest.main()
