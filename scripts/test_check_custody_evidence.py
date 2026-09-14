import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

import importlib.util

MODULE_PATH = Path(__file__).with_name("check-custody-evidence.py")
SPEC = importlib.util.spec_from_file_location("check_custody_evidence", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class CustodyEvidenceTest(unittest.TestCase):
    def evidence(self):
        now = datetime.now(timezone.utc)
        return {
            "schemaVersion": 1,
            "provider": "aws-kms",
            "commit": "abc123",
            "workflowRunId": "123",
            "region": "us-east-1",
            "startedAt": (now - timedelta(seconds=5)).isoformat(),
            "completedAt": now.isoformat(),
            "resources": {
                "primaryKeyArn": "arn:aws:kms:us-east-1:123456789012:key/11111111-1111-1111-1111-111111111111",
                "replacementKeyArn": "arn:aws:kms:us-east-1:123456789012:key/22222222-2222-2222-2222-222222222222",
                "deniedKeyArn": "arn:aws:kms:us-east-1:123456789012:key/33333333-3333-3333-3333-333333333333",
            },
            "fingerprints": {"primarySha256": "a" * 64, "replacementSha256": "b" * 64},
            "checks": {name: True for name in MODULE.REQUIRED_CHECKS},
            "denialResultType": "Error",
            "denialErrorCode": "AccessDeniedException",
        }

    def validate(self, document, expected="abc123"):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "evidence.json"
            path.write_text(json.dumps(document), encoding="utf-8")
            return MODULE.validate(path, expected)

    def test_valid_evidence_passes(self):
        self.assertEqual([], self.validate(self.evidence()))

    def test_a_denial_the_run_could_not_distinguish_from_absence_is_rejected(self):
        """KeyNotFound is what a mistyped ARN produces; it is not evidence that IAM refused."""
        document = self.evidence()
        document["denialResultType"] = "KeyNotFound"
        errors = self.validate(document)
        self.assertTrue(any("denialResultType" in error for error in errors), errors)

    def test_a_transport_fault_is_not_accepted_as_a_denial(self):
        document = self.evidence()
        document["denialErrorCode"] = "none"
        errors = self.validate(document)
        self.assertTrue(any("denialErrorCode" in error for error in errors), errors)

    def test_a_wrong_error_code_is_not_accepted_as_a_denial(self):
        document = self.evidence()
        document["denialErrorCode"] = "ThrottlingException"
        errors = self.validate(document)
        self.assertTrue(any("IAM enforcement" in error for error in errors), errors)

    def test_evidence_carrying_an_invented_check_is_rejected(self):
        """A run that renames a check must not slip a passing field past the required set."""
        document = self.evidence()
        document["checks"]["somethingElseEntirely"] = True
        errors = self.validate(document)
        self.assertTrue(any("unrecognized checks" in error for error in errors), errors)

    def test_mismatched_commit_and_failed_gate_fail(self):
        document = self.evidence()
        document["checks"]["unauthorizedKeyRejected"] = False
        errors = self.validate(document, "different")
        self.assertTrue(any("candidate commit" in error for error in errors))
        self.assertTrue(any("unauthorizedKeyRejected" in error for error in errors))

    def test_alias_duplicate_fingerprint_and_sensitive_field_fail(self):
        document = self.evidence()
        document["resources"]["primaryKeyArn"] = "alias/primary"
        document["fingerprints"]["replacementSha256"] = "a" * 64
        document["sessionToken"] = "must-not-appear"
        errors = self.validate(document)
        self.assertTrue(any("immutable" in error for error in errors))
        self.assertTrue(any("differ" in error for error in errors))
        self.assertTrue(any("sensitive" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
