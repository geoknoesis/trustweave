"""Render the sixth review from its recorded evidence; no external assets required."""
import html
import json
import os
from pathlib import Path

root = Path(__file__).resolve().parent
sdk = root.parents[3]
esc = html.escape
findings = json.loads((root / 'findings.json').read_text())
scores = json.loads((root / 'scores.json').read_text())
validation = json.loads((root / 'validation.json').read_text())


def link(repo, name):
    base = sdk if repo == 'trustweave' else sdk.parent / 'trustweave-saas'
    target = os.path.relpath(base / name, root).replace('\\', '/')
    return f'<a href="{esc(target)}">{esc(name)}</a>'


cards = []
for item in findings:
    references = ''.join(f'<li>{link(item["repo"], name)}</li>' for name in item['files'])
    cards.append(f'''<article data-repo="{item['repo']}" data-status="{item['status']}" id="{item['id']}">
    <div class="meta">{item['repo']} · {item['id']} · {item['status']}</div><h3>{esc(item['title'])}</h3>
    <p>{esc(item['detail'])}</p><p><b>Evidence:</b> {esc(item['evidence'])}</p>
    <p class="muted"><b>Limit:</b> {esc(item['limit'])}</p><details><summary>Source and tests</summary><ul>{references}</ul></details></article>''')

score_cards = ''.join(f'<section class="panel"><h2>{repo}</h2><div class="score">{scores["totals"][repo]}<small>/100</small></div><p>Previous review: {scores["previous"][repo]}/100</p></section>' for repo in ['trustweave', 'trustweave-saas'])
score_rows = ''.join(f'<tr><th>{name.title()}</th><td>{maximum}</td><td>{scores["trustweave"][i]}</td><td>{scores["trustweave-saas"][i]}</td></tr>' for i, (name, maximum) in enumerate(scores['rubric'].items()))
deductions = ''.join('<h3>'+esc(repo)+'</h3><ul>'+''.join('<li>'+esc(reason)+'</li>' for reason in reasons)+'</ul>' for repo, reasons in scores['deductions'].items())
evidence = ''.join(f'<tr><th>{esc(row["area"])}</th><td>{esc(row["result"])}</td><td>{esc(row["scope"])}</td></tr>' for row in validation['checks'])
logs = ''.join(f'<li><a href="{esc(name)}">{esc(name)}</a></li>' for name in validation['logs'])
runbook = link('trustweave-saas', 'docs/round-6-operations.md')

