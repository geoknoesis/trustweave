import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("workflow_pinning", Path(__file__).with_name("check-workflow-pinning.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

PINNED = "actions/checkout@11d5960a326750d5838078e36cf38b85af677262"

SCOPED = f"""name: W
on: push
permissions:
  contents: read
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: {PINNED} # v4
"""


class WorkflowPinningTest(unittest.TestCase):
    def test_pinned_and_scoped_workflow_passes(self):
        self.assertEqual([], checker.check_text("w.yml", SCOPED))

    def test_mutable_tag_is_rejected(self):
        text = SCOPED.replace(PINNED, "actions/checkout@v4")
        failures = checker.check_text("w.yml", text)
        self.assertEqual(1, len(failures))
        self.assertIn("not a 40-character commit SHA", failures[0])

    def test_short_sha_and_missing_version_are_rejected(self):
        for reference in ["actions/checkout@11d5960", "actions/checkout"]:
            with self.subTest(reference=reference):
                self.assertTrue(checker.check_text("w.yml", SCOPED.replace(PINNED, reference)))

    def test_local_and_docker_actions_are_exempt(self):
        for reference in ["./.github/actions/setup", "docker://alpine:3.20"]:
            with self.subTest(reference=reference):
                self.assertEqual([], checker.check_text("w.yml", SCOPED.replace(PINNED + " # v4", reference)))

    def test_job_without_permissions_fails_when_workflow_declares_none(self):
        text = SCOPED.replace("permissions:\n  contents: read\n", "")
        failures = checker.check_text("w.yml", text)
        self.assertEqual(1, len(failures))
        self.assertIn("job 'build' has no permissions scope", failures[0])

    def test_job_level_permissions_satisfy_the_gate(self):
        text = SCOPED.replace("permissions:\n  contents: read\n", "").replace(
            "    runs-on: ubuntu-latest\n", "    runs-on: ubuntu-latest\n    permissions:\n      contents: read\n"
        )
        self.assertEqual([], checker.check_text("w.yml", text))

    def test_every_job_is_checked_not_only_the_first(self):
        text = SCOPED.replace("permissions:\n  contents: read\n", "").replace(
            "    runs-on: ubuntu-latest\n", "    runs-on: ubuntu-latest\n    permissions:\n      contents: read\n"
        ) + f"""  notify:
    runs-on: ubuntu-latest
    steps:
      - uses: {PINNED} # v4
"""
        failures = checker.check_text("w.yml", text)
        self.assertEqual(1, len(failures))
        self.assertIn("job 'notify'", failures[0])

    def test_workflow_without_jobs_is_rejected(self):
        with self.assertRaises(ValueError):
            checker.check_text("w.yml", "name: W\non: push\n")

    def test_empty_folder_cannot_silently_pass(self):
        with tempfile.TemporaryDirectory() as folder, self.assertRaises(ValueError):
            checker.check(folder)

    def test_repository_workflows_are_clean(self):
        failures, count = checker.check(Path(__file__).resolve().parents[1] / ".github/workflows")
        self.assertEqual([], failures)
        self.assertGreater(count, 0)


if __name__ == "__main__":
    unittest.main()
