"""Fail CI when a test container image is pulled from a tag that can move or vanish.

A test that names `:latest` runs against whatever that tag points at today, so it is not
reproducible and cannot be bisected. Worse, a tag can disappear entirely: `minio/minio`'s Docker
Hub copy of RELEASE.2025-09-07T16-13-09Z stopped resolving, and because the image was cached
locally and the CI task came from the build cache, the break stayed invisible until a cache miss
turned it into a red build on main.

Rules:
  * `:latest` is never acceptable.
  * Everything else needs an explicit version tag or an `@sha256:` digest.
  * Images that genuinely cannot be pinned are recorded in config/container-images.json with a
    reason, and that list may only shrink.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

PRUNE = {"build", "node_modules", ".git", ".gradle", "_site", ".claude", ".trunk", ".idea"}
POLICY = "config/container-images.json"
# A quoted image reference: optional registry host, path, then :tag or @digest.
IMAGE = re.compile(
    r'"(?P<ref>(?:[a-z0-9][a-z0-9.-]*(?::\d+)?/)?'
    r'[a-z0-9][a-z0-9._/-]*'
    r'(?::(?P<tag>[A-Za-z0-9][A-Za-z0-9._-]*)|@(?P<digest>sha256:[0-9a-f]{64})))"'
)
# Only strings that are actually used as container images.
CONTAINER_CONTEXT = re.compile(r"GenericContainer|PostgreSQLContainer|DockerImageName|IMAGE|IMAGE_NAME")


def scan(root):
    """Return (unpinned, scanned) — the references that break the rules, and how many were seen."""
    root = Path(root)
    unpinned, scanned = [], 0
    for folder, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in PRUNE]
        for name in files:
            if not name.endswith(".kt"):
                continue
            full = Path(folder) / name
            relative = full.relative_to(root).as_posix()
            text = full.read_text(encoding="utf-8", errors="replace")
            if not CONTAINER_CONTEXT.search(text):
                continue
            for number, line in enumerate(text.splitlines(), start=1):
                if not CONTAINER_CONTEXT.search(line):
                    continue
                match = IMAGE.search(line)
                if not match:
                    continue
                scanned += 1
                tag = match.group("tag")
                if match.group("digest") or (tag and tag != "latest"):
                    continue
                unpinned.append({"file": relative, "line": number, "image": match.group("ref")})
    return unpinned, scanned


def check(root):
    root = Path(root)
    policy = json.loads((root / POLICY).read_text(encoding="utf-8-sig"))
    allowed = {entry["image"]: entry.get("reason", "") for entry in policy["allowed"]}
    recorded = policy["recorded"]
    if len(allowed) != recorded:
        raise ValueError(f"{POLICY}: recorded {recorded} does not match its own list of {len(allowed)}")
    for image, reason in allowed.items():
        if not reason.strip():
            raise ValueError(f"{POLICY}: {image} is allowed without a reason")

    unpinned, scanned = scan(root)
    failures = []
    seen = set()
    for entry in unpinned:
        seen.add(entry["image"])
        if entry["image"] not in allowed:
            failures.append(
                f"{entry['file']}:{entry['line']}: {entry['image']} is a moving tag. "
                f"Use a version tag or an @sha256 digest, or record it in {POLICY} with a reason."
            )
    for image in sorted(set(allowed) - seen):
        failures.append(f"{POLICY}: {image} is recorded but no longer used; remove it")
    if len(seen) > recorded:
        failures.append(f"Unpinned container images grew from {recorded} to {len(seen)}")
    return failures, scanned, len(seen)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    args = parser.parse_args()
    try:
        failures, scanned, unpinned = check(args.root)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"Invalid container image evidence: {error}", file=sys.stderr)
        sys.exit(1)
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        sys.exit(1)
    print(f"Checked {scanned} container image references; {unpinned} recorded as unpinnable")