document = f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>TrustWeave · Round 6 engineering review</title><style>
*{{box-sizing:border-box}}body{{margin:0;background:#f3f6f8;color:#173044;font:16px/1.65 system-ui,sans-serif}}header{{background:#153348;color:white;padding:40px max(24px,calc((100vw - 1060px)/2))}}header p{{color:#d7e5ee;max-width:850px}}h1{{font-size:clamp(28px,5vw,42px);line-height:1.2}}h2{{font-size:24px}}h3{{font-size:19px}}a{{color:#076485;overflow-wrap:anywhere}}header a{{color:#c7eff5}}main{{max-width:1110px;padding:25px;margin:auto}}.grid{{display:grid;grid-template-columns:1fr 1fr;gap:20px}}.panel,article{{padding:24px;border:1px solid #d7e2e9;border-radius:12px;background:white;margin:0 0 20px}}.score{{font-size:54px;color:#126358;font-weight:750;line-height:1.2}}small{{font-size:19px;font-weight:400}}.notice{{background:#fff8e9;border-left:5px solid #aa7417;padding:20px;margin:5px 0 25px}}.meta,.muted{{color:#516879}}.meta{{font-size:12px;text-transform:uppercase;letter-spacing:.05em}}table{{width:100%;border-collapse:collapse}}th,td{{text-align:left;vertical-align:top;padding:10px 8px;border-bottom:1px solid #d7e2e9}}.table-wrap{{overflow:auto}}li{{margin:6px 0}}details{{border-top:1px solid #d7e2e9;padding-top:12px}}summary{{font-weight:600;cursor:pointer}}nav.filters{{display:flex;gap:10px;flex-wrap:wrap;margin:20px 0}}button{{padding:9px 15px;border:1px solid #9aafbf;border-radius:6px;background:white;color:#173044;font:inherit;cursor:pointer}}button[aria-pressed=true]{{background:#173044;color:white}}a:focus-visible,button:focus-visible,summary:focus-visible{{outline:3px solid #d79323;outline-offset:3px}}[hidden]{{display:none!important}}footer{{color:#516879;font-size:14px;padding:20px 0}}@media(max-width:650px){{.grid{{grid-template-columns:1fr}}main{{padding:18px 14px}}.panel,article{{padding:18px}}th,td{{padding:8px 5px;font-size:14px}}}}@media print{{button{{display:none}}article{{break-inside:avoid}}}}
</style></head><body><header><div>5 SEPTEMBER 2026 · ROUND 6</div><h1>Engineering review and hardening</h1><p>Verified credential restoration and a protected operator recovery inventory. Both repositories receive partial UX credit; release, provider and custody limits remain explicit.</p><a href="../round-5/index.html">Previous review</a> · <a href="findings.json">Findings</a> · <a href="scores.json">Scores</a> · <a href="validation.json">Validation data</a></header><main>
<div class="grid">{score_cards}</div><aside class="notice"><b>A perfect score is not yet supported.</b> The SDK pin and local regressions are verified. Publication, exact-pair Linux CI, designated staging validation, live billing/provider evidence and production custody decisions remain outstanding. No deployment or publication was performed.</aside>
<section class="panel"><h2>Assessment scope</h2><p>{esc(scores['assessment'])}</p><p>Scores are engineering judgments against the same 30/25/20/15/10 rubric. They are not percentages of bug-free code or a security certification. Historical scores are preserved.</p><div class="table-wrap"><table><thead><tr><th>Category</th><th>Maximum</th><th>TrustWeave</th><th>SaaS</th></tr></thead><tbody>{score_rows}</tbody></table></div><h2>Remaining deductions</h2>{deductions}</section>
<section class="panel"><h2>Validation evidence</h2><div class="table-wrap"><table><thead><tr><th>Area</th><th>Result</th><th>Scope and limits</th></tr></thead><tbody>{evidence}</tbody></table></div><p>Results distinguish local database/HTTP fixtures from hosted-service validation. Optional tests that did not run remain reported as skips. Timing excludes fixture setup.</p><details><summary>Build and test logs</summary><ul>{logs}</ul></details></section>
<h2>Implemented improvements and remaining gaps</h2><nav class="filters" aria-label="Filter findings"><button data-filter="all" aria-pressed="true">All</button><button data-filter="trustweave" aria-pressed="false">TrustWeave</button><button data-filter="trustweave-saas" aria-pressed="false">SaaS</button><button data-filter="remaining" aria-pressed="false">Remaining</button></nav>{''.join(cards)}
<section class="panel"><h2>Operational handoff</h2><p>{runbook} covers backup limits, key-loss boundaries, admin authorization, pagination and provider redelivery. Staging environment and release-branch details were requested during this pass and remain prerequisites for external release checks.</p><p>Implementation references: <a href="https://www.postgresql.org/docs/16/explicit-locking.html#ADVISORY-LOCKS">PostgreSQL advisory locks</a>, <a href="https://nextjs.org/docs/app/guides/content-security-policy">Next.js CSP</a>, <a href="https://learn.microsoft.com/en-us/azure/storage/blobs/storage-blobs-list-java">Azure listing semantics</a>.</p></section><footer>Round 4 is committed; this follow-up remains local and uncommitted. Report generated from the accompanying evidence files.</footer></main><script>document.querySelectorAll('[data-filter]').forEach(button=>button.addEventListener('click',()=>{{document.querySelectorAll('[data-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));document.querySelectorAll('article').forEach(card=>{{const filter=button.dataset.filter;card.hidden=!(filter==='all'||filter===card.dataset.repo||(filter==='remaining'&&card.dataset.status==='remaining'));}});}}));</script></body></html>'''
(root / 'index.html').write_text(document, encoding='utf-8')
print('Rendered', root / 'index.html')
