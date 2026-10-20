import importlib.util
import io
import os
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("distribution", Path(__file__).with_name("verify-public-distribution.py"))
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class PublicDistributionTests(unittest.TestCase):
    def archive(self, entries):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        return buffer.getvalue()

    def test_public_classes_pass(self):
        policy.check_archive("release.jar", self.archive({"gg/floyd/Public.class": b"public", "fabric.mod.json": b"{}"}))

    def test_private_class_path_fails(self):
        with self.assertRaises(ValueError):
            policy.check_archive("release.jar", self.archive({"gg/floyd/polinex/Feature.class": b"private"}))

    def test_renamed_class_with_private_reference_fails(self):
        with self.assertRaises(ValueError):
            policy.check_archive("release.jar", self.archive({"gg/floyd/Feature.class": b"gg/floyd/polinex/Access"}))

    def test_dotted_source_reference_fails(self):
        with self.assertRaises(ValueError):
            policy.check_content("src/Feature.kt", b"import gg.floyd.polinex.Access")

    def test_private_ancestor_fails_after_its_file_is_deleted(self):
        cwd, baseline = Path.cwd(), policy.PUBLIC_BASELINE
        with tempfile.TemporaryDirectory() as directory:
            try:
                os.chdir(directory)
                def git(*args):
                    return subprocess.check_output(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', *args],stderr=subprocess.DEVNULL).decode().strip()
                git('init')
                git('commit', '--allow-empty', '-m', 'Public baseline')
                policy.PUBLIC_BASELINE = git('rev-parse', 'HEAD')
                source = Path('src/polinex/Feature.kt')
                source.parent.mkdir(parents=True)
                source.write_text('private feature')
                git('add', 'src')
                git('commit', '-m', 'Private feature')
                source.unlink()
                git('add', '-u')
                git('commit', '-m', 'Delete private feature')
                with self.assertRaises(ValueError):
                    policy.check_ref('HEAD')
            finally:
                os.chdir(cwd)
                policy.PUBLIC_BASELINE = baseline


if __name__ == "__main__":
    unittest.main()
