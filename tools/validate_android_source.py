#!/usr/bin/env python3
from pathlib import Path
import re, subprocess, shutil, tempfile

root = Path(__file__).resolve().parents[1]
required = [
    'settings.gradle.kts','build.gradle.kts','app/build.gradle.kts','app/src/main/AndroidManifest.xml',
    'app/src/main/java/cu/assi/prevarmobile/MainActivity.kt',
    'app/src/main/java/cu/assi/prevarmobile/data/PrevarRepository.kt',
    'app/src/main/java/cu/assi/prevarmobile/data/local/AppDatabase.kt',
    'app/src/main/java/cu/assi/prevarmobile/domain/AttachmentPolicy.kt',
    'app/src/main/java/cu/assi/prevarmobile/domain/RiskMatrix.kt',
    'app/src/main/java/cu/assi/prevarmobile/domain/ServerUrlPolicy.kt',
    'app/src/main/java/cu/assi/prevarmobile/security/AttachmentVault.kt',
    'app/src/main/java/cu/assi/prevarmobile/sync/SyncWorker.kt',
]
missing=[x for x in required if not (root/x).exists()]
if missing: raise SystemExit('Faltan archivos: '+str(missing))
text='\n'.join(p.read_text(encoding='utf-8') for p in root.rglob('*.kt'))
text += '\n' + (root/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
text += '\n' + (root/'app/build.gradle.kts').read_text(encoding='utf-8')
for forbidden in ['PrevarDB#2026!', 'F_PASSWORD', 'mysql://', 'mariadb://', 'TrustAll', 'ALLOW_ALL_HOSTNAME_VERIFIER']:
    if forbidden in text: raise SystemExit('Secreto/conexión directa detectada: '+forbidden)
for marker in ['evaluation.update','measure.upsert','risk_draft.upsert','uploadAttachment','PREVAR-MOBILE-PAIR-1','WorkManager','SQLCipher','AttachmentVault','RiskMatrix','ServerUrlPolicy','usesCleartextTraffic','versionName = "0.3.2"']:
    if marker not in text and marker not in (root/'README.md').read_text(encoding='utf-8'):
        raise SystemExit('Marcador funcional faltante: '+marker)
# Compila el núcleo Kotlin puro disponible sin Android SDK.
kotlinc=shutil.which('kotlinc')
if kotlinc:
    validator=root/'app/src/main/java/cu/assi/prevarmobile/domain/EvaluationValidator.kt'
    risk=root/'app/src/main/java/cu/assi/prevarmobile/domain/RiskMatrix.kt'
    server_url=root/'app/src/main/java/cu/assi/prevarmobile/domain/ServerUrlPolicy.kt'
    attachment=root/'app/src/main/java/cu/assi/prevarmobile/domain/AttachmentPolicy.kt'
    with tempfile.TemporaryDirectory() as td:
        runner=Path(td)/'Runner.kt'
        runner.write_text('''import cu.assi.prevarmobile.domain.AttachmentPolicy
import cu.assi.prevarmobile.domain.EvaluationValidator
import cu.assi.prevarmobile.domain.RiskMatrix
import cu.assi.prevarmobile.domain.ServerUrlPolicy
fun main(){
check(EvaluationValidator.validate(EvaluationValidator.Input(3,"","",0),100).isEmpty())
check(EvaluationValidator.validate(EvaluationValidator.Input(2,"1234567890","1234567890",1),10).isEmpty())
check(RiskMatrix.assess(2,3).result == 6)
check(RiskMatrix.assess(2,3).level == RiskMatrix.LEVEL_HIGH)
check(ServerUrlPolicy.normalize("https://prevar.example/mobile-api/v1/", false) == "https://prevar.example/mobile-api/v1")
check(ServerUrlPolicy.normalize("http://192.168.1.5/mobile-api/v1", true).startsWith("http://"))
check(runCatching { ServerUrlPolicy.normalize("http://example.com/mobile-api/v1", true) }.isFailure)
check(AttachmentPolicy.sanitizeFileName("../../secret.pdf") == "secret.pdf")
check(runCatching { AttachmentPolicy.validateMime("application/x-msdownload") }.isFailure)
println("OK")
}
''')
        jar=Path(td)/'smoke.jar'
        subprocess.run([kotlinc,str(validator),str(risk),str(server_url),str(attachment),str(runner),'-include-runtime','-d',str(jar)],check=True,capture_output=True,text=True)
        out=subprocess.run(['java','-jar',str(jar)],check=True,capture_output=True,text=True).stdout.strip()
        if out!='OK': raise SystemExit('Kotlin smoke no válido')
print('OK - estructura Android, seguridad estática y núcleo Kotlin validados.')
