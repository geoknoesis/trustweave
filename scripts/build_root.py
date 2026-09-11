"""Resolve the directory Gradle actually writes module outputs to.

`build.gradle.kts` centralizes every module's output under `build/<module path>`, except on
Windows, where it redirects to `%LOCALAPPDATA%/TrustWeave/gradle-build/<root name>/<module path>`
so IDE and antivirus file locks stay out of the workspace. `gradle.properties` can opt back in
with `trustweave.windowsInRepoBuild=true`.

Evidence gates that hardcode the in-repo `build/` therefore read a directory Gradle never wrote
on the project's default Windows layout. Worse, a `build/` left over from an earlier run looks
exactly like fresh evidence, so the gate reports on a tree nobody is testing. Resolving the root
the same way the build does, and refusing a root that has no test results at all, removes both
failure modes.
"""
import os
import sys
from pathlib import Path

PROPERTIES = "gradle.properties"
SETTINGS = "settings.gradle.kts"
OPT_OUT = "trustweave.windowsInRepoBuild"


def repository_root(start=None):
    """Walk up from [start] to the directory holding settings.gradle.kts."""
    current = Path(start or Path(__file__).resolve().parent).resolve()
    for candidate in [current, *current.parents]:
        if (candidate / SETTINGS).is_file():
            return candidate
    raise ValueError(f"No {SETTINGS} above {current}")


def in_repo_build_requested(root):
    properties = root / PROPERTIES
    if not properties.is_file():
        return False
    for line in properties.read_text(encoding="utf-8-sig").splitlines():
        stripped = line.strip()
        if stripped.startswith("#") or "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        if key.strip() == OPT_OUT:
            return value.strip().lower() == "true"
    return False


def root_project_name(root):
    """Read rootProject.name from settings.gradle.kts, falling back to the folder name."""
    for line in (root / SETTINGS).read_text(encoding="utf-8-sig").splitlines():
        stripped = line.strip()
        if stripped.startswith("rootProject.name"):
            _, _, value = stripped.partition("=")
            name = value.strip().strip('"').strip("'")
            if name:
                return name
    return root.name


def resolve(root=None, platform=None, environment=None):
    """Return the build root Gradle writes to for this repository and platform."""
    root = Path(root) if root else repository_root()
    platform = sys.platform if platform is None else platform
    environment = os.environ if environment is None else environment
    if not platform.startswith("win") or in_repo_build_requested(root):
        return root / "build"
    base = environment.get("LOCALAPPDATA") or environment.get("USERPROFILE") or environment.get("TEMP")
    if not base:
        return root / "build"
    return Path(base) / "TrustWeave" / "gradle-build" / root_project_name(root)


def require_results(build_root):
    """Fail loudly when a build root holds no JUnit results, instead of reporting phantom gaps."""
    build_root = Path(build_root)
    if not build_root.is_dir():
        raise ValueError(
            f"Build root {build_root} does not exist. Run the Gradle build first, "
            f"or pass --build-root explicitly."
        )
    if not any(build_root.glob("**/test-results/test/*.xml")):
        raise ValueError(
            f"Build root {build_root} holds no JUnit results. Evidence gates must not report on "
            f"an empty or stale directory; run the Gradle build first, or pass --build-root."
        )
    return build_root


if __name__ == "__main__":
    print(resolve())
