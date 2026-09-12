from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ("ci.yml", "release-evidence.yml")


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


if __name__ == "__main__":
    unittest.main()
