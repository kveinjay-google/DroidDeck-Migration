#!/usr/bin/env python3
"""Bounded external-volume build. Signing secrets never appear in command arguments."""
import os,sys,subprocess,pathlib,signal
root=pathlib.Path(__file__).resolve().parents[2]
b=pathlib.Path('/Volumes/LEXA 4TB DISK/BuildOutputs/droiddeck-migration')
env=dict(os.environ)
env['JAVA_TOOL_OPTIONS']='-XX:ActiveProcessorCount=2 -Djava.io.tmpdir="/Volumes/LEXA 4TB DISK/BuildOutputs/droiddeck-migration/tmp"'
env.update(JAVA_HOME='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home',ANDROID_HOME='/opt/homebrew/share/android-commandlinetools',GRADLE_USER_HOME=str(b/'gradle-home'),TMPDIR=str(b/'tmp'),TMP=str(b/'tmp'),TEMP=str(b/'tmp'),DROIDDECK_MIGRATION_KEYSTORE=str(b/'signing/migration.p12'),DROIDDECK_MIGRATION_STORE_PASSWORD=(b/'signing/store-password').read_text(),DROIDDECK_BUILD_LABEL='migration.1 based on 10ef8c4',DROIDDECK_SOURCE_COMMIT='10ef8c4bd62b21872fc13382a808cb0f2f117b26')
gradle='/Users/kevin/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle'
command=[gradle,'--no-daemon','--max-workers=2','--no-configuration-cache','-PndkVersion=28.2.13676358']+(sys.argv[1:] or [':app:testDebugUnitTest',':app:assembleRelease'])
process = subprocess.Popen(command, cwd=root, env=env, start_new_session=True)
try: sys.exit(process.wait(timeout=1800))
except subprocess.TimeoutExpired:
    os.killpg(process.pid, signal.SIGTERM)
    try: process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL); process.wait()
    raise SystemExit('Build exceeded 30-minute timeout; build process group stopped')
