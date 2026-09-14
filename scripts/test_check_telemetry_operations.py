import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("check-telemetry-operations.py")
SPEC = importlib.util.spec_from_file_location("check_telemetry_operations", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)

REPO = Path(__file__).resolve().parent.parent


class TelemetryOperationsTest(unittest.TestCase):
    def fixture(self, directory, values, emitters):
        root = Path(directory)
        enum = root / MODULE.ENUM
        enum.parent.mkdir(parents=True, exist_ok=True)
        body = "\n".join(f"    {value}," for value in values)
        enum.write_text(
            "package org.trustweave.core.telemetry\n\n"
            "public enum class Operation {\n" + body + "\n}\n",
            encoding="utf-8",
        )
        source = root / "domain/src/main/kotlin/Emitters.kt"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "\n".join(f"Telemetry.measure(Operation.{name}) {{ }}" for name in emitters),
            encoding="utf-8",
        )
        return root

    def test_the_repository_satisfies_its_own_gate(self):
        self.assertEqual([], MODULE.check(REPO))

    def test_a_declared_operation_with_no_emitter_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, ["DID_RESOLVE", "WALLET_STORE"], ["DID_RESOLVE"])
            failures = MODULE.check(root)
            self.assertTrue(any("WALLET_STORE" in failure for failure in failures), failures)
            self.assertTrue(any("zero series" in failure for failure in failures), failures)

    def test_an_emitter_for_an_undeclared_operation_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, ["DID_RESOLVE"], ["DID_RESOLVE", "DID_INVENTED"])
            failures = MODULE.check(root)
            self.assertTrue(any("DID_INVENTED" in failure for failure in failures), failures)

    def test_a_test_source_emitter_does_not_satisfy_the_gate(self):
        """A host does not run the test suite, so a test-only emitter is still a zero series."""
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, ["DID_RESOLVE", "WALLET_STORE"], ["DID_RESOLVE"])
            test_source = root / "domain/src/test/kotlin/EmitterTest.kt"
            test_source.parent.mkdir(parents=True, exist_ok=True)
            test_source.write_text("Telemetry.measure(Operation.WALLET_STORE) { }", encoding="utf-8")
            failures = MODULE.check(root)
            self.assertTrue(any("WALLET_STORE" in failure for failure in failures), failures)

    def test_a_multiline_emitter_call_is_recognized(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, ["DID_RESOLVE"], [])
            source = root / "domain/src/main/kotlin/Emitters.kt"
            source.write_text(
                "Telemetry.rejected(\n    Operation.DID_RESOLVE,\n    \"NotFound\",\n)",
                encoding="utf-8",
            )
            self.assertEqual([], MODULE.check(root))


if __name__ == "__main__":
    unittest.main()
