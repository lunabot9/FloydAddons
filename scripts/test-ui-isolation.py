"""Regression: two incompatible Compose versions must coexist on the JVM."""
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("isolation", Path(__file__).with_name("isolate-ui-libraries.py"))
isolation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(isolation)


def jar(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as archive:
        for name, value in entries.items():
            archive.writestr(name, value)
    return out.getvalue()


class IsolationTest(unittest.TestCase):
    def test_nested_metadata_services_resources_and_native_bytes(self):
        native = bytes(range(256))
        shared = jar({"fabric.mod.json": '{"id":"org_jetbrains_skiko_skiko-awt"}',
                      "META-INF/skiko.kotlin_module": b"unchanged index", "skiko.dll": native})
        shim = jar({"org/polyfrost/oneconfig/compat/marker.properties": "type=androidx.compose.Service"})
        nested = jar({
            "fabric.mod.json": json.dumps({"id": "oneconfigv1", "entrypoints": {"client": ["org.polyfrost.oneconfig.Boot"]},
                "mixins": ["mixins.oneconfigv1.json"], "depends": {"compose-multiplatform": "*"}}),
            "mixins.oneconfigv1.json": '{"package":"org.polyfrost.oneconfig.mixin"}',
            "META-INF/services/androidx.compose.Service": "org.polyfrost.oneconfig.Provider",
            "META-INF/module.kotlin_module": b"compile-time index",
            "skiko.dll": native,
            "META-INF/jars/skiko.jar": shared,
            "META-INF/oneconfig/shim.jar": shim,
        })
        transformed = isolation.rewrite_archive(jar({"META-INF/jars/ui.jar": nested}))
        with zipfile.ZipFile(io.BytesIO(transformed)) as outer:
            with zipfile.ZipFile(io.BytesIO(outer.read("META-INF/jars/ui.jar"))) as inner:
                m = json.loads(inner.read("fabric.mod.json"))
                self.assertEqual(m["id"], "floyd_ui_oneconfigv1")
                self.assertIn("floyd_ui_compose-multiplatform", m["depends"])
                self.assertIn(m["mixins"][0], inner.namelist())
                self.assertEqual(inner.read("skiko.dll"), native)
                self.assertEqual(inner.read("META-INF/jars/skiko.jar"), shared)
                with zipfile.ZipFile(io.BytesIO(inner.read("META-INF/oneconfig/shim.jar"))) as shim_archive:
                    self.assertEqual(shim_archive.read("gg/floyd/internal/uilibs/org/polyfrost/oneconfig/compat/marker.properties"),
                                     b"type=gg.floyd.internal.uilibs.androidx.compose.Service")
                self.assertNotIn("META-INF/module.kotlin_module", inner.namelist())
                self.assertEqual(inner.read("META-INF/services/gg.floyd.internal.uilibs.androidx.compose.Service"),
                    b"gg.floyd.internal.uilibs.org.polyfrost.oneconfig.Provider")
        self.assertEqual(isolation.rewrite_archive(transformed), transformed)

    def test_incompatible_versions_execute_together(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            def compile_sources(directory, sources, classpath=None):
                directory.mkdir()
                paths = []
                for name, content in sources.items():
                    p = directory / name
                    p.parent.mkdir(parents=True, exist_ok=True)
                    p.write_text(content)
                    paths.append(str(p))
                command = ["javac", "-d", str(directory)]
                if classpath:
                    command += ["-cp", str(classpath)]
                subprocess.run(command + paths, check=True, capture_output=True)
            new, old, caller = root / "new", root / "old", root / "caller"
            compile_sources(new, {
                "androidx/compose/Runtime.java": 'package androidx.compose; public class Runtime { public static String newApi() { return "private"; } }',
                "org/polyfrost/oneconfig/Ui.java": 'package org.polyfrost.oneconfig; public class Ui { public static String render() { return androidx.compose.Runtime.newApi(); } }',
                "Floyd.java": 'public class Floyd { public static String render() { return org.polyfrost.oneconfig.Ui.render(); } }',
            })
            compile_sources(old, {"androidx/compose/Runtime.java": 'package androidx.compose; public class Runtime { public static String oldApi() { return "nebula"; } }'})
            isolated = root / "private.jar"
            original = root / "original.jar"
            original.write_bytes(jar({p.relative_to(new).as_posix(): p.read_bytes() for p in new.rglob("*.class")}))
            isolated.write_bytes(isolation.rewrite_archive(original.read_bytes()))
            self.assertGreater(isolation.verify_archive(isolated.read_bytes()), 0)
            compile_sources(caller, {"Main.java": 'public class Main { public static void main(String[] args) { System.out.print(androidx.compose.Runtime.oldApi()+":"+Floyd.render()); } }'}, os.pathsep.join(map(str, [old, isolated])))
            broken = subprocess.run(["java", "-cp", os.pathsep.join(map(str, [old, original, caller])), "Main"], capture_output=True, text=True)
            self.assertNotEqual(broken.returncode, 0)
            self.assertIn("NoSuchMethodError", broken.stderr)
            result = subprocess.run(["java", "-cp", os.pathsep.join(map(str, [old, isolated, caller])), "Main"], check=True, capture_output=True, text=True)
            self.assertEqual(result.stdout, "nebula:private")


if __name__ == "__main__":
    unittest.main()
