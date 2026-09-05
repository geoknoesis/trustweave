"""Render the round-three assessment from its recorded findings and validation."""
import html
import json
import os
from pathlib import Path

root = Path(__file__).resolve().parent
sdk = root.parents[3]
findings = json.loads((root / 'findings.json').read_text())
scores = json.loads((root / 'scores.json').read_text())
validation = json.loads((root / 'validation.json').read_text())
esc = html.escape


def source_link(repo, name):
    base = sdk if repo == 'trustweave' else sdk.parent / 'trustweave-saas'
    target = os.path.relpath(base / name, root).replace('\\', '/')
    return f'<a href="{esc(target)}">{esc(name)}</a>'


cards = []
for f in findings:
    links = ''.join(f'<li>{source_link(f["repo"], name)}</li>' for name in f['files'])
    cards.append(f'''<article id="{f['id']}" data-repo="{f['repo']}" data-status="{f['status']}">
    <div class="eyebrow">{f['repo']} · {f['id']} · {f['severity']} · <strong>{f['status']}</strong></div>
    <h3>{esc(f['title'])}</h3><p>{esc(f['problem'])}</p>
    <p><b>{'Implemented' if f['status'] == 'fixed' else 'Next step'}:</b> {esc(f['change'])}</p>
    <p><b>Evidence:</b> {esc(f['validation'])}</p><p class="limit"><b>Boundary:</b> {esc(f['limit'])}</p>
    <details><summary>Source and test references</summary><ul>{links}</ul></details></article>''')

