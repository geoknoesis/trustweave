"""Check maintained Markdown links and source-backed examples; inventory all Kotlin blocks.

This is not a Kotlin compiler or a hosted-provider validation. CI separately runs
implemented local examples. Historical design/review material is inventoried but
excluded from current-link failures. Generated documentation is not source input.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
HISTORICAL = {'.internal', 'superpowers', 'reviews'}


def inspect(root):
    names = subprocess.check_output(
        ['git', '-C', str(root), 'ls-files', '--cached', '--others', '--exclude-standard', '-z']
    ).decode().split('\0')
    available = {(root / name).resolve() for name in names if name}
    settings = root / 'settings.gradle.kts'
    modules = set(re.findall(r'^include\("([^"]+)"\)', settings.read_text(encoding='utf-8'), re.M)) if settings.exists() else None
    paths = sorted({Path(name) for name in names if name.endswith('.md') and
                    not {'node_modules', '_site', 'build', '.next'}.intersection(Path(name).parts)})
    errors, snippets, source_examples = [], [], []
    for relative in paths:
        path = root / relative
        if not path.is_file():
            continue
        content = path.read_text(encoding='utf-8-sig')
        historical = bool(HISTORICAL.intersection(relative.parts))
        for match in re.finditer(r'^```(kotlin|kts)[^\n]*\n(.*?)^```', content, re.M | re.S):
            snippets.append({'file': relative.as_posix(), 'line': content[:match.start()].count('\n') + 1,
                             'historical': historical, 'has_main': bool(re.search(r'fun\s+main\s*\(', match[2]))})
        for match in re.finditer(r'<!-- example-source: ([^\n]+) -->\s*```kotlin\n(.*?)```', content, re.S):
            source = root / match[1]
            source_examples.append({'document': relative.as_posix(), 'source': match[1]})
            if not source.is_file() or source.read_text(encoding='utf-8').strip() != match[2].strip():
                errors.append(f'{relative}: source-backed example drift: {match[1]}')
        if historical:
            continue
        if modules is not None:
            for module in re.findall(r'project\(\s*["\'](:[^"\']+)["\']\s*\)', content):
                if module.lstrip(':') not in modules:
                    errors.append(f'{relative}: unknown Gradle module: {module}')
        fence = None
        prose_lines = []
        for line_number, line in enumerate(content.splitlines(), 1):
            match = re.match(r'^ {0,3}(`{3,}|~{3,})(.*)$', line)
            if not match:
                if fence is None:
                    prose_lines.append(line)
                continue
            marker, suffix = match.groups()
            if fence is None:
                fence = (marker[0], len(marker), line_number)
            elif marker[0] == fence[0] and len(marker) >= fence[1] and not suffix.strip():
                fence = None
        if fence:
            errors.append(f'{relative}:{fence[2]}: unclosed Markdown fence')
        if not {'migration'}.intersection(relative.parts) and relative.name not in {'api-patterns.md', 'code-example-style-guide.md'}:
            for pattern in [r'wallet\.presentation\s*\{', r'(?:trustweave|TrustWeave)\.(?:dids|credentials|wallets)\.',
                            r'IssuerIdentity\.from\(', r'All `?TrustWeave`? methods throw']:
                if re.search(pattern, content):
                    errors.append(f'{relative}: obsolete API pattern {pattern}')
        # Do not treat explanatory Markdown examples or inline code as actual links.
        prose = '\n'.join(prose_lines)
        prose = re.sub(r'`[^`\n]+`', '', prose)
        targets = re.findall(r'\[[^\]\n]*\]\(([^\s)]+)(?:\s+"[^"]*")?\)', prose)
        targets += re.findall(r'^\[[^\]\n]+\]:\s*(\S+)', prose, re.M)
        for target in targets:
            target = target.strip('<>').split('#')[0].split('?')[0]
            if not target or re.match(r'\w+:|//|/|\{', target):
                continue
            resolved = (path.parent / unquote(target)).resolve()
            shipped = resolved in available or (resolved.is_dir() and any(resolved in item.parents for item in available))
            if not resolved.exists() or not shipped:
                errors.append(f'{relative}: missing relative link: {target}')
    example_build = root / 'distribution/examples/build.gradle.kts'
    executable_examples = []
    if example_build.exists():
        registered = set(re.findall(r'mainClass.set\("([^\"]+)"\)', example_build.read_text(encoding='utf-8')))
        for name in names:
            if not name.startswith('distribution/examples/') or not name.endswith('.kt') or '/src/main/kotlin/' not in name:
                continue
            source = root / name
            if not source.is_file():
                continue
            code = source.read_text(encoding='utf-8')
            if not re.search(r'^fun main\s*\(', code, re.M):
                continue
            package = re.search(r'^package (\S+)', code, re.M)
            entry = (package[1] + '.' if package else '') + source.stem + 'Kt'
            if entry not in registered:
                errors.append(f'{name}: main entry point has no registered JavaExec task')
            readme = source.parent / 'README.md'
            if '/scenarios/' in name and '/src/main/kotlin/org/' not in name:
                readme = source.parents[3] / 'README.md'
            if not readme.is_file():
                errors.append(f'{name}: runnable example needs a README.md')
            executable_examples.append({'source': name, 'entry_point': entry})
    return {'executable_examples': executable_examples, 'markdown_files': len(paths), 'kotlin_blocks': len(snippets),
            'main_blocks': sum(item['has_main'] for item in snippets),
            'historical_blocks': sum(item['historical'] for item in snippets),
            'source_examples': source_examples, 'errors': errors, 'inventory': snippets,
            'limits': 'Relative path existence and exact source synchronization only. External URLs, anchors, partial snippets, historical designs and provider behavior are not certified by this check.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    result = inspect(ROOT)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    for error in result['errors']:
        print(error)
    print(f"Checked {result['markdown_files']} Markdown files; {result['kotlin_blocks']} Kotlin blocks inventoried; "
          f"{len(result['source_examples'])} source-backed examples; {len(result['errors'])} errors")
    raise SystemExit(bool(result['errors']))


if __name__ == '__main__':
    main()
