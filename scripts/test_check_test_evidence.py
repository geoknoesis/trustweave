import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('test_evidence', Path(__file__).with_name('check-test-evidence.py'))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class TestEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.file = self.root / 'module/test-results/test/TEST-demo.Example.xml'
        self.file.parent.mkdir(parents=True)
        self.policy = {'requirements': [{'id': 'REQ-1', 'module': 'module', 'suite': 'demo.Example', 'tests': ['required']}]}

    def write(self, body='<testcase name="required()"/>', tests=1, failures=0, errors=0, skipped=0):
        self.file.write_text(f'<testsuite name="demo.Example" tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}">{body}</testsuite>', encoding='utf-8')

    def test_success_requires_named_executed_test(self):
        self.write()
        result = checker.check(self.root, self.policy)
        self.assertEqual([], result['failures'])
        self.assertEqual(1, len(result['verified_tests']))

    def test_missing_suite_and_method_fail(self):
        self.assertTrue(checker.check(self.root, self.policy)['failures'])
        self.write('<testcase name="different()"/>')
        self.assertTrue(checker.check(self.root, self.policy)['failures'])

    def test_zero_tests_is_not_success(self):
        self.write('', tests=0)
        self.assertTrue(checker.check(self.root, self.policy)['failures'])

    def test_skipped_failed_and_errored_requirements_fail(self):
        for tag, counts in [('skipped', {'skipped': 1}), ('failure', {'failures': 1}), ('error', {'errors': 1})]:
            with self.subTest(tag=tag):
                self.write(f'<testcase name="required()"><{tag}/></testcase>', **counts)
                self.assertTrue(checker.check(self.root, self.policy)['failures'])

    def test_inconsistent_counts_are_rejected(self):
        self.write(tests=100)
        with self.assertRaises(ValueError):
            checker.check(self.root, self.policy)

    def test_empty_policy_and_duplicate_ids_are_rejected(self):
        with self.assertRaises(ValueError):
            checker.check(self.root, {'requirements': []})
        self.policy['requirements'] *= 2
        with self.assertRaises(ValueError):
            checker.check(self.root, self.policy)

    def test_empty_names_and_external_paths_are_rejected(self):
        requirement = self.policy['requirements'][0]
        requirement['tests'] = []
        with self.assertRaises(ValueError):
            checker.check(self.root, self.policy)
        requirement['tests'] = ['required']
        requirement['module'] = '../../outside'
        with self.assertRaises(ValueError):
            checker.check(self.root, self.policy)

    def test_duplicate_test_names_are_not_counted_twice(self):
        self.write('<testcase name="required()"/><testcase name="required()"/>', tests=2)
        self.assertTrue(checker.check(self.root, self.policy)['failures'])


if __name__ == '__main__':
    unittest.main()
