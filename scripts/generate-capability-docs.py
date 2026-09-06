"""Generate the assessed capability table from the runtime catalog; --check is a CI drift gate."""
import json
import pathlib
import sys
import subprocess

root = pathlib.Path(__file__).resolve().parents[1]
catalog = json.loads((root / 'common/src/main/resources/trustweave-capabilities.json').read_text())
text = '# Assessed module capabilities\n\nGenerated from `common/src/main/resources/trustweave-capabilities.json`. Unlisted modules are unassessed, not implicitly supported. Use `ModuleCapabilities.requireDeployment` before constructing production clients. It checks maturity, operations and formats; `requireOperations` alone checks functionality only. No catalog entry currently meets the supported-only production policy. See [deployment profiles](provider-deployment-profiles.md).\n\n| Module | Maturity | Operations | Formats |\n|---|---|---|---|\n'
for name, value in sorted(catalog.items()):
    text += f'| `{name}` | {value["maturity"]} | {", ".join(value["operations"]) or "None"} | {", ".join(value["formats"]) or "None"} |\n'
modules = subprocess.check_output(
    ['git', '-C', str(root), 'ls-files', '--cached', '--others', '--exclude-standard', '*build.gradle.kts'], text=True
).splitlines()
unassessed = sorted({str(pathlib.PurePosixPath(name).parent).replace('/', ':') for name in modules
                     if '/' in name} - set(catalog))
text += '\n## Unassessed modules\n\nNo capability guarantee is made for these modules. Application requirements fail closed until an assessment is added.\n\n'
text += '\n'.join(f'- `{module}`' for module in unassessed) + '\n'
target = root / 'docs/api-reference/assessed-capabilities.md'
if '--check' in sys.argv:
    if not target.exists() or target.read_text(encoding='utf8') != text:
        raise SystemExit('Capability documentation is stale; run scripts/generate-capability-docs.py')
else:
    target.write_text(text, encoding='utf8')
