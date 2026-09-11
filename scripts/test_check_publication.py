import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("publication", Path(__file__).with_name("check-publication.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

REPOSITORY = Path(__file__).resolve().parents[1]

COMPLETE_SCRIPT = """
publishing {
    repositories {
        maven { url = uri("https://example.invalid") }
    }
    publications {
        pom {
            licenses { license { name.set("AGPL-3.0") } }
            developers { developer { id.set("team") } }
            scm { url.set("https://example.invalid") }
        }
    }
}
"""

COMPLETE_POM = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <artifactId>common</artifactId>
  <name>common</name>
  <description>d</description>
  <url>u</url>
  <licenses><license><name>AGPL-3.0</name></license></licenses>
  <developers><developer><id>team</id></developer></developers>
  <scm><url>u</url></scm>
</project>
"""


class PublicationTest(unittest.TestCase):
    def script(self, folder, text):
        root = Path(folder)
        (root / "build.gradle.kts").write_text(text, encoding="utf-8")
        return root

    def pom(self, folder, text):
        target = Path(folder) / "common" / "publications" / "maven"
        target.mkdir(parents=True)
        (target / "pom-default.xml").write_text(text, encoding="utf-8")
        return Path(folder)

    def test_complete_script_passes(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual([], checker.check_script(self.script(folder, COMPLETE_SCRIPT)))

    def test_each_missing_block_is_reported(self):
        for fragment, expected in [
            ("maven {", "publishing repository"),
            ("scm {", "scm block"),
            ("licenses {", "licenses block"),
            ("developers {", "developers block"),
        ]:
            with self.subTest(fragment=fragment), tempfile.TemporaryDirectory() as folder:
                root = self.script(folder, COMPLETE_SCRIPT.replace(fragment, "removed {"))
                failures = checker.check_script(root)
                self.assertTrue(any(expected in failure for failure in failures), failures)

    def test_dead_documentation_reference_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.script(folder, COMPLETE_SCRIPT + '\n// docs/reference/module-maturity.md\n')
            failures = checker.check_script(root)
            self.assertEqual(1, len(failures))
            self.assertIn("does not exist", failures[0])

    def test_live_documentation_reference_passes(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.script(folder, COMPLETE_SCRIPT + '\n// docs/live.md\n')
            (root / "docs").mkdir()
            (root / "docs" / "live.md").write_text("x", encoding="utf-8")
            self.assertEqual([], checker.check_script(root))

    def test_complete_pom_passes(self):
        with tempfile.TemporaryDirectory() as folder:
            failures, count, orphans = checker.check_poms(self.pom(folder, COMPLETE_POM))
            self.assertEqual(([], 1, 0), (failures, count, orphans))

    def test_pom_without_scm_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            stripped = COMPLETE_POM.replace("<scm><url>u</url></scm>", "")
            failures, _, _ = checker.check_poms(self.pom(folder, stripped))
            self.assertEqual(1, len(failures))
            self.assertIn("scm", failures[0])

    def test_pom_declaring_a_doctype_is_refused_not_parsed(self):
        with tempfile.TemporaryDirectory() as folder:
            hostile = COMPLETE_POM.replace("<project", '<!DOCTYPE project [<!ENTITY x "y">]>\n<project', 1)
            failures, _, _ = checker.check_poms(self.pom(folder, hostile))
            self.assertEqual(1, len(failures))
            self.assertIn("DOCTYPE", failures[0])

    def test_absent_poms_are_not_a_failure(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual(([], 0, 0), checker.check_poms(folder))

    def test_output_for_a_deleted_module_is_ignored_not_failed(self):
        with tempfile.TemporaryDirectory() as folder:
            stripped = COMPLETE_POM.replace("<scm><url>u</url></scm>", "")
            root = self.pom(folder, stripped)
            self.assertEqual(([], 0, 1), checker.check_poms(root, modules={"other"}))
            failures, count, orphans = checker.check_poms(root, modules={"common"})
            self.assertEqual((1, 1, 0), (len(failures), count, orphans))

    def test_module_paths_come_from_settings(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "settings.gradle.kts").write_text(
                'include("common")\ninclude("did:plugins:key")\n', encoding="utf-8"
            )
            self.assertEqual({"common", "did/plugins/key"}, checker.module_paths(root))

    def test_this_repository_is_publishable(self):
        self.assertEqual([], checker.check_script(REPOSITORY))


if __name__ == "__main__":
    unittest.main()
