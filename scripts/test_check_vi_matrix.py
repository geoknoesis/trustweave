import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('matrix', Path(__file__).with_name('check-vi-matrix.py'))
matrix = importlib.util.module_from_spec(spec)
spec.loader.exec_module(matrix)


class MatrixTest(unittest.TestCase):
    def setUp(self):
        self.policy = dict(reference_revision='pin', vectors=[dict(id='case', direction='Python to Kotlin',
                          sdk_expected=False, reference_expected=True, difference='stricter merchant policy')])
        self.fixtures = [dict(reference_revision='pin', cases=[dict(name='case', expected=False,
                              referenceExpected=True, difference='stricter merchant policy')])]
        self.results = [dict(reference_revision='pin', outcomes=[dict(name='case', valid=True,
                             sdk_expected=False, difference='stricter merchant policy')])]

    def test_explained_difference_is_preserved(self):
        self.assertEqual(1, matrix.check(self.policy, self.fixtures, self.results)['explained_differences'])

    def test_missing_execution_or_inventory_is_rejected(self):
        with self.assertRaises(ValueError):
            matrix.check(self.policy, self.fixtures, [])
        with self.assertRaises(ValueError):
            matrix.check(self.policy, [], self.results)

    def test_changed_outcome_and_unexplained_difference_are_rejected(self):
        changed = copy.deepcopy(self.results)
        changed[0]['outcomes'][0]['valid'] = False
        with self.assertRaises(ValueError):
            matrix.check(self.policy, self.fixtures, changed)
        self.policy['vectors'][0]['difference'] = None
        self.fixtures[0]['cases'][0]['difference'] = None
        with self.assertRaisesRegex(ValueError, 'unexplained'):
            matrix.check(self.policy, self.fixtures, self.results)

    def test_reverse_challenge_evidence_is_required(self):
        self.policy['vectors'].append(dict(id='reverse', direction='Kotlin to Python', profile='immediate'))
        self.results[0]['outcomes'].append(dict(name='reverse', valid=True, sdk_expected=True))
        with self.assertRaisesRegex(ValueError, 'wrong-audience'):
            matrix.check(self.policy, self.fixtures, self.results)

    def test_revision_and_duplicate_results_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            matrix.check(self.policy, self.fixtures, self.results*2)
        self.results[0]['reference_revision'] = 'other'
        with self.assertRaisesRegex(ValueError, 'revision'):
            matrix.check(self.policy, self.fixtures, self.results)


if __name__ == '__main__':
    unittest.main()
