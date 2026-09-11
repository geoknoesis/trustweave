"""Reject JVM JUnit methods that discovery silently ignores. Scan fresh compiled test classes.

Supports direct Jupiter/JUnit4 test annotations. Does not certify custom composed
annotations, dynamic-factory contents, discovery counts or successful execution.
"""
import argparse
import json
import re
from pathlib import Path
import sys
import importlib.util as _importlib_util
from pathlib import Path as _Path

_spec = _importlib_util.spec_from_file_location("build_root", _Path(__file__).with_name("build_root.py"))
_build_root = _importlib_util.module_from_spec(_spec)
_spec.loader.exec_module(_build_root)

TESTS = {
    'Lorg/junit/jupiter/api/Test;', 'Lorg/junit/jupiter/api/RepeatedTest;',
    'Lorg/junit/jupiter/api/TestTemplate;', 'Lorg/junit/jupiter/params/ParameterizedTest;',
    'Lorg/junit/Test;',
}


class Reader:
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, size):
        end = self.offset + size
        if end > len(self.data):
            raise ValueError('Truncated JVM class')
        value = self.data[self.offset:end]
        self.offset = end
        return value

    def number(self, size):
        return int.from_bytes(self.take(size), 'big')


def methods(data):
    r = Reader(data)
    if r.take(4) != b'\xca\xfe\xba\xbe':
        raise ValueError('Invalid JVM class magic')
    r.take(4)
    pool = [None] * r.number(2)
    index = 1
    widths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
              12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < len(pool):
        tag = r.number(1)
        if tag == 1:
            # JVM modified UTF-8: descriptors/annotation names are ASCII. Preserve
            # Kotlin method names without allowing non-ASCII constants to break scanning.
            pool[index] = r.take(r.number(2)).decode('utf-8', errors='replace')
        elif tag in widths:
            r.take(widths[tag])
        else:
            raise ValueError(f'Unknown constant-pool tag {tag}')
        index += 2 if tag in (5, 6) else 1
    r.take(6)
    r.take(2 * r.number(2))

    def attributes():
        return [(pool[r.number(2)], r.take(r.number(4))) for _ in range(r.number(2))]

    for _ in range(r.number(2)):
        r.take(6)
        attributes()

    def annotation(a):
        name = pool[a.number(2)]
        for _ in range(a.number(2)):
            a.take(2)
            element(a)
        return name

    def element(a):
        tag = chr(a.number(1))
        if tag in 'BCDFIJSZsc':
            a.take(2)
        elif tag == 'e':
            a.take(4)
        elif tag == '@':
            annotation(a)
        elif tag == '[':
            for _ in range(a.number(2)):
                element(a)
        else:
            raise ValueError(f'Invalid annotation tag {tag}')

    found = []
    for _ in range(r.number(2)):
        access, name, descriptor = r.number(2), pool[r.number(2)], pool[r.number(2)]
        annotations = set()
        for kind, value in attributes():
            if kind in ('RuntimeVisibleAnnotations', 'RuntimeInvisibleAnnotations'):
                a = Reader(value)
                annotations.update(annotation(a) for _ in range(a.number(2)))
        if TESTS & annotations:
            found.append({'method': name, 'descriptor': descriptor, 'access': access,
                          'annotations': sorted(TESTS & annotations)})
    attributes()
    if r.offset != len(data):
        raise ValueError('Trailing data in JVM class')
    return found


def inspect(build_root, modules=None):
    build_root = Path(build_root)
    if modules is None:
        settings = Path('settings.gradle.kts')
        modules = re.findall(r'^include\("([^"]+)"\)', settings.read_text(encoding='utf-8'), re.M) if settings.exists() else []
    bases = [build_root] + [build_root / module.replace(':', '/') for module in modules]
    classes = sorted({path for base in bases for path in (base / 'classes').rglob('*.class')
                      if any(part in {'test', 'jvmTest'} for part in path.relative_to(base / 'classes').parts)})
    failures, count = [], 0
    for path in classes:
        try:
            for method in methods(path.read_bytes()):
                count += 1
                reason = []
                if not method['descriptor'].endswith(')V'):
                    reason.append('test must return JVM void; use explicit Unit for coroutine expression bodies')
                if method['access'] & 0x0002:
                    reason.append('test must not be private')
                if method['access'] & 0x0008:
                    reason.append('test must not be static')
                if method['access'] & 0x0400:
                    reason.append('test must not be abstract')
                if 'Lorg/junit/Test;' in method['annotations'] and not method['access'] & 0x0001:
                    reason.append('JUnit 4 test must be public')
                if reason:
                    failures.append({'class': path.relative_to(build_root).as_posix(), **method, 'reasons': reason})
        except (ValueError, IndexError) as error:
            failures.append({'class': path.relative_to(build_root).as_posix(), 'reasons': [str(error)]})
    if not classes or not count:
        failures.append({'reasons': ['No compiled test methods found; compile tests before checking']})
    return {'classes_scanned': len(classes), 'test_methods': count, 'failures': failures,
            'limits': 'Direct JVM JUnit annotations only; execution and custom/dynamic test discovery require separate evidence.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-root', type=Path, default=None)
    parser.add_argument('--module', action='append')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    try:
        root = _build_root.require_results(args.build_root or _build_root.resolve())
    except (OSError, ValueError) as error:
        print(f'Invalid test evidence: {error}', file=sys.stderr)
        raise SystemExit(1)
    result = inspect(root, args.module)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    for failure in result['failures']:
        print(json.dumps(failure))
    print(f"Scanned {result['classes_scanned']} classes and {result['test_methods']} JUnit methods; {len(result['failures'])} invalid methods/evidence")
    raise SystemExit(bool(result['failures']))


if __name__ == '__main__':
    main()
