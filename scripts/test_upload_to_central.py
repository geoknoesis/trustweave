import hashlib
import importlib.util
import io
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

SPEC = importlib.util.spec_from_file_location("upload", Path(__file__).with_name("upload-to-central.py"))
upload = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(upload)


def make_staging(root, tamper_sidecar=False):
    base = Path(root) / "org/trustweave/core/1.0"
    base.mkdir(parents=True)
    jar = base / "core-1.0.jar"
    jar.write_bytes(b"jar bytes")
    (base / "core-1.0.jar.sha1").write_text(hashlib.sha1(b"jar bytes").hexdigest() if not tamper_sidecar else "0" * 40)
    (base / "core-1.0.jar.md5").write_text(hashlib.md5(b"jar bytes").hexdigest())
    (Path(root) / "org/trustweave/core/maven-metadata.xml").write_text("<m/>")
    (Path(root) / "sbom.cdx.json").write_text("{}")
    lines = [
        f"{hashlib.sha256(b'jar bytes').hexdigest()}  org/trustweave/core/1.0/core-1.0.jar",
        f"{hashlib.sha256(b'{}').hexdigest()}  sbom.cdx.json",
    ]
    (Path(root) / "SHA256SUMS").write_text("\n".join(lines) + "\n")


class VerifyTest(unittest.TestCase):
    def test_clean_staging_verifies_and_bundles_only_maven_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            problems, files = upload.verify_staging(tmp)
            self.assertEqual([], problems)
            self.assertEqual(
                [
                    "org/trustweave/core/1.0/core-1.0.jar",
                    "org/trustweave/core/1.0/core-1.0.jar.md5",
                    "org/trustweave/core/1.0/core-1.0.jar.sha1",
                ],
                files,
            )

    def test_missing_sums_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            (Path(tmp) / "SHA256SUMS").unlink()
            self.assertIn("SHA256SUMS is missing", upload.verify_staging(tmp)[0][0])

    def test_modified_file_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            (Path(tmp) / "org/trustweave/core/1.0/core-1.0.jar").write_bytes(b"changed")
            self.assertTrue(any("does not match SHA256SUMS" in p for p in upload.verify_staging(tmp)[0]))

    def test_extra_unlisted_file_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            (Path(tmp) / "org/trustweave/core/1.0/extra.jar").write_bytes(b"x")
            self.assertTrue(any("not covered by SHA256SUMS" in p for p in upload.verify_staging(tmp)[0]))

    def test_bad_sidecar_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp, tamper_sidecar=True)
            self.assertTrue(any("core-1.0.jar.sha1" in p for p in upload.verify_staging(tmp)[0]))

    def test_missing_listed_file_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            (Path(tmp) / "sbom.cdx.json").unlink()
            self.assertTrue(any("missing" in p for p in upload.verify_staging(tmp)[0]))


class BundleAndMainTest(unittest.TestCase):
    def test_bundle_is_deterministic(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            _, files = upload.verify_staging(tmp)
            first = upload.build_bundle(tmp, files, Path(tmp) / "out" / "a.zip").read_bytes()
            second = upload.build_bundle(tmp, files, Path(tmp) / "out" / "b.zip").read_bytes()
            self.assertEqual(first, second)
            self.assertEqual(files, zipfile.ZipFile(io.BytesIO(first)).namelist())

    def test_dry_run_needs_no_credentials_and_does_not_call_network(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(upload.urllib.request, "urlopen") as net, \
                mock.patch.dict("os.environ", {}, clear=True):
            staging = Path(tmp) / "release-staging"
            make_staging(staging)
            code = upload.main(["--staging", str(staging), "--name", "n", "--dry-run"])
            self.assertEqual(0, code)
            net.assert_not_called()
            self.assertTrue((Path(tmp) / "central-bundle.zip").is_file())

    def test_real_run_without_credentials_fails_before_upload(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict("os.environ", {}, clear=True):
            staging = Path(tmp) / "release-staging"
            make_staging(staging)
            self.assertEqual(2, upload.main(["--staging", str(staging), "--name", "n"]))

    def test_tampered_staging_never_reaches_upload(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(upload, "upload") as send:
            staging = Path(tmp) / "release-staging"
            make_staging(staging)
            (staging / "org/trustweave/core/1.0/core-1.0.jar").write_bytes(b"changed")
            self.assertEqual(1, upload.main(["--staging", str(staging), "--name", "n", "--dry-run"]))
            send.assert_not_called()

    def test_upload_builds_expected_request(self):
        seen = {}

        class Response(io.BytesIO):
            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

        def opener(request, timeout):
            seen["request"] = request
            return Response(b"deployment-123\n")

        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "b.zip"
            bundle.write_bytes(b"PK")
            result = upload.upload(bundle, "trustweave-1.0", "USER_MANAGED", "u", "p", "https://x/api", opener)
        request = seen["request"]
        self.assertEqual("deployment-123", result)
        self.assertEqual("POST", request.get_method())
        self.assertIn("name=trustweave-1.0", request.full_url)
        self.assertIn("publishingType=USER_MANAGED", request.full_url)
        self.assertEqual("Bearer dTpw", request.get_header("Authorization"))
        self.assertIn(b'name="bundle"', request.data)


if __name__ == "__main__":
    unittest.main()
