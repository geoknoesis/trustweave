import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('junit_contract', Path(__file__).with_name('check-junit-contract.py'))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class JunitContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        cls.output = cls.root / 'classes/kotlin/test'
        cls.output.mkdir(parents=True)
        sources = {
            'org/junit/jupiter/api/Test.java': 'package org.junit.jupiter.api; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Test {}',
            'org/junit/jupiter/api/RepeatedTest.java': 'package org.junit.jupiter.api; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface RepeatedTest { int value(); }',
            'Example.java': '''import org.junit.jupiter.api.*;
                abstract class Example {
                    @Test void valid() {}
                    @Test String inferredReturn() { return "oops"; }
                    @Test private void hidden() {}
                    @Test static void staticTest() {}
                    @Test abstract void abstractTest();
                    @RepeatedTest(3) void repeated() {}
                    String helper() { return "not a test"; }
                    long constant = 123456789012L;
                    double decimal = 1.23;
                }''',
        }
        files = []
        for name, code in sources.items():
            path = cls.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(code, encoding='utf-8')
            files.append(str(path))
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac:
            raise RuntimeError('JDK required: the contract checker must be tested against real compiled classes')
        subprocess.run([javac, '-d', str(cls.output), *files], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_real_jvm_annotations_and_descriptors(self):
        found = checker.methods((self.output / 'Example.class').read_bytes())
        self.assertEqual({'valid', 'inferredReturn', 'hidden', 'staticTest', 'abstractTest', 'repeated'}, {m['method'] for m in found})
        self.assertEqual('()Ljava/lang/String;', next(m['descriptor'] for m in found if m['method'] == 'inferredReturn'))

    def test_invalid_methods_fail_and_valid_methods_pass(self):
        result = checker.inspect(self.root, [])
        self.assertEqual(6, result['test_methods'])
        self.assertEqual({'inferredReturn', 'hidden', 'staticTest', 'abstractTest'}, {f['method'] for f in result['failures']})

    def test_empty_build_does_not_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertTrue(checker.inspect(folder, [])['failures'])

    def test_corrupt_and_truncated_classes_fail(self):
        valid = (self.output / 'Example.class').read_bytes()
        for data in [b'', b'wrong', valid[:16], valid[:-1], valid + b'junk']:
            with self.subTest(size=len(data)), self.assertRaises((ValueError, IndexError)):
                checker.methods(data)

    def test_main_classes_are_not_test_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'classes/kotlin/main/Example.class'
            path.parent.mkdir(parents=True)
            path.write_bytes((self.output / 'Example.class').read_bytes())
            result = checker.inspect(folder, [])
            self.assertEqual(0, result['test_methods'])
            self.assertTrue(result['failures'])

    def test_multiplatform_jvm_test_classes_are_scanned(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'classes/kotlin/jvm/test/Example.class'
            path.parent.mkdir(parents=True)
            path.write_bytes((self.output / 'Example.class').read_bytes())
            self.assertEqual(6, checker.inspect(folder, [])['test_methods'])


if __name__ == '__main__':
    unittest.main()
