import hashlib
import os
import shutil
import subprocess
import importlib.util
import http.server
import io
import threading
import urllib.error
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

SPEC = importlib.util.spec_from_file_location("upload", Path(__file__).with_name("upload-to-central.py"))
upload = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(upload)


def make_staging(root, tamper_sidecar=False, signed=True):
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
    if signed:
        (base / "core-1.0.jar.asc").write_bytes(b"signature")
        (base / "core-1.0.jar.asc.md5").write_text(hashlib.md5(b"signature").hexdigest())
        lines.append(f"{hashlib.sha256(b'signature').hexdigest()}  org/trustweave/core/1.0/core-1.0.jar.asc")
    (Path(root) / "SHA256SUMS").write_text("\n".join(lines) + "\n")


def write_sums(root, lines):
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
                    "org/trustweave/core/1.0/core-1.0.jar.asc",
                    "org/trustweave/core/1.0/core-1.0.jar.asc.md5",
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


class HardeningTest(unittest.TestCase):
    def test_missing_signature_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp, signed=False)
            problems = upload.verify_staging(tmp)[0]
            self.assertTrue(any("core-1.0.jar: has no .asc signature" in p for p in problems))

    def test_orphan_signature_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            orphan = Path(tmp) / "org/trustweave/core/1.0/ghost.pom.asc"
            orphan.write_bytes(b"sig")
            write_sums(tmp, (Path(tmp) / "SHA256SUMS").read_text().splitlines()
                       + [f"{hashlib.sha256(b'sig').hexdigest()}  org/trustweave/core/1.0/ghost.pom.asc"])
            self.assertTrue(any("signature without a file" in p for p in upload.verify_staging(tmp)[0]))

    def test_unlisted_signature_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            sums = (Path(tmp) / "SHA256SUMS").read_text().splitlines()
            write_sums(tmp, [line for line in sums if not line.endswith(".jar.asc")])
            self.assertTrue(any("core-1.0.jar.asc: would be uploaded" in p for p in upload.verify_staging(tmp)[0]))

    def test_symlinked_file_and_directory_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            outside = Path(tmp) / "outside.txt"
            outside.write_bytes(b"secret")
            link = Path(tmp) / "org/trustweave/core/1.0/linked.jar"
            link.symlink_to(outside)
            (Path(tmp) / "org/linkdir").symlink_to(Path(tmp) / "org/trustweave", target_is_directory=True)
            problems = upload.verify_staging(tmp)[0]
            self.assertTrue(any("linked.jar: is a symlink" in p for p in problems))
            self.assertTrue(any("org/linkdir: is a symlink" in p for p in problems))

    def test_listed_symlink_is_refused_even_when_digest_matches(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            target = Path(tmp) / "org/trustweave/core/1.0/core-1.0.jar"
            real = Path(tmp) / "real.bin"
            real.write_bytes(target.read_bytes())
            target.unlink()
            target.symlink_to(real)
            self.assertTrue(any("core-1.0.jar: is a symlink" in p for p in upload.verify_staging(tmp)[0]))

    def test_read_regular_refuses_symlink_swapped_in_after_listing(self):
        with tempfile.TemporaryDirectory() as tmp:
            real = Path(tmp) / "real"
            real.write_bytes(b"x")
            link = Path(tmp) / "link"
            link.symlink_to(real)
            with self.assertRaises(OSError):
                upload.read_regular(link)

    def test_sums_paths_must_stay_inside_staging(self):
        digest = "a" * 64
        for bad in ("../outside.jar", "/etc/passwd", "a/../../b", "a//b", "./a", "a\\b", "C:/x"):
            with tempfile.TemporaryDirectory() as tmp:
                make_staging(tmp)
                write_sums(tmp, [f"{digest}  {bad}"])
                problems = upload.verify_staging(tmp)[0]
                self.assertTrue(any("not a plain relative path" in p for p in problems), bad)

    def test_sidecar_of_unlisted_file_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_staging(tmp)
            base = Path(tmp) / "org/trustweave/core/1.0"
            (base / "extra.pom").write_bytes(b"pom")
            (base / "extra.pom.sha1").write_text(hashlib.sha1(b"pom").hexdigest())
            problems = upload.verify_staging(tmp)[0]
            self.assertTrue(any("extra.pom.sha1: checksum sidecar for extra.pom, which is not listed" in p for p in problems))

    def test_each_bundle_file_is_read_once_and_zip_holds_hashed_bytes(self):
        reads = []
        real = upload.read_regular

        def counting(path):
            reads.append(Path(path).name)
            return real(path)

        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(upload, "read_regular", counting):
            make_staging(Path(tmp) / "stg")
            problems, files = upload.verify_staging(Path(tmp) / "stg", Path(tmp) / "out.zip")
            self.assertEqual([], problems)
            self.assertEqual(1, reads.count("core-1.0.jar"))
            self.assertEqual(len(files), len([r for r in reads if r != "sbom.cdx.json"]))
            self.assertEqual(b"jar bytes", zipfile.ZipFile(Path(tmp) / "out.zip").read("org/trustweave/core/1.0/core-1.0.jar"))

    def test_file_changed_between_listing_and_reading_is_caught_and_leaves_no_bundle(self):
        real = upload.read_regular

        def swapping(path):
            if Path(path).name == "core-1.0.jar":
                return b"tampered after the checksums were made"
            return real(path)

        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(upload, "read_regular", swapping):
            make_staging(Path(tmp) / "stg")
            problems, _ = upload.verify_staging(Path(tmp) / "stg", Path(tmp) / "out.zip")
            self.assertTrue(any("core-1.0.jar: does not match SHA256SUMS" in p for p in problems))
            self.assertFalse((Path(tmp) / "out.zip").exists())
            self.assertFalse((Path(tmp) / "out.zip.partial").exists())

    def test_verify_signatures_uses_gpg_status_output(self):
        calls = []

        def runner(command, **kwargs):
            calls.append(command)
            return mock.Mock(returncode=0, stdout="[GNUPG:] VALIDSIG AAAA 2026-01-01 1 0 4 0 1 10 00 BBBB\n")

        files = ["a/x.jar", "a/x.jar.asc"]
        self.assertEqual([], upload.verify_signatures("/s", files, "bbbb", runner=runner))
        self.assertEqual(1, len(calls))
        self.assertIn("--verify", calls[0])
        self.assertTrue(any("other than" in p for p in upload.verify_signatures("/s", files, "CCCC", runner=runner)))
        bad = lambda command, **kw: mock.Mock(returncode=1, stdout="")
        self.assertTrue(any("could not verify" in p for p in upload.verify_signatures("/s", files, runner=bad)))
        nostatus = lambda command, **kw: mock.Mock(returncode=0, stdout="")
        self.assertTrue(upload.verify_signatures("/s", files, runner=nostatus))

    @unittest.skipUnless(shutil.which("gpg"), "gpg not installed")
    def test_real_gpg_accepts_good_and_rejects_tampered_signature(self):
        with tempfile.TemporaryDirectory() as tmp:
            home = Path(tmp) / "gnupg"
            home.mkdir(mode=0o700)
            env = dict(os.environ, GNUPGHOME=str(home))
            subprocess.run(["gpg", "--batch", "--passphrase", "", "--quick-gen-key", "t <t@example.invalid>", "default", "default", "never"],
                           env=env, check=True, capture_output=True)
            staging = Path(tmp) / "s" / "g"
            staging.mkdir(parents=True)
            (staging / "x.jar").write_bytes(b"jar")
            subprocess.run(["gpg", "--batch", "--detach-sign", "--armor", str(staging / "x.jar")], env=env, check=True, capture_output=True)
            files = ["g/x.jar", "g/x.jar.asc"]
            with mock.patch.dict(os.environ, env):
                self.assertEqual([], upload.verify_signatures(Path(tmp) / "s", files))
                (staging / "x.jar").write_bytes(b"changed")
                self.assertTrue(upload.verify_signatures(Path(tmp) / "s", files))


class BundleAndMainTest(unittest.TestCase):
    def test_bundle_is_deterministic(self):
        with tempfile.TemporaryDirectory() as tmp:
            staging = Path(tmp) / "stg"
            make_staging(staging)
            files = upload.verify_staging(staging, Path(tmp) / "out" / "a.zip")[1]
            upload.verify_staging(staging, Path(tmp) / "out" / "b.zip")
            first = (Path(tmp) / "out" / "a.zip").read_bytes()
            second = (Path(tmp) / "out" / "b.zip").read_bytes()
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

        def opener(request, timeout):
            seen["request"] = request
            return FakeResponse(b"28570f16-da32-4c14-bd2e-c1acc0782365\n")

        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "b.zip"
            bundle.write_bytes(b"PK")
            result = upload.upload(bundle, "trustweave-1.0", "USER_MANAGED", "u", "p", "https://x/api", opener)
        request = seen["request"]
        self.assertEqual("28570f16-da32-4c14-bd2e-c1acc0782365", result)
        self.assertEqual("POST", request.get_method())
        self.assertIn("name=trustweave-1.0", request.full_url)
        self.assertIn("publishingType=USER_MANAGED", request.full_url)
        self.assertEqual("Bearer dTpw", request.get_header("Authorization"))
        self.assertIn(b'name="bundle"', request.data)

    def run_upload(self, opener, api="https://x/api"):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "b.zip"
            bundle.write_bytes(b"PK")
            return upload.upload(bundle, "n", "USER_MANAGED", "u", "p", api, opener)

    def test_empty_or_non_id_body_is_refused(self):
        for body in (b"", b"  \n", b"<html>error</html>", b'{"error":"x"}', b"two words here", b"short"):
            with self.assertRaises(RuntimeError, msg=body):
                self.run_upload(lambda request, timeout, body=body: FakeResponse(body))

    def test_http_error_becomes_runtime_error_with_body(self):
        def opener(request, timeout):
            raise urllib.error.HTTPError(request.full_url, 401, "Unauthorized", {}, io.BytesIO(b"bad token"))

        with self.assertRaisesRegex(RuntimeError, "HTTP 401 bad token"):
            self.run_upload(opener)

    def test_main_reports_upload_failure_without_traceback(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(upload, "upload", side_effect=RuntimeError("boom")), \
                mock.patch.dict("os.environ", {"TRUSTWEAVE_PUBLISH_USERNAME": "u", "TRUSTWEAVE_PUBLISH_PASSWORD": "p"}, clear=True):
            staging = Path(tmp) / "release-staging"
            make_staging(staging)
            self.assertEqual(1, upload.main(["--staging", str(staging), "--name", "n"]))

    def test_plain_http_to_a_remote_host_is_refused(self):
        with self.assertRaisesRegex(RuntimeError, "must be https"):
            self.run_upload(lambda request, timeout: FakeResponse(b"x"), api="http://central.example/api")

    def test_redirect_is_not_followed_and_credentials_go_nowhere_else(self):
        hits = []

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                hits.append((self.path, self.headers.get("Authorization")))
                self.rfile.read(int(self.headers.get("Content-Length", 0)))
                self.send_response(307)
                self.send_header("Location", f"http://127.0.0.1:{self.server.server_port}/elsewhere")
                self.end_headers()

            def log_message(self, *args):
                pass

        server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with self.assertRaisesRegex(RuntimeError, "HTTP 307"):
                self.run_upload(upload.default_opener, api=f"http://127.0.0.1:{server.server_port}/api")
        finally:
            server.shutdown()
            server.server_close()
        self.assertEqual(1, len(hits))
        self.assertTrue(hits[0][0].startswith("/api/upload"))


class FakeResponse(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


if __name__ == "__main__":
    unittest.main()
