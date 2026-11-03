#!/usr/bin/env python3
"""Build a small, signed Android APK using the SDK and JDK, without Gradle downloads."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser()
    sdk_default = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk_default:
        sdk_default = Path.home() / ('Library/Android/sdk' if os.sys.platform == 'darwin' else 'Android/Sdk')
    parser.add_argument('--sdk', type=Path, default=Path(sdk_default))
    parser.add_argument('--java-home', type=Path, default=Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
    args = parser.parse_args()
    java = args.java_home
    if java is None:
        javac = shutil.which('javac')
        if javac:
            candidate = Path(javac).resolve().parent.parent
            if candidate != Path('/usr'):
                java = candidate
    if java is None or not (java / 'bin/javac').is_file():
        raise SystemExit('JDK 17+ required. Pass --java-home /path/to/jdk.')
    sdk = args.sdk.expanduser().resolve()
    platform = sdk / 'platforms/android-35/android.jar'
    tools = sdk / 'build-tools/35.0.0'
    for item in [platform, tools / 'aapt2', tools / 'd8', tools / 'apksigner', tools / 'zipalign']:
        if not item.exists():
            raise SystemExit(f'Missing SDK component: {item}')
    env = dict(os.environ, JAVA_HOME=str(java), PATH=str(java / 'bin') + os.pathsep + os.environ['PATH'])

    def run(*command):
        subprocess.run([str(value) for value in command], env=env, check=True)

    build = ROOT / 'build'
    if build.exists():
        shutil.rmtree(build)
    for name in ['gen', 'classes', 'dex']:
        (build / name).mkdir(parents=True, exist_ok=True)
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    run(tools / 'aapt2', 'compile', '--dir', ROOT / 'res', '-o', build / 'resources.zip')
    run(tools / 'aapt2', 'link', '-o', build / 'resources.apk', '-I', platform,
        '--manifest', ROOT / 'AndroidManifest.xml', '--java', build / 'gen',
        '--auto-add-overlay', build / 'resources.zip')
    sources = sorted((ROOT / 'src').rglob('*.java')) + sorted((build / 'gen').rglob('*.java'))
    run(java / 'bin/javac', '-encoding', 'UTF-8', '--release', '8', '-Xlint:unchecked',
        '-classpath', platform, '-d', build / 'classes', *sources)
    classes = sorted((build / 'classes').rglob('*.class'))
    run(tools / 'd8', '--release', '--min-api', '26', '--lib', platform, '--output', build / 'dex', *classes)
    unsigned = build / 'unsigned.apk'
    shutil.copy2(build / 'resources.apk', unsigned)
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        for dex in sorted((build / 'dex').glob('*.dex')):
            archive.write(dex, dex.name)
    run(tools / 'zipalign', '-f', '-p', '4', unsigned, build / 'aligned.apk')
    keys = ROOT / '.keys'
    keys.mkdir(mode=0o700, exist_ok=True)
    credentials = keys / 'signer.json'
    keystore = keys / 'release.p12'
    if keystore.exists() != credentials.exists():
        raise SystemExit('Signing key or credentials missing; restore the matching backup instead of replacing the app identity.')
    if not credentials.exists():
        credentials.write_text(json.dumps({'password': secrets.token_urlsafe(32)}))
        credentials.chmod(0o600)
        password = json.loads(credentials.read_text())['password']
        env['DISPLAY_LAUNCHER_KEY_PASSWORD'] = password
        run(java / 'bin/keytool', '-genkeypair', '-keystore', keystore, '-storetype', 'PKCS12',
            '-storepass:env', 'DISPLAY_LAUNCHER_KEY_PASSWORD', '-alias', 'display-launcher',
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
            '-dname', 'CN=Display Launcher, OU=Personal Tools')
        keystore.chmod(0o600)
    env['DISPLAY_LAUNCHER_KEY_PASSWORD'] = json.loads(credentials.read_text())['password']
    output = dist / 'Display-Launcher-1.0.0.apk'
    run(tools / 'apksigner', 'sign', '--ks', keystore, '--ks-key-alias', 'display-launcher',
        '--ks-pass', 'env:DISPLAY_LAUNCHER_KEY_PASSWORD', '--out', output, build / 'aligned.apk')
    run(tools / 'apksigner', 'verify', '--verbose', output)
    run(tools / 'zipalign', '-c', '-p', '4', output)
    metadata = {'apk': output.name, 'bytes': output.stat().st_size, 'sha256': hashlib.sha256(output.read_bytes()).hexdigest()}
    (dist / 'build-info.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(json.dumps(metadata, indent=2))


if __name__ == '__main__':
    main()
