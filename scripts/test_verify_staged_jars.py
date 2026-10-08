import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("verifyjars", Path(__file__).with_name("verify-staged-jars.py"))
verifyjars = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verifyjars)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def staged(root, name, data):
    path = Path(root) / "org/trustweave" / name.rsplit("-", 1)[0] / "1.0" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


class VerifyStagedJarsTest(unittest.TestCase):
    def manifest(self, **jars):
        return {"jar_sha256": {f"core/libs/{name}": digest(data) for name, data in jars.items()}}

    def test_matching_jars_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            staged(tmp, "core-1.0.jar", b"a")
            self.assertEqual([], verifyjars.verify(self.manifest(**{"core-1.0.jar": b"a"}), tmp))

    def test_different_bytes_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            staged(tmp, "core-1.0.jar", b"b")
            problems = verifyjars.verify(self.manifest(**{"core-1.0.jar": b"a"}), tmp)
            self.assertEqual(1, len(problems))
            self.assertIn("differs", problems[0])

    def test_staged_jar_missing_from_evidence_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            staged(tmp, "core-1.0.jar", b"a")
            staged(tmp, "other-1.0.jar", b"z")
            problems = verifyjars.verify(self.manifest(**{"core-1.0.jar": b"a"}), tmp)
            self.assertTrue(any("other-1.0.jar" in p for p in problems))

    def test_javadoc_skipped_and_extra_sources_tolerated(self):
        with tempfile.TemporaryDirectory() as tmp:
            staged(tmp, "core-1.0.jar", b"a")
            staged(tmp, "core-1.0-javadoc.jar", b"dokka")
            staged(tmp, "core-1.0-sources.jar", b"src")
            self.assertEqual([], verifyjars.verify(self.manifest(**{"core-1.0.jar": b"a"}), tmp))

    def test_sources_compared_when_evidence_has_them(self):
        with tempfile.TemporaryDirectory() as tmp:
            staged(tmp, "core-1.0.jar", b"a")
            staged(tmp, "core-1.0-sources.jar", b"changed")
            problems = verifyjars.verify(self.manifest(**{"core-1.0.jar": b"a", "core-1.0-sources.jar": b"src"}), tmp)
            self.assertEqual(1, len(problems))

    def test_missing_manifest_section_and_empty_staging_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIn("jar_sha256", verifyjars.verify({}, tmp)[0])
            self.assertTrue(verifyjars.verify(self.manifest(**{"core-1.0.jar": b"a"}), tmp))

    def test_conflicting_duplicate_names_fail(self):
        manifest = {"jar_sha256": {"a/libs/x.jar": digest(b"1"), "b/libs/x.jar": digest(b"2")}}
        self.assertIn("two different jars", verifyjars.verify(manifest, ".")[0])


if __name__ == "__main__":
    unittest.main()
