# PREVAR Mobile Android MVP

Proyecto Android nativo para trabajo de autocontrol offline.

## Tecnología

- Kotlin
- Jetpack Compose
- Room
- SQLCipher
- Android Keystore
- Retrofit / OkHttp
- WorkManager
- ZXing para QR

## Uso

1. Abra el proyecto en Android Studio.
2. Compile con JDK 17/21 y Android SDK 35.
3. En PREVAR Web pulse el icono del teléfono y genere el QR.
4. En Android use **Escanear QR de PREVAR**.
5. Sincronice en la LAN, trabaje sin conexión y vuelva a sincronizar al regresar.

La aplicación no accede directamente a MariaDB y no almacena la contraseña del usuario PREVAR.

## Estado MVP

Implementados:
- emparejamiento QR/manual;
- token de dispositivo;
- base Room cifrada;
- listado de autocontroles;
- evaluación offline;
- medidas offline;
- outbox idempotente;
- sincronización push/pull;
- validaciones principales de PREVAR;
- WorkManager con reintento cuando hay conectividad;
- resolución visual de conflictos 409 entre PREVAR Web y móvil;
- manejo explícito de pérdida de conectividad sin descartar cambios pendientes.

Pendiente de la siguiente iteración:
- adjuntos/fotos/PDF offline;
- borradores de riesgo;
- endurecimiento HTTPS cuando el despliegue disponga de certificado local.


## Compilación online

No es obligatorio instalar Android SDK localmente para generar el APK de prueba.
El repositorio incluye `.github/workflows/android-debug.yml`. En GitHub use **Actions → Build PREVAR Mobile APK → Run workflow** y descargue el artefacto `PREVAR-Mobile-debug-apk`.

Consulte `BUILD_ONLINE.md` para el procedimiento completo.
