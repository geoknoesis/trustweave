"""Copy the JAR behind every Maven component of a CycloneDX SBOM into one directory.

The SBOMs the build produces list each resolved dependency by its coordinates, which is all a
vulnerability scanner needs for an ordinary JAR. A fat JAR is different: it carries other
libraries' classes inside it, and the SBOM names only the outer artifact. org.didcommx:didcomm
0.3.2 is the case that matters here; it embeds nimbus-jose-jwt 9.16-preview.1 and json-smart
2.4.7, neither of which appears in the dependency graph. Scanning the JAR files themselves (OSV
Scanner's java/archive plugin reads the META-INF/maven/**/pom.properties of everything shaded in)
closes that gap.

CycloneDX records each component's SHA-1, and Gradle's module cache stores every downloaded file
under files-2.1/<group>/<name>/<version>/<sha1>/, so the exact file the build used is found
without re-resolving anything. Run it after `./gradlew cyclonedxBom`, which downloads the files
in order to hash them.
"""
import argparse
import json
import os
import shutil
import sys
from pathlib import Path

DEFAULT_CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"


def components(sbom):
    """Yield (group, name, version, sha1) for every Maven component in a CycloneDX document."""
    pending = list(sbom.get("components", []))
    while pending:
        component = pending.pop()
        pending.extend(component.get("components", []))
        if not str(component.get("purl", "")).startswith("pkg:maven/"):
            continue
        sha1 = next(
            (entry.get("content") for entry in component.get("hashes", []) if entry.get("alg") == "SHA-1"),
            None,
        )
        group, name, version = component.get("group"), component.get("name"), component.get("version")
        if group and name and version and sha1:
            yield group, name, version, sha1.lower()


def locate(cache, group, name, version, sha1):
    """Return the cached JAR for one component, or None when it was never downloaded.

    Gradle names the folder after the hash as a number, so leading zeros are dropped.
    """
    base = Path(cache) / group / name / version
    for folder in (base / sha1, base / (sha1.lstrip("0") or "0")):
        jars = sorted(folder.glob("*.jar")) if folder.is_dir() else []
        if jars:
            return jars[0]
    return None


def collect(sboms, cache, output):
    """Copy every component's JAR into output; return (copied, missing coordinates)."""
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    seen, copied, missing = set(), 0, []
    for path in sboms:
        document = json.loads(Path(path).read_text(encoding="utf-8"))
        for group, name, version, sha1 in components(document):
            if sha1 in seen:
                continue
            seen.add(sha1)
            jar = locate(cache, group, name, version, sha1)
            if jar is None:
                missing.append(f"{group}:{name}:{version}")
                continue
            shutil.copy2(jar, output / f"{group}__{jar.name}")
            copied += 1
    return copied, sorted(missing)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sboms", nargs="+", type=Path)
    parser.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--output", type=Path, default=Path("build/reports/osv/jars"))
    args = parser.parse_args()
    try:
        copied, missing = collect(args.sboms, args.cache, args.output)
    except (OSError, ValueError) as error:
        print(f"Cannot collect SBOM jars: {error}", file=sys.stderr)
        sys.exit(1)
    for coordinate in missing:
        print(f"warning: {coordinate} is not in the Gradle cache; its embedded content is not scanned", file=sys.stderr)
    print(f"Copied {copied} jars into {args.output} ({len(missing)} not cached)")
    if copied == 0:
        sys.exit(1)