rows = ''.join(f'<tr><th scope="row">{key.title()}</th><td>{weight}</td><td>{scores["trustweave"][i]}</td><td>{scores["trustweave-saas"][i]}</td></tr>' for i, (key, weight) in enumerate(scores['rubric'].items()))
deductions = ''.join(f'<section><h3>{repo}</h3><ul>'+''.join(f'<li>{esc(item)}</li>' for item in items)+'</ul></section>' for repo, items in scores['deductions'].items())
logs = ''.join(f'<li><a href="{link}">{Path(link).name}</a></li>' for link in validation['logs'])
document = f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TrustWeave · Round 3 review and remediation</title><style>
:root{{color-scheme:light;--ink:#15283b;--muted:#4c6272;--line:#d8e2e8;--accent:#12665b}}
*{{box-sizing:border-box}}body{{margin:0;background:#f1f5f7;color:var(--ink);font:16px/1.65 system-ui,sans-serif}}
header{{background:#112c42;color:white;padding:42px max(24px,calc((100vw - 1080px)/2))}}header p{{max-width:800px;color:#d7e6ef}}
h1{{font-size:clamp(28px,5vw,44px);line-height:1.15;margin:12px 0}}h2{{font-size:25px}}h3{{font-size:19px;line-height:1.4}}main{{max-width:1130px;margin:auto;padding:28px 24px}}
a{{color:#075e83;overflow-wrap:anywhere}}header a{{color:#b7e9ee}}.eyebrow{{font-size:12px;letter-spacing:.07em;text-transform:uppercase;color:var(--muted)}}header .eyebrow{{color:#a6d4df}}
.scores,.grid{{display:grid;grid-template-columns:1fr 1fr;gap:20px}}.score,article,.panel{{background:white;border:1px solid var(--line);border-radius:12px;padding:24px;margin-bottom:20px}}
.score strong{{font-size:54px;line-height:1.2;color:var(--accent)}}.score p{{margin:8px 0}}.score span{{font-size:18px;color:var(--muted)}}
.notice{{border-left:5px solid #b57a18;background:#fff8e8;padding:18px 22px;margin:12px 0 25px}}table{{width:100%;border-collapse:collapse}}th,td{{text-align:left;padding:10px 8px;border-bottom:1px solid var(--line)}}tfoot{{font-weight:750}}
.limit{{color:var(--muted)}}details{{border-top:1px solid var(--line);padding-top:12px}}summary{{cursor:pointer;font-weight:600}}li{{margin:5px 0}}.filters{{display:flex;flex-wrap:wrap;gap:10px;margin:20px 0}}button{{border:1px solid #9db5c4;border-radius:6px;background:white;padding:10px 16px;cursor:pointer;color:var(--ink);font:inherit}}button[aria-pressed=true]{{background:var(--ink);color:white}}button:focus-visible,a:focus-visible,summary:focus-visible{{outline:3px solid #d68b16;outline-offset:3px}}[hidden]{{display:none!important}}footer{{color:var(--muted);padding:20px 0}}.table-wrap{{overflow-x:auto}}@media(max-width:650px){{.scores,.grid{{grid-template-columns:1fr}}main{{padding:20px 14px}}.panel,article,.score{{padding:18px}}th,td{{padding:8px 5px;font-size:14px}}}}@media print{{button{{display:none}}article{{break-inside:avoid}}body{{background:white}}header{{padding:24px}}}}
</style></head><body><header><div class="eyebrow">Engineering review · 5 September 2026 · Round 3</div><h1>Stronger boundaries.<br>Measured progress.</h1>
<p>Seven additional findings fixed across TrustWeave and TrustWeave SaaS. Updated scores reflect tested local improvements and retain explicit deductions for release, operational and coverage gaps.</p>
<a href="../round-2/remediation.html">Previous remediation</a> · <a href="scores.json">Score data</a> · <a href="validation.json">Validation evidence</a> · <a href="findings.json">Finding data</a></header>
<main><div class="scores"><section class="score"><h2>TrustWeave</h2><strong>95<span>/100</span></strong><p>+1 from provisional 94 · original round 2: 79</p><p>Stricter disclosure semantics, signed holder binding, honest selective issuance and browser protections.</p></section>
<section class="score"><h2>TrustWeave SaaS</h2><strong>93<span>/100</span></strong><p>+1 from provisional 92 · original round 2: 75</p><p>Non-cooperative workers retain capacity reservations; source parity covers dependency and runtime inputs.</p></section></div>
<aside class="notice"><b>Release is still pending.</b> SA13 remains open. Publish the reviewed SDK commit, update the immutable SaaS pin, then validate the exact pair in Linux CI and staging. No release, remote CI execution or staging deployment is claimed.</aside>
<section class="panel"><h2>Scoring and scope</h2><p>This is a targeted follow-up review of the remaining gaps and adjacent code, not an exhaustive audit of every module. Scores are weighted engineering judgments, not percentages of bug-free code or security certification. The earlier 94/92 figures were provisional estimates; the new scores include additional source inspection and regression evidence. Historical reports remain intact.</p>
<div class="table-wrap"><table><thead><tr><th>Category</th><th>Maximum</th><th>TrustWeave</th><th>SaaS</th></tr></thead><tbody>{rows}</tbody><tfoot><tr><th>Total</th><td>100</td><td>95</td><td>93</td></tr></tfoot></table></div>
<h3>Why points remain deducted</h3><div class="grid">{deductions}</div></section>
<section class="panel"><h2>Validation performed</h2><ul>
<li><b>Backend:</b> 517 tests, zero failures/errors, two skips; full coverage gate passed. Includes the non-cooperative provider regression.</li>
<li><b>Frontend:</b> 292 tests across 62 files passed; lint {validation['frontend']['lint']}.</li>
<li><b>Reference wallet:</b> 28 unit tests, seven real Chromium tests and production build passed. Browser tests cover all four demo issuer profiles, selective withholding, headers, custody, concurrent tabs and recovery rendering.</li>
<li><b>Dependency audits:</b> zero reported npm vulnerabilities in each web project at validation time. This is not a JVM dependency audit.</li>
<li><b>Source parity:</b> fingerprint regression passed; local source fingerprint matches the strengthened inventory. The old published pin has not been corrected.</li>
<li><b>Prior evidence:</b> 515 SDK tests passed in round 2. A complete SDK test suite was not rerun in this pass; the SaaS composite build recompiled its SDK dependencies.</li></ul>
<p>The first browser run exposed overlapping visible/selective issuer claims. Those issuer routes were corrected and the final seven-test run passed. Frontend tests emit jsdom network-error diagnostics while still passing; the logs retain those diagnostics.</p>
<details><summary>Validation logs</summary><ul>{logs}</ul></details></section>
<h2>Findings and remaining work</h2><p>Seven fixed findings; one release blocker and three explicit evidence/design gaps. Priorities express impact, not proof of exploitability in a deployed environment.</p>
<nav class="filters" aria-label="Filter findings"><button aria-pressed="true" data-filter="all">All findings</button><button aria-pressed="false" data-filter="trustweave">TrustWeave</button><button aria-pressed="false" data-filter="trustweave-saas">SaaS</button><button aria-pressed="false" data-filter="remaining">Remaining</button></nav>
<div id="findings">{''.join(cards)}</div><section class="panel"><h2>Path to the next assessment</h2><ol><li>Publish and pin the reviewed SDK; obtain exact-pair Linux CI and staging evidence.</li><li>Exercise distributed admission, failed webhook recovery and billing workflow boundaries under real deployment conditions.</li><li>Expand provider contract coverage and benchmark storage with realistic data sizes and failure injection.</li><li>Choose and validate production wallet custody, recovery and supported interoperability profiles.</li></ol><p>These are the conditions for reconsidering remaining deductions. Completing a checklist alone will not establish a perfect score.</p></section>
<footer>Reviewed local working trees; changes remain uncommitted. <a href="../../../../../../trustweave-saas/docs/round-2-operations.md">Operational guidance</a>. Evidence recorded {esc(validation['timestamp'])}.</footer></main>
<script>document.querySelectorAll('[data-filter]').forEach(button=>button.addEventListener('click',()=>{{document.querySelectorAll('[data-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));const filter=button.dataset.filter;document.querySelectorAll('article').forEach(card=>{{card.hidden=!(filter==='all'||filter===card.dataset.repo||(filter==='remaining'&&card.dataset.status!=='fixed'));}});}}));</script></body></html>'''
# Compute the sibling runbook link rather than depending on a fixed parent depth.
document = document.replace('../../../../../../trustweave-saas/docs/round-2-operations.md', os.path.relpath(sdk.parent / 'trustweave-saas/docs/round-2-operations.md', root).replace('\\', '/'))
(root / 'index.html').write_text(document, encoding='utf-8')
print('Rendered', root / 'index.html')
