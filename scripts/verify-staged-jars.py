#!/usr/bin/env python3
"""Fail unless the jars staged for release are byte-identical to the jars the evidence job validated.

The evidence job builds and tests the tag, then records the SHA-256 of every jar under build/**/libs in
build/reports/validation-manifest.json ("jar_sha256", keyed by build-relative path). The publish job builds
again into the releaseStaging repository (build/release-staging); archives are reproducible, so each staged
jar must hash the same as the evidence jar with the same file name. A difference means the published bytes
are not the tested bytes, and the release stops.

Rules:
  * every staged binary jar (not -sources/-javadoc) must have an evidence jar of the same name and hash;
  * a staged -sources jar is compared when the evidence job built one;
  * -javadoc jars are not compared: the evidence build does not run Dokka, publish builds do;
  * at least --min-jars jars must have been compared, so an empty or mis-pointed manifest cannot pass.

    python scripts/verify-staged-jars.py --manifest path/validation-manifest.json --staging build/release-staging
"""
import argparse
import hashlib
import json
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


def verify(manifest, staging, min_jars=1):
    """Return a list of problems; empty means every staged jar matches its evidence jar."""
    problems = []
    try:
        evidence = evidence_by_name(manifest)
    except ValueError as error:
        return [str(error)]
    compared = 0
    for jar in sorted(Path(staging).rglob("*.jar")):
        name = jar.name
        if name.endswith("-javadoc.jar"):
            continue
        expected = evidence.get(name)
        if expected is None:
            if not name.endswith("-sources.jar"):
                problems.append(f"{name}: staged but absent from the evidence build")
            continue
        actual = sha256(jar)
        compared += 1
        if actual != expected:
            problems.append(f"{name}: staged {actual} differs from validated {expected}")
    if compared < min_jars:
        problems.append(f"only {compared} jar(s) compared, expected at least {min_jars}")
    return problems


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--staging", type=Path, default=Path("build/release-staging"))
    parser.add_argument("--min-jars", type=int, default=1)
    args = parser.parse_args(argv)
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print(f"cannot read validation manifest {args.manifest}: {error}", file=sys.stderr)
        return 2
    problems = verify(manifest, args.staging, args.min_jars)
    if problems:
        print(f"{len(problems)} staged jar(s) do not match the validated build:", file=sys.stderr)
        for line in problems:
            print(f"  {line}", file=sys.stderr)
        return 1
    print("Staged jars match the validated build.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
