import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("images", Path(__file__).with_name("check-container-images.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

REPOSITORY = Path(__file__).resolve().parents[1]

DIGEST = "sha256:" + "1" * 64


class ContainerImageTest(unittest.TestCase):
    def repo(self, folder, source, policy=None):
        root = Path(folder)
        target = root / "mod" / "src" / "test" / "kotlin"
        target.mkdir(parents=True)
        (target / "T.kt").write_text(source, encoding="utf-8")
        (root / "config").mkdir(parents=True, exist_ok=True)
        (root / checker.POLICY).write_text(
            json.dumps(policy or {"recorded": 0, "allowed": []}), encoding="utf-8"
        )
        return root

    def test_version_tag_and_digest_pass(self):
        for reference in ["postgres:16-alpine", f"quay.io/minio/minio@{DIGEST}", "hashicorp/vault:2.1.0"]:
            with self.subTest(reference=reference), tempfile.TemporaryDirectory() as folder:
                root = self.repo(folder, f'val c = GenericContainer<Nothing>("{reference}")\n')
                unpinned, scanned = checker.scan(root)
                self.assertEqual(([], 1), (unpinned, scanned), reference)

    def test_latest_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.repo(folder, 'val c = GenericContainer<Nothing>("minio/minio:latest")\n')
            failures, _, _ = checker.check(root)
            # Both the rule and the ratchet fire: the tag moves, and the count grew past zero.
            self.assertTrue(any("moving tag" in failure for failure in failures), failures)
            self.assertTrue(any("grew from 0 to 1" in failure for failure in failures), failures)

    def test_recorded_exception_passes_and_needs_a_reason(self):
        source = 'val c = GenericContainer<Nothing>("ghcr.io/x/y:latest")\n'
        with tempfile.TemporaryDirectory() as folder:
            policy = {"recorded": 1, "allowed": [{"image": "ghcr.io/x/y:latest", "reason": "opt-in only"}]}
            root = self.repo(folder, source, policy)
            failures, _, unpinned = checker.check(root)
            self.assertEqual(([], 1), (failures, unpinned))
        with tempfile.TemporaryDirectory() as folder:
            policy = {"recorded": 1, "allowed": [{"image": "ghcr.io/x/y:latest", "reason": "  "}]}
            root = self.repo(folder, source, policy)
            with self.assertRaises(ValueError):
                checker.check(root)

    def test_a_recorded_image_that_is_gone_must_be_removed(self):
        with tempfile.TemporaryDirectory() as folder:
            policy = {"recorded": 1, "allowed": [{"image": "ghcr.io/x/y:latest", "reason": "stale"}]}
            root = self.repo(folder, 'val c = GenericContainer<Nothing>("postgres:16-alpine")\n', policy)
            failures, _, _ = checker.check(root)
            self.assertTrue(any("no longer used" in failure for failure in failures), failures)

    def test_inconsistent_policy_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            policy = {"recorded": 5, "allowed": [{"image": "a:latest", "reason": "r"}]}
            root = self.repo(folder, "", policy)
            with self.assertRaises(ValueError):
                checker.check(root)

    def test_strings_outside_a_container_context_are_ignored(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.repo(folder, 'val url = "https://example.invalid:8080/path"\n')
            self.assertEqual(([], 0), checker.scan(root))

    def test_this_repository_passes(self):
        failures, scanned, _ = checker.check(REPOSITORY)
        self.assertEqual([], failures)
        self.assertGreater(scanned, 0)


if __name__ == "__main__":
    unittest.main()
