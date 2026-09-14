import importlib.util
import shutil
import tempfile
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ("ci.yml", "release-evidence.yml")

MODULE_PATH = Path(__file__).with_name("check-workflow-evidence-paths.py")
SPEC = importlib.util.spec_from_file_location("check_workflow_evidence_paths", MODULE_PATH)
GATE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(GATE)


class WorkflowEvidencePathTest(unittest.TestCase):
    def test_workflows_consume_module_build_outputs(self):
        required = (
            "build/credentials/plugins/verifiable-intent/qualification/vi-kotlin-immediate.json",
            "build/credentials/plugins/verifiable-intent/qualification/vi-kotlin-autonomous.json",
            "build/credentials/plugins/verifiable-intent/qualification/vi-kotlin-checkout.json",
            "build/credentials/plugins/verifiable-intent/qualification/intent-metrics.prom",
            "build/credentials/plugins/status-list/server/qualification/status-list-diagnostics.log",
            "build/observability/qualification/host-metrics.prom",
        )
        forbidden = (
            "credentials/plugins/verifiable-intent/build/reports/",
            "credentials/plugins/verifiable-intent/build/qualification/",
            "credentials/plugins/status-list/server/build/reports/",
            "build/credentials/plugins/status-list/server/reports/status-list-diagnostics.log",
            "build/observability/reports/host-metrics.prom",
        )
        for workflow_name in WORKFLOWS:
            workflow = (ROOT / ".github" / "workflows" / workflow_name).read_text(encoding="utf-8")
            self.assertIn(
                ":credentials:plugins:verifiable-intent:test --rerun-tasks --no-build-cache",
                workflow,
                f"{workflow_name} must regenerate evidence independently of historical caches",
            )
            self.assertIn(
                ":observability:test :observability:koverXmlReport --rerun-tasks --no-build-cache",
                workflow,
                f"{workflow_name} must regenerate host coverage and evidence independently of historical caches",
            )
            for path in required:
                self.assertIn(path, workflow, f"{workflow_name} must retain {path}")
            for path in forbidden:
                self.assertNotIn(path, workflow, f"{workflow_name} uses obsolete root-relative {path}")


class WorkflowEvidenceOutputTest(unittest.TestCase):
    """The named-path list above catches the paths we thought of; this catches the rest.

    A workflow reading a path no module declares as a task output is the defect that has broken CI
    three times. Rather than adding paths to a list as we discover them, assert the property: every
    module-scoped evidence path a workflow reads is backed by a declared Gradle output.
    """

    NEWLINE = chr(10)

    def fixture(self, workflow_text, build_script):
        directory = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, directory, True)
        root = Path(directory)
        workflows = root / ".github" / "workflows"
        workflows.mkdir(parents=True)
        for name in GATE.WORKFLOWS:
            (workflows / name).write_text(workflow_text + self.NEWLINE, encoding="utf-8")
        module = root / "domain" / "widget"
        module.mkdir(parents=True)
        (module / "build.gradle.kts").write_text(build_script + self.NEWLINE, encoding="utf-8")
        return root

    DECLARED = 'tasks.test { outputs.dir(layout.buildDirectory.dir("qualification")) }'
    UNDECLARED = 'tasks.test { systemProperty("out", "qualification") }'

    def test_the_repository_satisfies_its_own_gate(self):
        self.assertEqual([], GATE.check(ROOT))

    def test_a_path_from_a_module_that_declares_no_output_fails(self):
        root = self.fixture("path: build/domain/widget/qualification/evidence.json", self.UNDECLARED)
        failures = GATE.check(root)
        self.assertTrue(any("declares no task output" in failure for failure in failures), failures)

    def test_a_path_the_module_never_writes_fails(self):
        """The build layout moved and the workflow did not."""
        root = self.fixture("path: build/domain/widget/receipts/evidence.json", self.DECLARED)
        failures = GATE.check(root)
        self.assertTrue(any("never mentions 'receipts'" in failure for failure in failures), failures)

    def test_a_declared_output_passes(self):
        root = self.fixture("path: build/domain/widget/qualification/evidence.json", self.DECLARED)
        self.assertEqual([], GATE.check(root))

    def test_root_level_reports_are_exempt(self):
        """Nothing module-scoped writes build/reports; scripts and Gradle's own reporting do."""
        root = self.fixture("path: build/reports/documentation.json", "tasks.test { }")
        self.assertEqual([], GATE.check(root))


if __name__ == "__main__":
    unittest.main()


class ScriptTestDiscoveryTest(unittest.TestCase):
    """Every scripts/test_*.py must actually run in CI.

    This file is the test written to stop the evidence-path defect, and for its first three
    commits no workflow ran it: the discovery pattern was `test_check_*.py`, and this file is not
    named that way. A test nobody runs is indistinguishable from one that passes.
    """

    def test_the_ci_discovery_pattern_covers_every_script_test(self):
        pattern = "python -m unittest discover -s scripts -p 'test_*.py'"
        workflow = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        self.assertIn(
            pattern,
            workflow,
            "ci.yml must discover every scripts/test_*.py, not a subset chosen by filename",
        )
