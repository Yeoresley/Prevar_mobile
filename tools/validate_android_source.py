#!/usr/bin/env python3
from pathlib import Path
import re, subprocess, shutil, tempfile

root = Path(__file__).resolve().parents[1]
required = [
    'settings.gradle.kts','build.gradle.kts','app/build.gradle.kts','app/src/main/AndroidManifest.xml',
    'app/src/main/java/cu/assi/prevarmobile/MainActivity.kt',
    'app/src/main/java/cu/assi/prevarmobile/data/PrevarRepository.kt',
    'app/src/main/java/cu/assi/prevarmobile/data/local/AppDatabase.kt',
    'app/src/main/java/cu/assi/prevarmobile/sync/SyncWorker.kt',
]
missing=[x for x in required if not (root/x).exists()]
if missing: raise SystemExit('Faltan archivos: '+str(missing))
text='\n'.join(p.read_text(encoding='utf-8') for p in root.rglob('*.kt'))
for forbidden in ['PrevarDB#2026!', 'F_PASSWORD', 'mysql://', 'mariadb://']:
    if forbidden in text: raise SystemExit('Secreto/conexión directa detectada: '+forbidden)
for marker in ['evaluation.update','measure.upsert','PREVAR-MOBILE-PAIR-1','WorkManager','SQLCipher']:
    if marker not in text and marker not in (root/'README.md').read_text(encoding='utf-8'):
        raise SystemExit('Marcador funcional faltante: '+marker)
# Compila el núcleo Kotlin puro disponible sin Android SDK.
kotlinc=shutil.which('kotlinc')
if kotlinc:
    validator=root/'app/src/main/java/cu/assi/prevarmobile/domain/EvaluationValidator.kt'
    with tempfile.TemporaryDirectory() as td:
        runner=Path(td)/'Runner.kt'
        runner.write_text('''import cu.assi.prevarmobile.domain.EvaluationValidator\nfun main(){\ncheck(EvaluationValidator.validate(EvaluationValidator.Input(3,"","",0),100).isEmpty())\ncheck(EvaluationValidator.validate(EvaluationValidator.Input(2,"1234567890","1234567890",1),10).isEmpty())\nprintln("OK")\n}\n''')
        jar=Path(td)/'smoke.jar'
        subprocess.run([kotlinc,str(validator),str(runner),'-include-runtime','-d',str(jar)],check=True,capture_output=True,text=True)
        out=subprocess.run(['java','-jar',str(jar)],check=True,capture_output=True,text=True).stdout.strip()
        if out!='OK': raise SystemExit('Kotlin smoke no válido')
print('OK - estructura Android, seguridad estática y núcleo Kotlin validados.')
