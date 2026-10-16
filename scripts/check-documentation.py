"""Check maintained Markdown links and source-backed examples; inventory all Kotlin blocks.

This is not a Kotlin compiler or a hosted-provider validation. CI separately runs
implemented local examples. Historical design/review material is inventoried and its relative links are checked, but its
imports and module paths are not. Generated documentation is not source input.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
HISTORICAL = {'.internal', 'superpowers', 'reviews', 'archive'}


MAIN_SOURCE = re.compile(r'(?:^|/)src/(?:main|[a-zA-Z0-9]*Main)/(?:kotlin|java)/.+\.(?:kt|java)$')
DECLARATION = re.compile(
    r'^(?:(?:public|internal|private|protected|open|abstract|sealed|final|data|enum|annotation|value|inline|'
    r'fun|const|lateinit|expect|actual|suspend|operator|infix|tailrec|external|override)\s+)*'
    r'(?:class|interface|object|typealias|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?`?(\w+)`?', re.M)
IMPORT = re.compile(r'^import\s+(org\.trustweave\.[\w.`]+?)(?:\.\*)?(?:\s+as\s+\w+)?\s*$', re.M)


def source_symbols(root, names):
    """Map package -> set of top-level declaration names, for every main-source file in the repository."""
    packages = {}
    for name in names:
        if not MAIN_SOURCE.search(name) or not (root / name).is_file():
            continue
        code = (root / name).read_text(encoding='utf-8-sig', errors='replace')
        package = re.search(r'^package\s+([\w.`]+)', code, re.M)
        if not package:
            continue
        declared = packages.setdefault(package[1].replace('`', ''), set())
        if name.endswith('.java'):
            declared.add(Path(name).stem)
        else:
            declared.update(DECLARATION.findall(re.sub(r'(?m)^[ \t]+.*$', '', code)))
    return packages


def documented_imports(code):
    """org.trustweave imports in a code block, skipping ones the text itself labels as wrong."""
    lines = code.splitlines()
    for index, line in enumerate(lines):
        previous = lines[index - 1] if index else ''
        if re.search(r'❌|[Ww]rong|[Ii]ncorrect', previous):
            continue
        found = IMPORT.match(line)
        if found:
            yield found[1]


def unresolved_import(target, packages):
    """True when no package or top-level symbol in main sources matches an org.trustweave import."""
    target = target.replace('`', '')
    if target in packages or any(package.startswith(target + '.') for package in packages):
        return False
    parts = target.split('.')
    for cut in range(len(parts) - 1, 0, -1):
        package, symbol = '.'.join(parts[:cut]), parts[cut]
        if symbol in packages.get(package, ()):
            return False
    return True


LINK = re.compile(r'\]\(<?([^\s)>]+)>?(?:\s+"[^"]*")?\)')
REFERENCE = re.compile(r'^\[[^\]\n]+\]:\s*(\S+)', re.M)


def prose_of(content):
    """Markdown outside fenced blocks and inline code, so examples are not treated as live links."""
    fence, lines = None, []
    for line in content.splitlines():
        match = re.match(r'^ {0,3}(`{3,}|~{3,})(.*)$', line)
        if match:
            marker, suffix = match.groups()
            if fence is None:
                fence = (marker[0], len(marker))
            elif marker[0] == fence[0] and len(marker) >= fence[1] and not suffix.strip():
                fence = None
        elif fence is None:
            lines.append(line)
    return re.sub(r'`[^`\n]+`', '', '\n'.join(lines))


def missing_links(path, relative, content, available):
    """Relative Markdown links (including badge and image links) that do not resolve to a shipped file."""
    problems = []
    prose = prose_of(content)
    for target in LINK.findall(prose) + REFERENCE.findall(prose):
        target = target.strip('<>').split('#')[0].split('?')[0]
        if not target or re.match(r'\w+:|//|/|\{', target):
            continue
        resolved = (path.parent / unquote(target)).resolve()
        shipped = resolved in available or (resolved.is_dir() and any(resolved in item.parents for item in available))
        if not resolved.exists() or not shipped:
            problems.append(f'{relative}: missing relative link: {target}')
    return problems


def unknown_module_paths(relative, content, modules):
    """Gradle project paths in include(...) lines, gradlew commands or inline code that settings.gradle.kts lacks.

    A path is accepted when it is an included module or a parent of one (for example kms:plugins).
    A trailing Gradle task name after a real module (did:did-core:test) is ignored. Only tokens whose
    first two segments are a known path, or whose second segment is plugins/platforms, are checked, so
    DID strings and placeholders such as did:key or did:method:identifier are not mistaken for modules.
    """
    if not modules:
        return []
    groups = {module.split(':')[0] for module in modules}
    known = set(modules) | {':'.join(module.split(':')[:cut]) for module in modules for cut in range(1, len(module.split(':')))}
    candidates = set(re.findall(r'^\s*include\(\s*"([^"]+)"', content, re.M))
    candidates |= set(re.findall(r'gradlew\s+(?:--?\S+\s+)*:?([a-z][\w-]*(?::[a-z][\w-]*)*)', content))
    candidates |= set(re.findall(r'`:?([a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)+)`', content))
    problems = []
    for token in sorted(candidates):
        parts = token.lstrip(':').split(':')
        if parts[0] not in groups or ':'.join(parts) in known:
            continue
        if ':'.join(parts[:-1]) in modules:  # <module>:<task>
            continue
        if parts[1] in {'plugins', 'platforms'} or (len(parts) >= 3 and ':'.join(parts[:2]) in known):
            problems.append(f'{relative}: module path not in settings.gradle.kts: {token}')
    return problems


def inspect(root, require_contract=False):
    names = subprocess.check_output(
        ['git', '-C', str(root), 'ls-files', '--cached', '--others', '--exclude-standard', '-z']
    ).decode().split('\0')
    available = {(root / name).resolve() for name in names if name}
    settings = root / 'settings.gradle.kts'
    modules = set(re.findall(r'^include\("([^"]+)"\)', settings.read_text(encoding='utf-8'), re.M)) if settings.exists() else None
    paths = sorted({Path(name) for name in names if name.endswith('.md') and
                    not {'node_modules', '_site', 'build', '.next'}.intersection(Path(name).parts)})
    packages = source_symbols(root, names)
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
        markers = list(re.finditer(r'<!-- example-source:([^\n]*?)-->', content))
        examples = list(re.finditer(r'<!-- example-source: ([^\n]+) -->\s*```kotlin\n(.*?)```', content, re.S))
        matched = {match.start() for match in examples}
        for marker in markers:
            if marker.start() not in matched:
                errors.append(f'{relative}: source-backed marker requires an immediately following Kotlin fence')
        for match in examples:
            source = (root / match[1]).resolve()
            source_examples.append({'document': relative.as_posix(), 'source': match[1]})
            if not source.is_relative_to(root.resolve()) or source not in available or source.suffix != '.kt':
                errors.append(f'{relative}: example source must be a shipped Kotlin file inside the repository: {match[1]}')
            elif not source.is_file() or source.read_text(encoding='utf-8-sig').strip() != match[2].strip():
                errors.append(f'{relative}: source-backed example drift: {match[1]}')
        errors.extend(missing_links(path, relative, content, available))
        if historical:
            continue
        if relative.name != 'CHANGELOG.md':  # the changelog legitimately names removed modules
            errors.extend(unknown_module_paths(relative, content, modules))
        if packages:
            for match in re.finditer(r'^```(?:kotlin|kts|java)[^\n]*\n(.*?)^```', content, re.M | re.S):
                for target in documented_imports(match[1]):
                    if unresolved_import(target, packages):
                        errors.append(f'{relative}: import does not resolve to a class, function or package in main sources: {target}')
        if modules is not None:
            for module in re.findall(r'project\(\s*["\'](:[^"\']+)["\']\s*\)', content):
                if module.lstrip(':') not in modules:
                    errors.append(f'{relative}: unknown Gradle module: {module}')
        fence = None
        for line_number, line in enumerate(content.splitlines(), 1):
            match = re.match(r'^ {0,3}(`{3,}|~{3,})(.*)$', line)
            if not match:
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
    contract_file = root / 'config/documentation-contract.json'
    if require_contract and not contract_file.is_file():
        errors.append('Missing required config/documentation-contract.json')
    if contract_file.is_file():
        try:
            contract = json.loads(contract_file.read_text(encoding='utf-8-sig'))
            required = contract['examples']
            if not isinstance(required, list) or not required:
                raise ValueError('nonempty examples required')
            pairs = [(item['document'], item['source']) for item in required]
            if len(set(pairs)) != len(pairs) or any(not all(isinstance(value, str) and value for value in pair) for pair in pairs):
                raise ValueError('unique nonempty document/source pairs required')
            for document, source in pairs:
                matches = [item for item in source_examples if item == {'document': document, 'source': source}]
                if len(matches) != 1:
                    errors.append(f'{document}: required source contract missing or duplicated: {source}')
        except (ValueError, TypeError, KeyError) as error:
            errors.append(f'Invalid documentation contract: {error}')
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
    result = inspect(ROOT, require_contract=True)
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
