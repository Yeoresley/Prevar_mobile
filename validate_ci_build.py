#!/usr/bin/env python3
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
workflow = root / '.github/workflows/android-debug.yml'
if not workflow.exists():
    raise SystemExit('Falta workflow de compilación online')
text = workflow.read_text(encoding='utf-8')
required = [
    'workflow_dispatch:',
    'actions/checkout@v4',
    'actions/setup-java@v4',
    'cmdline-tools/latest/bin/sdkmanager',
    'platforms;android-35',
    'build-tools;35.0.0',
    'gradle/actions/setup-gradle@v4',
    'gradle-version: "8.9"',
    'testDebugUnitTest',
    'assembleDebug',
    'actions/upload-artifact@v4',
    'app/build/outputs/apk/debug/app-debug.apk',
]
missing = [x for x in required if x not in text]
if missing:
    raise SystemExit('Faltan elementos del CI: ' + ', '.join(missing))
if 'android-actions/setup-android@v3' in text:
    raise SystemExit('El workflow conserva setup-android@v3, que falla al solicitar el paquete obsoleto tools')
app = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
if not re.search(r'compileSdk\s*=\s*35', app):
    raise SystemExit('compileSdk no coincide con SDK 35 del workflow')
if not re.search(r'targetSdk\s*=\s*35', app):
    raise SystemExit('targetSdk no coincide con SDK 35 del workflow')
print('OK - workflow online usa el Android SDK preinstalado de GitHub y SDK 35.')
