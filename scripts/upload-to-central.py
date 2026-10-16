#!/usr/bin/env python3
"""Upload the staged, checksummed and attested release directory to Maven Central.

This is the LAST step of the release: it runs only after build/release-staging holds SHA256SUMS and the
attestations were created over exactly those bytes. Nothing is rebuilt here. Before anything leaves the
machine the script re-verifies the directory:

  * every file in SHA256SUMS exists and matches its digest;
  * every Maven file that is NOT in SHA256SUMS is a checksum sidecar (.md5/.sha1/.sha256/.sha512) whose
    content equals the digest of the file it sits next to (maven-metadata* is left out of the bundle);
  * every non-sidecar Maven file has a detached `.asc` signature that is itself listed in SHA256SUMS (and a
    `.asc` never stands alone); with --verify-signatures each one is also checked with gpg against the keys
    in the active GNUPGHOME (the workflow imports the release public key there when one is configured);
  * SHA256SUMS paths are relative and stay inside the staging directory (no absolute path, no `..`), and no
    symlink exists anywhere under it (files are opened with O_NOFOLLOW);
  * each file is read exactly once: the same bytes are hashed, checked against its sidecars and written into
    the zip, so nothing can change between verification and bundling;
  * the bundle is a deterministic zip of the Maven-layout files (the root-level SHA256SUMS and aggregate
    SBOM are release assets, not part of the Maven repository, and are not uploaded).

Then it POSTs the zip to the Central Portal Publisher API (POST {api}/upload?name=..&publishingType=..,
Authorization: Bearer base64(user:password), multipart field "bundle") with the credentials from
TRUSTWEAVE_PUBLISH_USERNAME / TRUSTWEAVE_PUBLISH_PASSWORD (a Portal user token). The default publishing
type is USER_MANAGED: the deployment waits in the Portal for a person to release it. Pass
--publishing-type AUTOMATIC to release without that review.

--dry-run does everything except the network call and needs no credentials; it writes the bundle (and,
with --list, prints the entries) so it can be inspected. The HTTP exchange itself is NOT covered by the unit
tests; only a real tag run proves it (see SECURITY.md / docs/contributing/release-validation.md).

    python scripts/upload-to-central.py --staging build/release-staging --name trustweave-0.7.0 --dry-run
"""
import argparse
import base64
import hashlib
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
from pathlib import Path

DEFAULT_API = "https://central.sonatype.com/api/v1/publisher"
SIDECARS = {".md5": "md5", ".sha1": "sha1", ".sha256": "sha256", ".sha512": "sha512"}
PUBLISHING_TYPES = ("USER_MANAGED", "AUTOMATIC")
FIXED_ZIP_TIME = (1980, 1, 1, 0, 0, 0)
SIGNATURE = ".asc"
# Central answers with a UUID; accept any single token of id characters, but never HTML/JSON/whitespace.
DEPLOYMENT_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{7,127}")


