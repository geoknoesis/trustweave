import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('documentation', Path(__file__).with_name('check-documentation.py'))
documentation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(documentation)


class DocumentationCheckTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')

    def errors(self):
        return documentation.inspect(self.root)['errors']

    def test_ignored_local_file_cannot_hide_a_broken_link(self):
        self.write('.gitignore', 'local.md\n')
        self.write('local.md', 'Only on this machine')
        self.write('README.md', '[design](local.md)\n')
        self.assertTrue(any('missing relative link' in error for error in self.errors()))

    def test_unknown_module_is_rejected(self):
        self.write('settings.gradle.kts', 'include("common")\n')
        self.write('README.md', '```kotlin\nimplementation(project(":missing"))\n```\n')
        self.assertTrue(any('unknown Gradle module' in error for error in self.errors()))
        self.write('README.md', '```kotlin\nimplementation(project(":common"))\n```\n')
        self.assertEqual([], self.errors())

    def test_source_drift_is_detected(self):
        self.write('Example.kt', 'fun main() = println("ok")\n')
        self.write('README.md', '<!-- example-source: Example.kt -->\n```kotlin\nfun main() = println("old")\n```\n')
        self.assertTrue(any('source-backed example drift' in error for error in self.errors()))

    def test_matching_source_passes(self):
        source = 'fun main() = println("ok")\n'
        self.write('Example.kt', source)
        self.write('README.md', '<!-- example-source: Example.kt -->\n```kotlin\n' + source + '```\n')
        self.assertEqual([], self.errors())

    def test_missing_link_is_detected_but_fenced_links_are_ignored(self):
        self.write('README.md', '~~~markdown\n[example](absent.md)\n~~~\n[real](missing.md)\n')
        self.assertEqual(1, len(self.errors()))
        self.assertIn('missing.md', self.errors()[0])

    def test_unclosed_fence_is_detected(self):
        self.write('README.md', '```kotlin\nval x = 1\n')
        self.assertTrue(any('unclosed' in error for error in self.errors()))

    def test_historical_designs_are_not_current_api_errors(self):
        self.write('docs/reviews/old.md', '[old](missing.md)\nwallet.presentation { }\n')
        self.assertEqual([], self.errors())

    def test_obsolete_api_is_detected(self):
        self.write('README.md', '```kotlin\nwallet.presentation { }\n```\n')
        self.assertTrue(any('obsolete API' in error for error in self.errors()))

    def test_unregistered_example_and_missing_readme_are_detected(self):
        self.write('distribution/examples/build.gradle.kts', '// no execution task')
        self.write('distribution/examples/scenarios/demo/src/main/kotlin/Demo.kt', 'package demo\nfun main() = Unit\n')
        self.assertEqual(2, len(self.errors()))

    def test_registered_documented_example_passes(self):
        self.write('distribution/examples/build.gradle.kts', 'mainClass.set("demo.DemoKt")')
        self.write('distribution/examples/scenarios/demo/src/main/kotlin/Demo.kt', 'package demo\nfun main() = Unit\n')
        self.write('distribution/examples/scenarios/demo/README.md', '# Demo')
        self.assertEqual([], self.errors())

    def test_source_marker_without_kotlin_fence_cannot_silently_disable_check(self):
        for content in ['<!-- example-source: Example.kt -->', '<!-- example-source: Example.kt -->\n```text\nold\n```',
                        '<!-- example-source: Example.kt -->\nUnrelated prose\n```kotlin\nold\n```']:
            with self.subTest(content=content):
                self.write('README.md', content)
                self.assertTrue(any('immediately following Kotlin fence' in e for e in self.errors()))

    def test_ignored_source_is_not_reproducible(self):
        self.write('.gitignore', 'Example.kt\n')
        self.write('Example.kt', 'fun main() = Unit')
        self.write('README.md', '<!-- example-source: Example.kt -->\n```kotlin\nfun main() = Unit\n```')
        self.assertTrue(any('shipped Kotlin' in e for e in self.errors()))

    def test_source_outside_repository_is_rejected(self):
        self.write('README.md', '<!-- example-source: ../Example.kt -->\n```kotlin\nfun main() = Unit\n```')
        self.assertTrue(any('inside the repository' in e for e in self.errors()))

    def test_source_marker_cannot_point_to_non_kotlin(self):
        self.write('example.txt', 'val ok = true')
        self.write('README.md', '<!-- example-source: example.txt -->\n```kotlin\nval ok = true\n```')
        self.assertTrue(any('shipped Kotlin' in e for e in self.errors()))

    def test_required_marker_cannot_be_removed(self):
        self.write('config/documentation-contract.json', '{"examples":[{"document":"README.md","source":"Example.kt"}]}')
        self.write('Example.kt', 'fun main() = Unit')
        self.write('README.md', '```kotlin\nfun main() = Unit\n```')
        self.assertTrue(any('required source contract missing' in e for e in self.errors()))
        self.write('README.md', '<!-- example-source: Example.kt -->\n```kotlin\nfun main() = Unit\n```')
        self.assertEqual([], self.errors())

    def test_empty_documentation_contract_is_rejected(self):
        self.write('config/documentation-contract.json', '{"examples":[]}')
        self.assertTrue(any('Invalid documentation contract' in e for e in self.errors()))

    def test_repository_gate_requires_the_contract_file(self):
        self.assertTrue(any('Missing required config/documentation-contract.json' in e
                            for e in documentation.inspect(self.root, require_contract=True)['errors']))


if __name__ == '__main__':
    unittest.main()
