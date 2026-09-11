import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("build_root", Path(__file__).with_name("build_root.py"))
resolver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resolver)

REPOSITORY = Path(__file__).resolve().parents[1]


class BuildRootTest(unittest.TestCase):
    def repository(self, folder, properties=None, name="trustweave"):
        root = Path(folder)
        (root / "settings.gradle.kts").write_text(f'rootProject.name = "{name}"\n', encoding="utf-8")
        if properties is not None:
            (root / "gradle.properties").write_text(properties, encoding="utf-8")
        return root

    def test_non_windows_uses_the_in_repo_build_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.repository(folder)
            self.assertEqual(root / "build", resolver.resolve(root, platform="linux", environment={}))

    def test_windows_redirects_under_local_app_data(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.repository(folder)
            resolved = resolver.resolve(root, platform="win32", environment={"LOCALAPPDATA": r"C:\AppData"})
            self.assertEqual(Path(r"C:\AppData") / "TrustWeave" / "gradle-build" / "trustweave", resolved)

    def test_windows_opt_out_returns_to_the_repository(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.repository(folder, properties="trustweave.windowsInRepoBuild=true\n")
            self.assertEqual(root / "build", resolver.resolve(root, platform="win32", environment={"LOCALAPPDATA": "C:/x"}))

    def test_commented_and_false_opt_out_do_not_apply(self):
        for properties in ["#trustweave.windowsInRepoBuild=true\n", "trustweave.windowsInRepoBuild=false\n", ""]:
            with self.subTest(properties=properties), tempfile.TemporaryDirectory() as folder:
                root = self.repository(folder, properties=properties)
                resolved = resolver.resolve(root, platform="win32", environment={"LOCALAPPDATA": "C:/x"})
                self.assertNotEqual(root / "build", resolved)

    def test_redirect_follows_the_declared_root_project_name(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.repository(folder, name="renamed")
            resolved = resolver.resolve(root, platform="win32", environment={"LOCALAPPDATA": "C:/x"})
            self.assertEqual("renamed", resolved.name)

    def test_windows_without_any_home_variable_falls_back_to_the_repository(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.repository(folder)
            self.assertEqual(root / "build", resolver.resolve(root, platform="win32", environment={}))

    def test_missing_or_empty_build_root_is_rejected_rather_than_reported_on(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(ValueError):
                resolver.require_results(Path(folder) / "absent")
            with self.assertRaises(ValueError):
                resolver.require_results(folder)

    def test_build_root_with_results_is_accepted(self):
        with tempfile.TemporaryDirectory() as folder:
            results = Path(folder) / "module" / "test-results" / "test"
            results.mkdir(parents=True)
            (results / "TEST-x.xml").write_text("<testsuite/>", encoding="utf-8")
            self.assertEqual(Path(folder), resolver.require_results(folder))

    def test_repository_root_is_discovered_from_this_file(self):
        self.assertEqual(REPOSITORY, resolver.repository_root())


if __name__ == "__main__":
    unittest.main()
