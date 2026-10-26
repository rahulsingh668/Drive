#!/usr/bin/env python3
"""Build instrumentation fixtures using the launcher's local development key."""
from pathlib import Path
import importlib.util
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parent
spec = importlib.util.spec_from_file_location('drive_app_build', PROJECT / 'build.py')
app = importlib.util.module_from_spec(spec)
spec.loader.exec_module(app)

def main():
    if not app.KEY.exists():
        raise SystemExit('Build the launcher first: python3 build.py')
    target = ROOT / 'build'
    if target.exists():
        shutil.rmtree(target)
    (target / 'classes').mkdir(parents=True)
    (target / 'dex').mkdir()
    run = app.run
    run(app.TOOLS / 'aapt', 'package', '-f', '-M', ROOT / 'AndroidManifest.xml', '-I', app.ANDROID, '-F', target / 'unsigned.apk')
    bootpath = str(app.ANDROID) + app.os.pathsep + str(app.TOOLS / 'core-lambda-stubs.jar')
    run('javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', bootpath, '-d', target / 'classes', *sorted((ROOT / 'src').rglob('*.java')))
    run(app.TOOLS / 'd8', '--lib', app.ANDROID, '--min-api', '23', '--output', target / 'dex', *sorted((target / 'classes').rglob('*.class')))
    with zipfile.ZipFile(target / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
        apk.write(target / 'dex/classes.dex', 'classes.dex')
    run(app.TOOLS / 'zipalign', '-f', '4', target / 'unsigned.apk', target / 'aligned.apk')
    run(app.TOOLS / 'apksigner', 'sign', '--ks', app.KEY, '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', target / 'drive-tests.apk', target / 'aligned.apk')
    run(app.TOOLS / 'apksigner', 'verify', target / 'drive-tests.apk')
    print('Built:', target / 'drive-tests.apk')

if __name__ == '__main__':
    main()
