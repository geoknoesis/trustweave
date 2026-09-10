"""Reject fixture inventory drift and missing or contradictory reference execution evidence."""
import argparse
import json
from pathlib import Path


def check(matrix, fixtures, results):
    revision = matrix['reference_revision']
    vectors = {row['id']: row for row in matrix['vectors']}
    if len(vectors) != len(matrix['vectors']):
        raise ValueError('Duplicate matrix ID')
    cases = {}
    for fixture in fixtures:
        if fixture['reference_revision'] != revision:
            raise ValueError('Fixture reference revision differs')
        for case in fixture['cases']:
            if case['name'] in cases:
                raise ValueError('Duplicate fixture ID')
            cases[case['name']] = case
    expected = {key: row for key, row in vectors.items() if row['direction'] == 'Python to Kotlin'}
    if set(expected) != set(cases):
        raise ValueError('Matrix and fixture inventory differ')
    outcomes = {}
    for result in results:
        if result['reference_revision'] != revision:
            raise ValueError('Result reference revision differs')
        for outcome in result['outcomes']:
            if outcome['name'] in outcomes:
                raise ValueError('Duplicate executed outcome')
            outcomes[outcome['name']] = outcome
    for key, row in expected.items():
        case = cases[key]
        if (case['expected'], case.get('referenceExpected', case['expected']), case.get('difference')) != (
                row['sdk_expected'], row['reference_expected'], row.get('difference')):
            raise ValueError(f'{key}: matrix outcome differs from fixture')
        if row['sdk_expected'] != row['reference_expected'] and not row.get('difference'):
            raise ValueError(f'{key}: unexplained difference')
        outcome = outcomes.get(key)
        if not outcome or (outcome['valid'], outcome['sdk_expected'], outcome.get('difference')) != (
                row['reference_expected'], row['sdk_expected'], row.get('difference')):
            raise ValueError(f'{key}: missing or contradictory executed reference result')
    for key, row in vectors.items():
        if row['direction'] == 'Kotlin to Python':
            names = [key, key+'-wrong-audience', key+'-wrong-nonce']
            if row['profile'] != 'immediate':
                names += [key+'-wrong-payment-audience', key+'-wrong-payment-nonce']
            if row['profile'] == 'authenticated-checkout':
                names += [key+'-wrong-checkout-audience', key+'-wrong-checkout-nonce']
            for name in names:
                outcome = outcomes.get(name)
                wanted = name == key
                if not outcome or outcome['valid'] != wanted or outcome['sdk_expected'] != wanted:
                    raise ValueError(f'{name}: missing or contradictory reverse result')
    return dict(python_cases=len(cases), reverse_profiles=sum(r['direction']=='Kotlin to Python' for r in vectors.values()),
                reference_outcomes=len(outcomes), explained_differences=sum(r['sdk_expected']!=r['reference_expected'] for r in expected.values()))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--results-root', type=Path, default=Path('.gradle'))
    parser.add_argument('--report', type=Path, default=Path('build/reports/vi-matrix.json'))
    args = parser.parse_args()
    def read(path):
        return json.loads(path.read_text(encoding='utf-8-sig'))
    try:
        matrix = read(Path('config/vi-conformance-matrix.json'))
        fixtures = [read(Path('credentials/plugins/verifiable-intent/src/test/resources')/name)
                    for name in ['vi_python_cross_stack.json', 'vi_python_autonomous.json']]
        results = [read(args.results_root/f'vi-{name}-results.json') for name in
                   ['immediate', 'autonomous', 'checkout', 'python-immediate', 'python-autonomous']]
        result = check(matrix, fixtures, results)
    except (ValueError, KeyError, TypeError, OSError) as error:
        raise SystemExit(str(error))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(result))
