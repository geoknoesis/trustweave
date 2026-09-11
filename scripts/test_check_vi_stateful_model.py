import importlib.util
from pathlib import Path
import unittest
spec=importlib.util.spec_from_file_location('stateful',Path(__file__).with_name('vi-stateful-model.py'))
model=importlib.util.module_from_spec(spec)
spec.loader.exec_module(model)

class StatefulModelTest(unittest.TestCase):
    def test_budget_replay_release_and_terminal_decisions(self):
        actions=model.vectors()['actions']
        self.assertEqual([True,False,True,False,False,False,True,False,True,False,True,False,True,False],
                         [a['expected'] for a in actions[:14]])
        self.assertEqual({'A':60,'B':30},actions[13]['expectedSpent'])
        self.assertEqual({'RESERVED':1,'SETTLED':1,'RELEASED':1},actions[13]['expectedStates'])

    def test_seed_is_reproducible_and_spending_never_negative_or_over_budget(self):
        first=model.vectors()
        self.assertEqual(first,model.vectors())
        for action in first['actions']:
            self.assertTrue(all(0<=spent<=100 for spent in action['expectedSpent'].values()))
            if action['op']=='reserve' and action['amount']>100:
                self.assertFalse(action['expected'])

if __name__=='__main__':
    unittest.main()
