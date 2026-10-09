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
        return {"head": "abc", "dirty": False, "jar_sha256": {f"core/libs/{name}": digest(data) for name, data in jars.items()}}

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

    def test_javadoc_skipped_and_extra_sources_tolerated_with_their_binary(self):
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

    def test_evidence_jar_missing_from_staging_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            staged(tmp, "core-1.0.jar", b"a")
            problems = verifyjars.verify(self.manifest(**{"core-1.0.jar": b"a", "gone-1.0.jar": b"g"}), tmp)
            self.assertTrue(any("gone-1.0.jar" in p and "not staged" in p for p in problems))

    def test_allowlisted_evidence_jar_may_be_absent_but_not_staged(self):
        manifest = self.manifest(**{"core-1.0.jar": b"a", "tool-1.0.jar": b"t"})
        allow = {"tool-1.0.jar": "build-only helper"}
        with tempfile.TemporaryDirectory() as tmp:
            staged(tmp, "core-1.0.jar", b"a")
            self.assertEqual([], verifyjars.verify(manifest, tmp, allowlist=allow))
            staged(tmp, "tool-1.0.jar", b"t")
            problems = verifyjars.verify(manifest, tmp, allowlist=allow)
            self.assertTrue(any("never published" in p for p in problems))

    def test_sources_without_binary_and_unstaged_evidence_sources_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            staged(tmp, "core-1.0.jar", b"a")
            staged(tmp, "lone-1.0-sources.jar", b"s")
            problems = verifyjars.verify(self.manifest(**{"core-1.0.jar": b"a", "core-1.0-sources.jar": b"s"}), tmp)
            self.assertTrue(any("lone-1.0-sources.jar" in p and "without a staged binary" in p for p in problems))
            self.assertTrue(any("core-1.0-sources.jar" in p and "not staged" in p for p in problems))

    def test_floor_derives_from_manifest_not_a_constant(self):
        with tempfile.TemporaryDirectory() as tmp:
            names = {f"m{i}-1.0.jar": b"x%d" % i for i in range(12)}
            for name, data in names.items():
                staged(tmp, name, data)
            self.assertEqual([], verifyjars.verify(self.manifest(**names), tmp))
            # an explicit floor can only raise the bar
            self.assertTrue(any("at least 13" in p for p in verifyjars.verify(self.manifest(**names), tmp, min_jars=13)))

    def test_manifest_head_must_equal_release_commit(self):
        with tempfile.TemporaryDirectory() as tmp:
            staged(tmp, "core-1.0.jar", b"a")
            manifest = self.manifest(**{"core-1.0.jar": b"a"})
            self.assertEqual([], verifyjars.verify(manifest, tmp, expected_commit="abc"))
            self.assertTrue(any("not the commit" in p for p in verifyjars.verify(manifest, tmp, expected_commit="def")))
            manifest["dirty"] = True
            self.assertTrue(any("clean" in p for p in verifyjars.verify(manifest, tmp, expected_commit="abc")))

    def test_allowlist_requires_reason(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "a.json"
            path.write_text('{"allowed":[{"name":"x-1.jar","reason":" "}]}')
            with self.assertRaises(ValueError):
                verifyjars.load_allowlist(path)
            path.write_text('{"allowed":[{"name":"x-1.jar","reason":"why"}]}')
            self.assertEqual({"x-1.jar": "why"}, verifyjars.load_allowlist(path))
            self.assertEqual({}, verifyjars.load_allowlist(Path(__file__).parent.parent / "config/release-unpublished-jars.json"))

    def test_main_requires_commit_binding(self):
        import json, os
        from unittest import mock
        with tempfile.TemporaryDirectory() as tmp:
            manifest = Path(tmp) / "m.json"
            manifest.write_text(json.dumps(self.manifest(**{"core-1.0.jar": b"a"})))
            staged(Path(tmp) / "s", "core-1.0.jar", b"a")
            allow = Path(__file__).parent.parent / "config/release-unpublished-jars.json"
            base = ["--manifest", str(manifest), "--staging", str(Path(tmp) / "s"), "--allowlist", str(allow)]
            with mock.patch.dict(os.environ, {}, clear=True):
                self.assertEqual(2, verifyjars.main(base))
                self.assertEqual(0, verifyjars.main(base + ["--expected-commit", "abc"]))
                self.assertEqual(1, verifyjars.main(base + ["--expected-commit", "zzz"]))

    def test_missing_manifest_section_and_empty_staging_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertTrue(any("jar_sha256" in p for p in verifyjars.verify({}, tmp)))
            self.assertTrue(verifyjars.verify(self.manifest(**{"core-1.0.jar": b"a"}), tmp))

    def test_conflicting_duplicate_names_fail(self):
        manifest = {"jar_sha256": {"a/libs/x.jar": digest(b"1"), "b/libs/x.jar": digest(b"2")}}
        self.assertTrue(any("two different jars" in p for p in verifyjars.verify(manifest, ".")))


if __name__ == "__main__":
    unittest.main()