def read_sums(sums_file):
    """{relative path: sha256} from a `sha256sum` file ('<hex>  <path>' or '<hex> *<path>')."""
    sums = {}
    for number, line in enumerate(sums_file.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        digest, _, path = line.partition(" ")
        path = path.lstrip(" *")
        if len(digest) != 64 or not path:
            raise ValueError(f"{sums_file.name}:{number}: not a sha256sum line")
        if (
            path.startswith("/")
            or "\\" in path
            or "\0" in path
            or re.match(r"^[A-Za-z]:", path)
            or any(part in ("", ".", "..") for part in path.split("/"))
        ):
            raise ValueError(f"{sums_file.name}:{number}: {path!r} is not a plain relative path inside the staging directory")
        if path in sums:
            raise ValueError(f"{sums_file.name}:{number}: {path} listed twice")
        sums[path] = digest
    if not sums:
        raise ValueError(f"{sums_file.name} is empty")
    return sums


def file_digest(path, algorithm="sha256"):
    digest = hashlib.new(algorithm)
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_symlinks(staging):
    """Relative paths of every symlink (file or directory) under staging."""
    found = []
    for directory, subdirs, names in os.walk(staging, followlinks=False):
        for name in subdirs + names:
            full = Path(directory) / name
            if full.is_symlink():
                found.append(full.relative_to(staging).as_posix())
    return sorted(found)


def bundle_files(staging):
    """Relative posix paths of the Maven-layout regular files to upload (sorted). Symlinks are never listed."""
    names = []
    for path in sorted(Path(staging).rglob("*")):
        if path.is_symlink() or not path.is_file():
            continue
        relative = path.relative_to(staging).as_posix()
        if "/" not in relative or path.name.startswith("maven-metadata"):
            continue  # SHA256SUMS, aggregate SBOM and Gradle's repository metadata are not bundled
        names.append(relative)
    return names


def read_regular(path):
    """The whole file as bytes, refusing a symlink (O_NOFOLLOW) at the moment of reading."""
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(descriptor, "rb") as handle:
        return handle.read()


def verify_staging(staging, destination=None):
    """Return (problems, files to upload).

    Problems are strings; an empty list means safe to upload. With `destination` the deterministic bundle is
    written during this same pass from the very bytes that were hashed (and removed again if anything is
    wrong), so there is no window between verifying a file and zipping it.
    """
    staging = Path(staging)
    sums_file = staging / "SHA256SUMS"
    if sums_file.is_symlink() or not sums_file.is_file():
        return ["SHA256SUMS is missing; the release was not checksummed"], []
    try:
        sums = read_sums(sums_file)
    except ValueError as error:
        return [str(error)], []
    problems = [f"{relative}: is a symlink; refusing to follow it" for relative in find_symlinks(staging)]
    files = bundle_files(staging)
    digests = {}
    sidecar_text = {}
    bundle = partial = None
    if destination is not None:
        destination = Path(destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        partial = destination.with_name(destination.name + ".partial")
        bundle = zipfile.ZipFile(partial, "w", zipfile.ZIP_DEFLATED)
    try:
        for relative in files:
            try:
                data = read_regular(staging / relative)
            except OSError as error:
                problems.append(f"{relative}: cannot be read safely ({error.strerror or error})")
                continue
            digests[relative] = {algorithm: hashlib.new(algorithm, data).hexdigest() for algorithm in ("sha256", *SIDECARS.values())}
            if bundle is not None:
                info = zipfile.ZipInfo(relative, FIXED_ZIP_TIME)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                bundle.writestr(info, data)
            if Path(relative).suffix in SIDECARS:
                sidecar_text[relative] = data.decode("utf-8", errors="replace")
            del data
    finally:
        if bundle is not None:
            bundle.close()
    for relative, expected in sums.items():
        target = staging / relative
        if relative in digests:
            actual = digests[relative]["sha256"]
        elif target.is_symlink() or not target.is_file():
            problems.append(f"{relative}: listed in SHA256SUMS but missing")
            continue
        else:
            try:
                actual = hashlib.sha256(read_regular(target)).hexdigest()
            except OSError as error:
                problems.append(f"{relative}: cannot be read safely ({error.strerror or error})")
                continue
        if actual != expected:
            problems.append(f"{relative}: does not match SHA256SUMS")
    for relative in files:
        if relative not in digests:
            continue
        suffix = Path(relative).suffix
        if suffix in SIDECARS:
            base = relative[: -len(suffix)]
            if base not in sums:
                problems.append(f"{relative}: checksum sidecar for {Path(base).name}, which is not listed in SHA256SUMS")
            elif base not in digests:
                problems.append(f"{relative}: checksum sidecar without a file to check")
            else:
                recorded = sidecar_text[relative].split()
                if not recorded or recorded[0].lower() != digests[base][SIDECARS[suffix]]:
                    problems.append(f"{relative}: does not match {Path(base).name}")
            continue
        if relative not in sums:
            problems.append(f"{relative}: would be uploaded but is not covered by SHA256SUMS")
        if suffix == SIGNATURE:
            if relative[: -len(suffix)] not in digests:
                problems.append(f"{relative}: signature without a file to verify")
        elif relative + SIGNATURE not in digests:
            problems.append(f"{relative}: has no {SIGNATURE} signature")
    if not files:
        problems.append("no Maven files to upload")
    if bundle is not None:
        if problems:
            partial.unlink(missing_ok=True)
        else:
            os.replace(partial, destination)
    return problems, files


def verify_signatures(staging, files, expected_fingerprint=None, gpg="gpg", runner=subprocess.run):
    """gpg --verify every .asc in `files` against the keyring in GNUPGHOME; returns problems."""
    problems = []
    for relative in files:
        if not relative.endswith(SIGNATURE):
            continue
        signature, signed = Path(staging) / relative, Path(staging) / relative[: -len(SIGNATURE)]
        result = runner(
            [gpg, "--batch", "--status-fd", "1", "--verify", str(signature), str(signed)],
            capture_output=True,
            text=True,
        )
        valid = [line.split() for line in result.stdout.splitlines() if line.startswith("[GNUPG:] VALIDSIG")]
        if result.returncode != 0 or not valid:
            problems.append(f"{relative}: gpg could not verify the signature")
        elif expected_fingerprint and expected_fingerprint.replace(" ", "").upper() not in (f.upper() for f in valid[0][2:3] + valid[0][-1:]):
            problems.append(f"{relative}: signed by a key other than {expected_fingerprint}")
    return problems


def multipart(field, filename, payload):
    boundary = uuid.uuid4().hex
    head = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode()
    return f"multipart/form-data; boundary={boundary}", head + payload + f"\r\n--{boundary}--\r\n".encode()


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Never follow a redirect: the request carries the Authorization header and the bundle."""

    def redirect_request(self, *args, **kwargs):
        return None


def default_opener(request, timeout):
    return urllib.request.build_opener(_NoRedirect).open(request, timeout=timeout)


def check_api(api):
    parts = urllib.parse.urlsplit(api)
    if parts.scheme != "https" and not (parts.scheme == "http" and parts.hostname in ("localhost", "127.0.0.1", "::1")):
        raise RuntimeError(f"refusing to send credentials to {api}: the Central API must be https")


def upload(bundle, name, publishing_type, username, password, api=DEFAULT_API, opener=default_opener, payload=None):
    """POST the bundle; returns the deployment id the Portal answers with (validated)."""
    check_api(api)
    token = base64.b64encode(f"{username}:{password}".encode()).decode()
    content_type, body = multipart("bundle", bundle.name, bundle.read_bytes() if payload is None else payload)
    query = urllib.parse.urlencode({"name": name, "publishingType": publishing_type})
    request = urllib.request.Request(
        f"{api.rstrip('/')}/upload?{query}",
        data=body,
        method="POST",
        headers={"Authorization": f"Bearer {token}", "Content-Type": content_type},
    )
    try:
        with opener(request, timeout=600) as response:
            answer = response.read().decode(errors="replace").strip()
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"Central rejected the upload: HTTP {error.code} {error.read().decode(errors='replace')[:500]}")
    if not DEPLOYMENT_ID.fullmatch(answer):
        raise RuntimeError(
            f"Central answered with something that is not a deployment id ({answer[:80]!r}); the bundle may or may not "
            "have been accepted, so check the Central Portal deployments before retrying"
        )
    return answer


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--staging", type=Path, default=Path("build/release-staging"))
    parser.add_argument("--name", required=True, help="deployment name shown in the Portal, e.g. trustweave-0.7.0")
    parser.add_argument("--publishing-type", choices=PUBLISHING_TYPES, default="USER_MANAGED")
    parser.add_argument("--bundle", type=Path, help="where to write the zip (default: <staging>/../central-bundle.zip)")
    parser.add_argument("--api", default=os.environ.get("TRUSTWEAVE_CENTRAL_API", DEFAULT_API))
    parser.add_argument("--verify-signatures", action="store_true", help="gpg --verify every .asc against the keys in GNUPGHOME")
    parser.add_argument("--signing-fingerprint", default=os.environ.get("TRUSTWEAVE_SIGNING_FINGERPRINT", ""), help="optional: require this key fingerprint")
    parser.add_argument("--dry-run", action="store_true", help="verify and build the bundle; do not contact Central")
    parser.add_argument("--list", action="store_true", help="print every bundle entry")
    args = parser.parse_args(argv)

    destination = args.bundle or args.staging.resolve().parent / "central-bundle.zip"
    problems, files = verify_staging(args.staging, destination)
    if args.verify_signatures and not problems:
        problems = verify_signatures(args.staging, files, args.signing_fingerprint or None)
        if problems:
            destination.unlink(missing_ok=True)
    if problems:
        print(f"Refusing to upload: {len(problems)} problem(s) in {args.staging}", file=sys.stderr)
        for line in problems[:50]:
            print(f"  {line}", file=sys.stderr)
        return 1
    username = os.environ.get("TRUSTWEAVE_PUBLISH_USERNAME", "")
    password = os.environ.get("TRUSTWEAVE_PUBLISH_PASSWORD", "")
    if not args.dry_run and not (username and password):
        destination.unlink(missing_ok=True)
        print("TRUSTWEAVE_PUBLISH_USERNAME and TRUSTWEAVE_PUBLISH_PASSWORD are required to upload.", file=sys.stderr)
        return 2
    bundle = destination
    payload = bundle.read_bytes()
    if args.list:
        for relative in files:
            print(relative)
    print(f"Verified {len(files)} files against SHA256SUMS; bundle {bundle} sha256 {hashlib.sha256(payload).hexdigest()}")
    if args.dry_run:
        print("Dry run: nothing was sent to Central.")
        return 0
    try:
        deployment = upload(bundle, args.name, args.publishing_type, username, password, args.api, payload=payload)
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"Uploaded to Central as deployment {deployment} ({args.publishing_type}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
