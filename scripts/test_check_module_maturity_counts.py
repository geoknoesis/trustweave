import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("maturity", Path(__file__).with_name("check-module-maturity-counts.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

REPOSITORY = Path(__file__).resolve().parents[1]


class ModuleMaturityCountsTest(unittest.TestCase):
    def project(self, folder, annotated, stated):
        root = Path(folder)
        tests = root / "signatures" / "xades" / "src" / "test" / "kotlin"
        tests.mkdir(parents=True)
        body = "".join(f"    @Test\n    fun t{i}() {{}}\n" for i in range(annotated))
        (tests / "T.kt").write_text(f"class T {{\n{body}}}\n", encoding="utf-8")
        doc = root / "module-maturity.md"
        doc.write_text(f"| Module | Tests |\n|---|---|\n| `signatures:xades` | {stated} | note |\n", encoding="utf-8")
        return root, doc

    def test_matching_count_passes(self):
        with tempfile.TemporaryDirectory() as folder:
            root, doc = self.project(folder, 20, 20)
            self.assertEqual([], checker.check(root, doc, 0.25))

    def test_a_count_within_tolerance_below_the_real_one_passes(self):
        with tempfile.TemporaryDirectory() as folder:
            root, doc = self.project(folder, 20, 16)
            self.assertEqual([], checker.check(root, doc, 0.25))

    def test_a_stale_low_count_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            root, doc = self.project(folder, 82, 3)
            problems = checker.check(root, doc, 0.25)
            self.assertEqual(1, len(problems))
            self.assertIn("update the row", problems[0])

    def test_an_overstated_count_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            root, doc = self.project(folder, 5, 9)
            self.assertIn("only 5 are annotated", checker.check(root, doc, 0.25)[0])

    def test_a_module_without_sources_is_reported(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            doc = root / "m.md"
            doc.write_text("| Module | Tests |\n|---|---|\n| `nope:missing` | 1 | x |\n", encoding="utf-8")
            self.assertIn("no sources", checker.check(root, doc, 0.25)[0])

    def test_the_committed_document_matches_the_sources(self):
        doc = REPOSITORY / "docs/api-reference/module-maturity.md"
        self.assertEqual([], checker.check(REPOSITORY, doc, 0.25))


if __name__ == "__main__":
    unittest.main()
