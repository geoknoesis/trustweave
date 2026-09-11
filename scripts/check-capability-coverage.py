"""Force every new module to declare a maturity, and stop the unassessed list from growing.

`ModuleCapabilities.requireDeployment` already fails closed for a module with no catalog entry, so
an unassessed module is safe — it is just invisible. A consumer reading the catalog cannot tell
"we assessed this and it is experimental" from "nobody has looked at it yet", and the list grows
quietly every time a module is added.

This gate makes the distinction explicit. A module must either carry a catalog entry or appear in
`config/capability-ratchet.json`. Anything else fails. The recorded count may only fall, so
assessing modules is the only way to change it.
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path
from pathlib import PurePosixPath

CATALOG = "common/src/main/resources/trustweave-capabilities.json"
RATCHET = "config/capability-ratchet.json"
MATURITIES = {"stub", "experimental", "supported"}


def declared_modules(root):
    """Every module with a build script, as a Gradle path, from git rather than a directory walk."""
    listing = subprocess.check_output(
        ["git", "-C", str(root), "ls-files", "--cached", "--others", "--exclude-standard", "*build.gradle.kts"],
        text=True,
    ).splitlines()
    return {str(PurePosixPath(name).parent).replace("/", ":") for name in listing if "/" in name}


def check(root):
    root = Path(root)
    catalog = json.loads((root / CATALOG).read_text(encoding="utf-8-sig"))
    ratchet = json.loads((root / RATCHET).read_text(encoding="utf-8-sig"))
    recorded = ratchet["recorded"]
    allowed = set(ratchet["unassessed"])
    if len(allowed) != recorded:
        raise ValueError(f"{RATCHET}: recorded {recorded} does not match its own list of {len(allowed)}")

    failures = []
    for name, entry in sorted(catalog.items()):
        maturity = entry.get("maturity")
        if maturity not in MATURITIES:
            failures.append(f"{name}: maturity '{maturity}' is not one of {sorted(MATURITIES)}")
        if name in allowed:
            failures.append(f"{name}: assessed in the catalog but still listed as unassessed; remove it from {RATCHET}")

    modules = declared_modules(root)
    unassessed = sorted(modules - set(catalog))
    for name in unassessed:
        if name not in allowed:
            failures.append(
                f"{name}: declares no maturity. Add it to {CATALOG}, "
                f"or record why it is unassessed in {RATCHET}."
            )
    for name in sorted(allowed - modules - set(catalog)):
        failures.append(f"{name}: listed as unassessed but no longer exists; remove it from {RATCHET}")

    if len(unassessed) > recorded:
        failures.append(f"Unassessed modules grew from {recorded} to {len(unassessed)}")
    drift = recorded - len(unassessed)
    return failures, len(catalog), len(unassessed), drift


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    args = parser.parse_args()
    try:
        failures, assessed, unassessed, drift = check(args.root)
    except (OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"Invalid capability evidence: {error}", file=sys.stderr)
        sys.exit(1)
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        sys.exit(1)
    progress = f"; {drift} newly assessed since the ratchet was recorded — lower `recorded`" if drift > 0 else ""
    print(f"{assessed} modules assessed, {unassessed} explicitly unassessed{progress}")
