#!/usr/bin/env python3
"""Fail-closed validation for sanitized live AWS KMS qualification evidence."""

from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path

KEY_ARN = re.compile(r"^arn:aws[a-z-]*:kms:[a-z0-9-]+:[0-9]{12}:key/[0-9a-fA-F-]{36}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
REQUIRED_CHECKS = {
    "providerIdentity",
    "independentSignatureVerification",
    "alteredMessageRejected",
    "unauthorizedKeyRejected",
    "restartContinuity",
    "replacementKeyDistinct",
    "historicalPublicKeyResolvable",
}
FORBIDDEN_NAMES = {"secret", "token", "password", "privatekey", "accesskey"}


def validate(path: Path, expected_commit: str | None = None) -> list[str]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [f"cannot read evidence: {error}"]

    errors: list[str] = []
    if document.get("schemaVersion") != 1:
        errors.append("schemaVersion must be 1")
    if document.get("provider") != "aws-kms":
        errors.append("provider must be aws-kms")
    if expected_commit and document.get("commit") != expected_commit:
        errors.append("evidence commit does not match the candidate commit")

    resources = document.get("resources")
    if not isinstance(resources, dict):
        errors.append("resources must be an object")
        resources = {}
    arns = [resources.get(name) for name in ("primaryKeyArn", "replacementKeyArn", "deniedKeyArn")]
    if any(not isinstance(arn, str) or not KEY_ARN.fullmatch(arn) for arn in arns):
        errors.append("all resources must be immutable AWS KMS key ARNs")
    elif len(set(arns)) != 3:
        errors.append("primary, replacement and denied key ARNs must be distinct")

    fingerprints = document.get("fingerprints")
    if not isinstance(fingerprints, dict):
        errors.append("fingerprints must be an object")
        fingerprints = {}
    primary = fingerprints.get("primarySha256")
    replacement = fingerprints.get("replacementSha256")
    if not isinstance(primary, str) or not SHA256.fullmatch(primary):
        errors.append("primarySha256 must be a lowercase SHA-256 fingerprint")
    if not isinstance(replacement, str) or not SHA256.fullmatch(replacement):
        errors.append("replacementSha256 must be a lowercase SHA-256 fingerprint")
    if primary == replacement:
        errors.append("replacement fingerprint must differ from primary")

    checks = document.get("checks")
    if not isinstance(checks, dict):
        errors.append("checks must be an object")
        checks = {}
    for name in sorted(REQUIRED_CHECKS):
        if checks.get(name) is not True:
            errors.append(f"required check {name} did not pass")

    try:
        started = datetime.fromisoformat(document["startedAt"].replace("Z", "+00:00"))
        completed = datetime.fromisoformat(document["completedAt"].replace("Z", "+00:00"))
        if started.tzinfo is None or completed.tzinfo is None or completed < started:
            errors.append("qualification timestamps must be ordered and timezone-aware")
        if completed > datetime.now(timezone.utc):
            errors.append("completedAt cannot be in the future")
    except (KeyError, TypeError, ValueError):
        errors.append("startedAt and completedAt must be ISO-8601 timestamps")

    def inspect(value: object, path_parts: tuple[str, ...] = ()) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                normalized = re.sub(r"[^a-z]", "", str(key).lower())
                if any(name in normalized for name in FORBIDDEN_NAMES):
                    errors.append(f"forbidden sensitive field: {'.'.join(path_parts + (str(key),))}")
                inspect(child, path_parts + (str(key),))
        elif isinstance(value, list):
            for index, child in enumerate(value):
                inspect(child, path_parts + (str(index),))

    inspect(document)
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence", type=Path)
    parser.add_argument("--expected-commit")
    args = parser.parse_args()
    errors = validate(args.evidence, args.expected_commit)
    if errors:
        print("AWS KMS custody evidence is invalid:")
        for error in errors:
            print(f"- {error}")
        return 1
    print("AWS KMS custody evidence is valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
