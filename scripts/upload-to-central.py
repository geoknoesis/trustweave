#!/usr/bin/env python3
"""Upload the staged, checksummed and attested release directory to Maven Central.

This is the LAST step of the release: it runs only after build/release-staging holds SHA256SUMS and the
attestations were created over exactly those bytes. Nothing is rebuilt here. Before anything leaves the
machine the script re-verifies the directory:

  * every file in SHA256SUMS exists and matches its digest;
  * every Maven file that is NOT in SHA256SUMS is a checksum sidecar (.md5/.sha1/.sha256/.sha512) whose
    content equals the digest of the file it sits next to (maven-metadata* is left out of the bundle);
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


def bundle_files(staging):
    """Relative posix paths of the Maven-layout files to upload (sorted)."""
    names = []
    for path in sorted(Path(staging).rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(staging).as_posix()
        if "/" not in relative or path.name.startswith("maven-metadata"):
            continue  # SHA256SUMS, aggregate SBOM and Gradle's repository metadata are not bundled
        names.append(relative)
    return names


def verify_staging(staging):
    """Return (problems, files to upload). Problems are strings; an empty list means safe to upload."""
    staging = Path(staging)
    sums_file = staging / "SHA256SUMS"
    if not sums_file.is_file():
        return ["SHA256SUMS is missing; the release was not checksummed"], []
    try:
        sums = read_sums(sums_file)
    except ValueError as error:
        return [str(error)], []
    problems = []
    for relative, expected in sums.items():
        target = staging / relative
        if not target.is_file():
            problems.append(f"{relative}: listed in SHA256SUMS but missing")
        elif file_digest(target) != expected:
            problems.append(f"{relative}: does not match SHA256SUMS")
    files = bundle_files(staging)
    for relative in files:
        if relative in sums:
            continue
        suffix = Path(relative).suffix
        base = relative[: -len(suffix)] if suffix in SIDECARS else None
        if base is None:
            problems.append(f"{relative}: would be uploaded but is not covered by SHA256SUMS")
        elif not (staging / base).is_file():
            problems.append(f"{relative}: checksum sidecar without a file to check")
        else:
            recorded = (staging / relative).read_text(encoding="utf-8").split()
            actual = file_digest(staging / base, SIDECARS[suffix])
            if not recorded or recorded[0].lower() != actual:
                problems.append(f"{relative}: does not match {Path(base).name}")
    if not files:
        problems.append("no Maven files to upload")
    return problems, files


def build_bundle(staging, files, destination):
    """Write a deterministic zip of `files` (relative to staging) and return its path."""
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as bundle:
        for relative in sorted(files):
            info = zipfile.ZipInfo(relative, FIXED_ZIP_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            bundle.writestr(info, (Path(staging) / relative).read_bytes())
    return destination


def multipart(field, filename, payload):
    boundary = uuid.uuid4().hex
    head = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode()
    return f"multipart/form-data; boundary={boundary}", head + payload + f"\r\n--{boundary}--\r\n".encode()


def upload(bundle, name, publishing_type, username, password, api=DEFAULT_API, opener=urllib.request.urlopen):
    """POST the bundle; returns the deployment id the Portal answers with."""
    token = base64.b64encode(f"{username}:{password}".encode()).decode()
    content_type, body = multipart("bundle", bundle.name, bundle.read_bytes())
    query = urllib.parse.urlencode({"name": name, "publishingType": publishing_type})
    request = urllib.request.Request(
        f"{api.rstrip('/')}/upload?{query}",
        data=body,
        method="POST",
        headers={"Authorization": f"Bearer {token}", "Content-Type": content_type},
    )
    try:
        with opener(request, timeout=600) as response:
            return response.read().decode().strip()
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"Central rejected the upload: HTTP {error.code} {error.read().decode(errors='replace')[:500]}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--staging", type=Path, default=Path("build/release-staging"))
    parser.add_argument("--name", required=True, help="deployment name shown in the Portal, e.g. trustweave-0.7.0")
    parser.add_argument("--publishing-type", choices=PUBLISHING_TYPES, default="USER_MANAGED")
    parser.add_argument("--bundle", type=Path, help="where to write the zip (default: <staging>/../central-bundle.zip)")
    parser.add_argument("--api", default=os.environ.get("TRUSTWEAVE_CENTRAL_API", DEFAULT_API))
    parser.add_argument("--dry-run", action="store_true", help="verify and build the bundle; do not contact Central")
    parser.add_argument("--list", action="store_true", help="print every bundle entry")
    args = parser.parse_args(argv)

    problems, files = verify_staging(args.staging)
    if problems:
        print(f"Refusing to upload: {len(problems)} problem(s) in {args.staging}", file=sys.stderr)
        for line in problems[:50]:
            print(f"  {line}", file=sys.stderr)
        return 1
    username = os.environ.get("TRUSTWEAVE_PUBLISH_USERNAME", "")
    password = os.environ.get("TRUSTWEAVE_PUBLISH_PASSWORD", "")
    if not args.dry_run and not (username and password):
        print("TRUSTWEAVE_PUBLISH_USERNAME and TRUSTWEAVE_PUBLISH_PASSWORD are required to upload.", file=sys.stderr)
        return 2
    bundle = build_bundle(args.staging, files, args.bundle or args.staging.resolve().parent / "central-bundle.zip")
    if args.list:
        for relative in files:
            print(relative)
    print(f"Verified {len(files)} files against SHA256SUMS; bundle {bundle} sha256 {file_digest(bundle)}")
    if args.dry_run:
        print("Dry run: nothing was sent to Central.")
        return 0
    deployment = upload(bundle, args.name, args.publishing_type, username, password, args.api)
    print(f"Uploaded to Central as deployment {deployment} ({args.publishing_type}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
