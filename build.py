#!/usr/bin/env python3
"""Build without Gradle, network access, or third-party libraries. Requires JDK 17 and Android SDK."""
from pathlib import Path
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
TOOLS = SDK / 'build-tools/34.0.0'
ANDROID = SDK / 'platforms/android-34/android.jar'
BUILD = ROOT / 'build'
KEY = Path(os.environ.get('DRIVE_KEYSTORE', str(ROOT / '.signing/drive-local.jks')))

def run(*args):
    subprocess.run([str(x) for x in args], check=True)

def main():
    if not ANDROID.exists() or not TOOLS.exists():
        raise SystemExit('Install Android SDK platform 34 and build-tools 34.0.0; set ANDROID_HOME if needed.')
    if BUILD.exists():
        shutil.rmtree(BUILD)
    (BUILD / 'classes').mkdir(parents=True)
    (BUILD / 'dex').mkdir()
    run(TOOLS / 'aapt', 'package', '-f', '-M', ROOT / 'AndroidManifest.xml', '-S', ROOT / 'res', '-I', ANDROID, '-F', BUILD / 'resources.apk')
    bootpath = str(ANDROID) + os.pathsep + str(TOOLS / 'core-lambda-stubs.jar')
    run('javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', bootpath, '-d', BUILD / 'classes', *sorted((ROOT / 'src').rglob('*.java')))
    run(TOOLS / 'd8', '--lib', ANDROID, '--min-api', '23', '--output', BUILD / 'dex', *sorted((BUILD / 'classes').rglob('*.class')))
    shutil.copyfile(BUILD / 'resources.apk', BUILD / 'unsigned.apk')
    with zipfile.ZipFile(BUILD / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
        apk.write(BUILD / 'dex/classes.dex', 'classes.dex')
    run(TOOLS / 'zipalign', '-f', '4', BUILD / 'unsigned.apk', BUILD / 'aligned.apk')
    if not KEY.exists():
        KEY.parent.mkdir(parents=True, exist_ok=True)
        run('keytool', '-genkeypair', '-keystore', KEY, '-storepass', 'android', '-keypass', 'android', '-alias', 'drive-local', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=DRIVE Local Development')
        KEY.chmod(0o600)
    run(TOOLS / 'apksigner', 'sign', '--ks', KEY, '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', BUILD / 'drive-launcher.apk', BUILD / 'aligned.apk')
    run(TOOLS / 'apksigner', 'verify', '--verbose', BUILD / 'drive-launcher.apk')
    print('Built:', BUILD / 'drive-launcher.apk')

if __name__ == '__main__':
    main()
