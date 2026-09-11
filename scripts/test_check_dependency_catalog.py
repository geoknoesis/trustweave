import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("catalog", Path(__file__).with_name("check-dependency-catalog.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

REPOSITORY = Path(__file__).resolve().parents[1]


class DependencyCatalogTest(unittest.TestCase):
    def build(self, folder, text, relative="module/build.gradle.kts"):
        root = Path(folder)
        script = root / relative
        script.parent.mkdir(parents=True, exist_ok=True)
        script.write_text(text, encoding="utf-8")
        return root

    def test_catalog_accessors_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.build(folder, "dependencies {\n    implementation(libs.gson)\n}\n")
            self.assertEqual([], checker.offenders(root))

    def test_literal_coordinate_is_reported_with_its_location(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.build(folder, 'dependencies {\n    implementation("com.google.code.gson:gson:2.10.1")\n}\n')
            found = checker.offenders(root)
            self.assertEqual(1, len(found))
            self.assertEqual(("module/build.gradle.kts", 2, "com.google.code.gson:gson:2.10.1"), found[0])

    def test_every_declaration_configuration_is_covered(self):
        for configuration in ["api", "implementation", "testImplementation", "compileOnly", "runtimeOnly"]:
            with self.subTest(configuration=configuration), tempfile.TemporaryDirectory() as folder:
                root = self.build(folder, f'dependencies {{\n    {configuration}("a.b:c:1.0")\n}}\n')
                self.assertEqual(1, len(checker.offenders(root)))

    def test_version_less_coordinates_are_allowed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.build(folder, 'dependencies {\n    implementation("com.google.cloud:google-cloud-kms")\n}\n')
            self.assertEqual([], checker.offenders(root))

    def test_catalog_interpolation_is_allowed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.build(
                folder,
                'dependencies {\n    testImplementation("org.slf4j:slf4j-simple:${libs.versions.slf4j.get()}")\n}\n',
            )
            self.assertEqual([], checker.offenders(root))

    def test_generated_and_excluded_trees_are_skipped(self):
        for relative in ["module/build/build.gradle.kts", "reference-wallet/android/app/build.gradle.kts"]:
            with self.subTest(relative=relative), tempfile.TemporaryDirectory() as folder:
                root = self.build(folder, 'dependencies {\n    implementation("a.b:c:1.0")\n}\n', relative)
                self.assertEqual([], checker.offenders(root))

    def test_comments_and_project_accessors_are_not_flagged(self):
        with tempfile.TemporaryDirectory() as folder:
            text = 'dependencies {\n    // implementation("a.b:c:1.0")\n    implementation(project(":common"))\n}\n'
            root = self.build(folder, text)
            self.assertEqual([], checker.offenders(root))

    def test_this_repository_is_fully_catalogued(self):
        self.assertEqual([], checker.offenders(REPOSITORY))


if __name__ == "__main__":
    unittest.main()
