import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('manifest',Path(__file__).with_name('record-validation-manifest.py'))
manifest=importlib.util.module_from_spec(spec)
spec.loader.exec_module(manifest)

class ManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.build=self.root/'build'
        subprocess.run(['git','init','-q',str(self.root)],check=True)
        (self.root/'.gitignore').write_text('build/\n')
        (self.root/'source.kt').write_text('val version = 1\n')
        subprocess.run(['git','-C',str(self.root),'add','.'],check=True)
        subprocess.run(['git','-C',str(self.root),'-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','fixture'],check=True)
        self.xml=self.build/'module/test-results/test/TEST-demo.xml'
        self.xml.parent.mkdir(parents=True)
        self.xml.write_text('<testsuite name="demo" tests="1" failures="0" errors="0" skipped="0"><testcase name="ok"/></testsuite>')
        self.coverage=self.build/'reports/kover/report.xml'
        self.coverage.parent.mkdir(parents=True)
        self.coverage.write_text('<report/>')
        self.environment=patch.dict('os.environ',{'GITHUB_SHA':''})
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def test_clean_commit_records_content_identity(self):
        result=manifest.collect(self.root,self.build)
        self.assertTrue(result['release_candidate'])
        self.assertIn('source.kt',result['source_sha256'])
        self.assertEqual(1,result['tests']['tests'])

    def test_dirty_tree_cannot_be_claimed_as_release_candidate(self):
        (self.root/'source.kt').write_text('changed')
        with self.assertRaisesRegex(ValueError,'clean committed'):
            manifest.collect(self.root,self.build)
        self.assertFalse(manifest.collect(self.root,self.build,True)['release_candidate'])

    def test_untracked_source_is_not_clean(self):
        (self.root/'other.kt').write_text('changed')
        with self.assertRaises(ValueError):
            manifest.collect(self.root,self.build)

    def test_wrong_github_commit_is_rejected(self):
        with patch.dict('os.environ',{'GITHUB_SHA':'wrong'}),self.assertRaises(ValueError):
            manifest.collect(self.root,self.build)

    def test_missing_coverage_and_failing_tests_are_rejected(self):
        self.coverage.unlink()
        with self.assertRaises(ValueError):
            manifest.collect(self.root,self.build)
        self.coverage.write_text('<report/>')
        self.xml.write_text('<testsuite name="demo" tests="1" failures="1" errors="0" skipped="0"/>')
        with self.assertRaises(ValueError):
            manifest.collect(self.root,self.build)

    def test_inconsistent_xml_counters_are_rejected(self):
        self.xml.write_text('<testsuite name="demo" tests="2" failures="0" errors="0" skipped="0"><testcase name="ok"/></testsuite>')
        with self.assertRaisesRegex(ValueError,'inconsistent JUnit'):
            manifest.collect(self.root,self.build)

    def test_windows_root_coverage_path_and_skips_are_recorded(self):
        external=self.build/'_root/reports/kover/report.xml'
        external.parent.mkdir(parents=True)
        external.write_bytes(self.coverage.read_bytes())
        self.coverage.unlink()
        self.xml.write_text('<testsuite name="demo" tests="1" failures="0" errors="0" skipped="1"><testcase name="disabled"><skipped/></testcase></testsuite>')
        result=manifest.collect(self.root,self.build,coverage_report=external)
        self.assertEqual(1,result['tests']['skipped'])
        self.assertEqual('disabled',result['skipped'][0]['name'])
        self.assertIn('_root/reports/kover/report.xml',result['artifact_sha256'])

if __name__=='__main__':
    unittest.main()
