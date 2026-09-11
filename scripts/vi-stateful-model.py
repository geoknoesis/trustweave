"""Deterministic cross-language ledger model; this is not a payment-provider emulator."""
import argparse
import json
import random
from pathlib import Path


def vectors():
    actions = [
        dict(op='reserve', scope='A', transaction='t1', challenge='n1', amount=40),
        dict(op='reserve', scope='A', transaction='t2', challenge='n2', amount=70),
        dict(op='RELEASED', scope='A', transaction='t1', evidence='e1'),
        dict(op='reserve', scope='A', transaction='t1', challenge='n3', amount=10),
        dict(op='reserve', scope='A', transaction='t2', challenge='n1', amount=10),
        dict(op='reserve', scope='B', transaction='t3', challenge='n1', amount=10),
        dict(op='reserve', scope='A', transaction='t2', challenge='n2', amount=60),
        dict(op='reserve', scope='A', transaction='t4', challenge='n4', amount=1),
        dict(op='RELEASED', scope='A', transaction='t1', evidence='e1'),
        dict(op='SETTLED', scope='A', transaction='t1', evidence='e2'),
        dict(op='SETTLED', scope='A', transaction='t2', evidence='e2'),
        dict(op='RELEASED', scope='A', transaction='t2', evidence='e3'),
        dict(op='reserve', scope='B', transaction='t3', challenge='n3', amount=30),
        dict(op='RELEASED', scope='missing', transaction='missing', evidence='e1'),
    ]
    randomizer = random.Random(95)
    for index in range(200):
        op = randomizer.choice(['reserve', 'reserve', 'SETTLED', 'RELEASED'])
        action = dict(op=op, scope=randomizer.choice(list('ABCDE')), transaction='t'+str(randomizer.randrange(8)))
        if op == 'reserve':
            action.update(challenge='n'+str(randomizer.randrange(20)), amount=randomizer.choice([0,1,20,40,100,101]))
        else:
            action['evidence'] = 'e'+str(randomizer.randrange(3))
        actions.append(action)
    records, challenges, spent = {}, set(), {}
    for action in actions:
        scope, tx = action['scope'], action['transaction']
        key = scope, tx
        if action['op'] == 'reserve':
            cap = 2 if scope == 'A' else 5
            action['maximumOccurrences'] = cap
            count = sum(s == scope for s, _ in records)
            accepted = (key not in records and action['challenge'] not in challenges
                        and count < cap and spent.get(scope,0)+action['amount'] <= 100)
            if accepted:
                records[key] = dict(amount=action['amount'], state='RESERVED', evidence=None)
                challenges.add(action['challenge'])
                spent[scope] = spent.get(scope,0)+action['amount']
        else:
            record = records.get(key)
            accepted = record is not None and (record['state'] == 'RESERVED' or
                       (record['state'] == action['op'] and record['evidence'] == action['evidence']))
            if accepted and record['state'] == 'RESERVED':
                if action['op'] == 'RELEASED':
                    spent[scope] -= record['amount']
                record.update(state=action['op'], evidence=action['evidence'])
        action['expected'] = accepted
        action['expectedSpent'] = dict(spent)
        action['expectedStates'] = {state:sum(r['state']==state for r in records.values()) for state in ['RESERVED','SETTLED','RELEASED']}
    return dict(model_version=1, seed=95, scope='Sequential budget/count/replay/reconciliation semantics; no external journal, concurrency or uncertain-commit oracle', actions=actions)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--file', type=Path, required=True)
    parser.add_argument('--check', action='store_true')
    args=parser.parse_args()
    expected=json.dumps(vectors(),indent=2)+'\n'
    if args.check:
        if args.file.read_text(encoding='utf-8') != expected:
            raise SystemExit('Stateful model fixture differs; review the model and regenerate explicitly')
    else:
        args.file.parent.mkdir(parents=True,exist_ok=True)
        args.file.write_text(expected,encoding='utf-8')
    print('214 stateful model transitions verified')
