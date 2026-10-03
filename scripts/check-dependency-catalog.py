"""Fail CI when a module declares a dependency version outside the version catalog.

`gradle/libs.versions.toml` is the single place dependency versions are reviewed and the only
place Dependabot updates. A coordinate written as a literal string in a module's build file is
invisible to both: the catalog moves and the module stays pinned, so two versions of the same
library end up on the classpath and a security update silently misses the module that needed it.

The Android reference wallet is a separate Gradle build with no catalog of its own and is skipped.
"""
import argparse
import os
import re
import sys
from pathlib import Path

# group:name:version anywhere in a dependency declaration, however deeply it is wrapped:
# `implementation("g:a:v")`, `implementation(platform("g:a:v"))`,
# `api(enforcedPlatform("g:a:v"))`, `add("x", "g:a:v")`, `force("g:a:v")` and so on. Version-less
# coordinates (platform BOM members, project accessors) are not matched, and neither is
# `libs.`-style catalog access.
COORDINATE = re.compile(r"""["']([A-Za-z][\w.\-]*):([\w.\-]+):([^"']+)["']""")
# A call to any dependency configuration (the standard ones, source-set prefixed variants such
# as `testFixturesImplementation` or `integrationTestRuntimeOnly`, plugin classpaths) or to one of
# the resolution-strategy helpers that also pins a version.
DECLARATION = re.compile(
    r"""^\s*(?:\w*(?:[Ii]mplementation|[Aa]pi|[Rr]untimeOnly|[Cc]ompileOnly|[Cc]lasspath|Plugins)
        |api|annotationProcessor|kapt|ksp|classpath|add|force|constraints)\s*\(""",
    re.X,
)
SKIPPED = ("reference-wallet/android",)
# "group:name:${libs.versions.x.get()}" already reads its version from the catalog.
CATALOG_INTERPOLATION = re.compile(r"^\$\{\s*libs\.versions\.[\w.]+\.get\(\)\s*\}$")


# Pruned at the directory level: walking into them costs far more than reading every build script.
PRUNED = {"build", "node_modules", ".git", ".gradle", "_site", ".claude"}


def build_scripts(root, skipped=SKIPPED):
    """Yield (relative path, file path) for every build script this repository owns."""
    root = Path(root)
    for folder, directories, files in os.walk(root):
        directories[:] = sorted(name for name in directories if name not in PRUNED)
        if "build.gradle.kts" not in files:
            continue
        script = Path(folder) / "build.gradle.kts"
        relative = script.relative_to(root).as_posix()
        if relative.startswith(skipped):
            continue
        yield relative, script


def offenders(root, skipped=SKIPPED):
    """Return (path, line number, coordinate) for every literal versioned coordinate."""
    found = []
    for relative, script in sorted(build_scripts(root, skipped)):
        for number, line in enumerate(script.read_text(encoding="utf-8-sig").splitlines(), start=1):
            if not DECLARATION.match(line):
                continue
            for match in COORDINATE.finditer(line):
                version = match.group(3)
                if CATALOG_INTERPOLATION.match(version):
                    continue
                if any(character.isdigit() for character in version):
                    found.append((relative, number, ":".join(match.groups())))
    return found


def scripts_scanned(root, skipped=SKIPPED):
    return sum(1 for _ in build_scripts(root, skipped))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    args = parser.parse_args()
    try:
        found = offenders(args.root)
        count = scripts_scanned(args.root)
    except OSError as error:
        print(f"Invalid build scripts: {error}", file=sys.stderr)
        sys.exit(1)
    for path, number, coordinate in found:
        print(f"{path}:{number}: {coordinate} is declared outside gradle/libs.versions.toml", file=sys.stderr)
    if found:
        print(
            f"{len(found)} literal coordinates. Add them to the catalog and reference them as libs.<alias>.",
            file=sys.stderr,
        )
        sys.exit(1)
    print(f"Checked {count} build scripts; every dependency version comes from the catalog")
