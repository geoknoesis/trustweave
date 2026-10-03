import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("collect", Path(__file__).with_name("collect-sbom-jars.py"))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


def component(group, name, version, sha1, nested=()):
    return {
        "group": group,
        "name": name,
        "version": version,
        "purl": f"pkg:maven/{group}/{name}@{version}?type=jar",
        "hashes": [{"alg": "MD5", "content": "00"}, {"alg": "SHA-1", "content": sha1}],
        "components": list(nested),
    }


class CollectSbomJarsTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name)
        self.cache = self.root / "cache"

    def tearDown(self):
        self.folder.cleanup()

    def cached(self, group, name, version, sha1):
        folder = self.cache / group / name / version / sha1
        folder.mkdir(parents=True)
        (folder / f"{name}-{version}.jar").write_bytes(b"PK")

    def sbom(self, *items):
        path = self.root / "bom.json"
        path.write_text(json.dumps({"bomFormat": "CycloneDX", "components": list(items)}), encoding="utf-8")
        return path

    def test_cached_component_jars_are_copied(self):
        self.cached("org.didcommx", "didcomm", "0.3.2", "abc")
        sbom = self.sbom(component("org.didcommx", "didcomm", "0.3.2", "ABC"))
        copied, missing = collector.collect([sbom], self.cache, self.root / "out")
        self.assertEqual((1, []), (copied, missing))
        self.assertTrue((self.root / "out" / "org.didcommx__didcomm-0.3.2.jar").is_file())

    def test_gradle_cache_folders_drop_leading_zeros(self):
        self.cached("software.amazon.awssdk", "kms", "2.43.0", "e5deb3")
        sbom = self.sbom(component("software.amazon.awssdk", "kms", "2.43.0", "00e5deb3"))
        self.assertEqual((1, []), collector.collect([sbom], self.cache, self.root / "out"))

    def test_nested_components_are_followed_and_duplicates_copied_once(self):
        self.cached("a.b", "inner", "1.0", "111")
        inner = component("a.b", "inner", "1.0", "111")
        sbom = self.sbom(component("a.b", "outer", "2.0", "222", nested=[inner]), inner)
        copied, missing = collector.collect([sbom], self.cache, self.root / "out")
        self.assertEqual(1, copied)
        self.assertEqual(["a.b:outer:2.0"], missing)

    def test_non_maven_and_unhashed_components_are_ignored(self):
        sbom = self.sbom(
            {"name": "app", "version": "1", "purl": "pkg:npm/app@1"},
            {"group": "a", "name": "b", "version": "1", "purl": "pkg:maven/a/b@1"},
        )
        self.assertEqual((0, []), collector.collect([sbom], self.cache, self.root / "out"))


if __name__ == "__main__":
    unittest.main()
