import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    "guards", Path(__file__).with_name("check-cancellation-guards.py")
)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

REPOSITORY = Path(__file__).resolve().parents[1]


def source(clauses):
    return "suspend fun work() {\n    try {\n        step()\n    " + clauses + "\n}\n"


class CancellationGuardTest(unittest.TestCase):
    def repo(self, folder, text, policy=None):
        root = Path(folder)
        target = root / "mod" / "src" / "main" / "kotlin"
        target.mkdir(parents=True)
        (target / "T.kt").write_text(text, encoding="utf-8")
        (root / "config").mkdir(parents=True, exist_ok=True)
        (root / checker.POLICY).write_text(
            json.dumps(policy or {"recorded": 0, "allowed": []}), encoding="utf-8"
        )
        return root

    def test_guard_first_passes(self):
        text = source("} catch (e: CancellationException) {\n        throw e\n    } catch (e: Exception) {\n        log(e)\n    }")
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual([], checker.scan(self.repo(folder, text)))

    def test_missing_guard_is_reported(self):
        text = source("} catch (e: Exception) {\n        log(e)\n    }")
        with tempfile.TemporaryDirectory() as folder:
            found = checker.scan(self.repo(folder, text))
            self.assertEqual(1, len(found))
            self.assertIn("no CancellationException guard", found[0]["problem"])

    def test_guard_after_a_supertype_clause_is_unreachable(self):
        # CancellationException is an IllegalStateException, so this clause order never reaches it.
        for earlier in ["IllegalStateException", "RuntimeException", "Exception", "Throwable"]:
            text = source(
                f"}} catch (e: {earlier}) {{\n        log(e)\n    }} catch (e: CancellationException) {{\n"
                "        throw e\n    }"
            )
            with tempfile.TemporaryDirectory() as folder, self.subTest(earlier=earlier):
                found = checker.scan(self.repo(folder, text))
                self.assertEqual(1, len(found))
                self.assertIn("unreachable", found[0]["problem"])

    def test_unrelated_clause_before_the_guard_is_fine(self):
        text = source(
            "} catch (e: java.io.IOException) {\n        log(e)\n    } catch (e: CancellationException) {\n"
            "        throw e\n    } catch (e: Exception) {\n        log(e)\n    }"
        )
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual([], checker.scan(self.repo(folder, text)))

    def test_rethrow_inside_the_body_counts_as_a_guard(self):
        text = source(
            "} catch (e: Throwable) {\n        if (e is CancellationException) throw e\n        log(e)\n    }"
        )
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual([], checker.scan(self.repo(folder, text)))

    def test_non_suspend_functions_are_not_checked(self):
        text = "fun work() {\n    try {\n        step()\n    } catch (e: Exception) {\n        log(e)\n    }\n}\n"
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual([], checker.scan(self.repo(folder, text)))

    def test_narrow_clauses_alone_are_not_checked(self):
        text = source("} catch (e: java.io.IOException) {\n        log(e)\n    }")
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual([], checker.scan(self.repo(folder, text)))

    def test_a_recorded_site_passes_while_a_new_one_fails(self):
        text = source("} catch (e: Exception) {\n        log(e)\n    }")
        with tempfile.TemporaryDirectory() as folder:
            root = self.repo(folder, text)
            line = checker.scan(root)[0]["line"]
            (root / checker.POLICY).write_text(
                json.dumps({"recorded": 1, "allowed": [{"file": "mod/src/main/kotlin/T.kt", "line": line}]}),
                encoding="utf-8",
            )
            self.assertEqual(([], 1), checker.check(root))
            # A second unguarded catch is neither recorded nor within the recorded count.
            (root / "mod/src/main/kotlin/U.kt").write_text(text, encoding="utf-8")
            failures, total = checker.check(root)
            self.assertEqual(2, total)
            self.assertTrue(any("U.kt" in failure for failure in failures), failures)
            self.assertTrue(any("grew from 1 to 2" in failure for failure in failures), failures)

    def test_inconsistent_policy_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.repo(folder, source("} catch (e: java.io.IOException) {\n        log(e)\n    }"),
                             {"recorded": 3, "allowed": []})
            with self.assertRaises(ValueError):
                checker.check(root)

    def test_this_repository_has_no_unguarded_catches(self):
        failures, total = checker.check(REPOSITORY)
        self.assertEqual([], failures)
        self.assertEqual(0, total)


if __name__ == "__main__":
    unittest.main()
