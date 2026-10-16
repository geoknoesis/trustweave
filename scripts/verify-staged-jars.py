#!/usr/bin/env python3
"""Fail unless the jars staged for release are byte-identical to the jars the evidence job validated.

The evidence job builds and tests the tag, then records the SHA-256 of every jar under build/**/libs in
build/reports/validation-manifest.json ("jar_sha256", keyed by build-relative path). The publish job builds
again into the releaseStaging repository (build/release-staging); archives are reproducible, so each staged
jar must hash the same as the evidence jar with the same file name. A difference means the published bytes
are not the tested bytes, and the release stops.

Rules:
  * the set of staged binary jar names (not -sources/-javadoc) must EQUAL the set of binary jar names in the
    evidence manifest minus the allowlist (config/release-unpublished-jars.json: evidence jars that are
    deliberately never published, each with a reason). A jar missing from staging, a staged jar the evidence job
    never built, and an allowlisted jar that is staged anyway all fail;
  * every staged binary jar must hash the same as the evidence jar with that name;
  * a staged -sources jar is compared when the evidence job built one, and must belong to a staged binary jar;
    an evidence -sources jar must be staged unless allowlisted;
  * -javadoc jars are not compared: the evidence build does not run Dokka, publish builds do;
  * the floor on compared jars derives from the manifest (the number of expected binary jars), so it cannot
    drift from a hand-picked guess; --min-jars can only raise it;
  * the manifest must be bound to the commit being released: manifest "head" must equal --expected-commit
    (default $GITHUB_SHA), and it must not be a dirty tree.

    python scripts/verify-staged-jars.py --manifest path/validation-manifest.json --staging build/release-staging
"""
import argparse
import hashlib
import json
import os
import sys
from pathlib import Path


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def evidence_by_name(manifest):
    """{jar file name: sha256}; raises ValueError for a missing section or two different jars with one name."""
    jars = manifest.get("jar_sha256")
    if not isinstance(jars, dict) or not jars:
        raise ValueError("validation manifest has no 'jar_sha256' section")
    by_name = {}
    for relative, digest in jars.items():
        name = relative.rpartition("/")[2]
        if by_name.setdefault(name, digest) != digest:
            raise ValueError(f"evidence holds two different jars named {name}")
    return by_name


def kind(name):
    if name.endswith("-javadoc.jar"):
        return "javadoc"
    if name.endswith("-sources.jar"):
        return "sources"
    return "binary"


def load_allowlist(path):
    """{jar name: reason} from the allowlist file; every entry must carry a non-empty reason."""
    data = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    allowed = {}
    for item in data.get("allowed", []):
        name, reason = item.get("name"), item.get("reason")
        if not isinstance(name, str) or not name.endswith(".jar") or "/" in name:
            raise ValueError(f"allowlist entry has no jar file name: {item!r}")
        if not isinstance(reason, str) or not reason.strip():
            raise ValueError(f"allowlist entry {name} has no reason")
        if name in allowed:
            raise ValueError(f"allowlist lists {name} twice")
        allowed[name] = reason
    return allowed


def verify(manifest, staging, min_jars=None, allowlist=None, expected_commit=None):
    """Return a list of problems; empty means the staged jars are exactly the validated, publishable jars."""
    allowlist = allowlist or {}
    problems = []
    if expected_commit is not None and manifest.get("head") != expected_commit:
        problems.append(f"manifest head {manifest.get('head')!r} is not the commit being released {expected_commit!r}")
    if manifest.get("dirty") is not False:
        problems.append("manifest is not from a clean committed tree (dirty must be false)")
    try:
        evidence = evidence_by_name(manifest)
    except ValueError as error:
        return problems + [str(error)]
    staged = {}
    for jar in sorted(Path(staging).rglob("*.jar")):
        if kind(jar.name) != "javadoc":
            staged.setdefault(jar.name, jar)
    expected_binary = {n for n in evidence if kind(n) == "binary" and n not in allowlist}
    for name in sorted(expected_binary - staged.keys()):
        problems.append(f"{name}: validated by the evidence build but not staged (allowlist it with a reason if it is never published)")
    for name in sorted(n for n in staged if kind(n) == "binary" and n not in evidence):
        problems.append(f"{name}: staged but absent from the evidence build")
    for name in sorted(n for n in staged if n in allowlist):
        problems.append(f"{name}: staged although the allowlist says it is never published")
    for name in sorted(n for n in staged if kind(n) == "sources"):
        binary = name[: -len("-sources.jar")] + ".jar"
        if binary not in staged:
            problems.append(f"{name}: sources jar without a staged binary jar")
    for name in sorted(n for n in evidence if kind(n) == "sources" and n not in allowlist and n not in staged):
        problems.append(f"{name}: validated by the evidence build but not staged")
    compared = 0
    for name, jar in staged.items():
        expected = evidence.get(name)
        if expected is None or name in allowlist:
            continue
        actual = sha256(jar)
        if kind(name) == "binary":
            compared += 1
        if actual != expected:
            problems.append(f"{name}: staged {actual} differs from validated {expected}")
    floor = max(len(expected_binary), min_jars or 0, 1)
    if compared < floor:
        problems.append(f"only {compared} jar(s) compared, expected at least {floor} (derived from the manifest)")
    return problems


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--staging", type=Path, default=Path("build/release-staging"))
    parser.add_argument("--min-jars", type=int, default=None, help="optional extra floor; the default floor derives from the manifest")
    parser.add_argument("--allowlist", type=Path, default=Path("config/release-unpublished-jars.json"))
    parser.add_argument("--expected-commit", default=os.environ.get("GITHUB_SHA"), help="default: $GITHUB_SHA")
    parser.add_argument("--no-commit-check", action="store_true", help="skip the commit binding (local use only)")
    args = parser.parse_args(argv)
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print(f"cannot read validation manifest {args.manifest}: {error}", file=sys.stderr)
        return 2
    if not args.expected_commit and not args.no_commit_check:
        print("no --expected-commit and GITHUB_SHA is unset; refusing to skip the commit binding", file=sys.stderr)
        return 2
    try:
        allowlist = load_allowlist(args.allowlist)
    except (OSError, ValueError, AttributeError) as error:
        print(f"cannot read allowlist {args.allowlist}: {error}", file=sys.stderr)
        return 2
    problems = verify(manifest, args.staging, args.min_jars, allowlist, None if args.no_commit_check else args.expected_commit)
    if problems:
        print(f"{len(problems)} staged jar(s) do not match the validated build:", file=sys.stderr)
        for line in problems:
            print(f"  {line}", file=sys.stderr)
        return 1
    print("Staged jars match the validated build.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
