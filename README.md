# PREVAR Mobile Android MVP 0.2.0

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
- manejo explícito de pérdida de conectividad sin descartar cambios pendientes;
- adjuntos, fotografías y PDF offline, cifrados con Android Keystore y almacenados en el espacio privado de la aplicación;
- captura de fotografía desde cámara, selección múltiple de imágenes/PDF, apertura controlada y eliminación local;
- borradores de riesgo alineados con PREVAR (objeto, riesgo, probabilidad, impacto, resultado, nivel y argumentación);
- matriz de riesgo PREVAR: 1–2 BAJO, 3–4 MEDIO, 6–9 ALTO;
- política HTTPS segura por defecto: release rechaza HTTP; debug solo permite HTTP en LAN privada;
- cliente OkHttp sin logging en release y con TLS moderno para HTTPS.

Los adjuntos y borradores de riesgo de esta versión son artefactos offline locales asociados a la evaluación; no se envían al servidor mediante operaciones no soportadas por la API actual.


## Compilación online

No es obligatorio instalar Android SDK localmente para generar el APK de prueba.
El repositorio incluye `.github/workflows/android-debug.yml`. En GitHub use **Actions → Build PREVAR Mobile APK → Run workflow** y descargue el artefacto `PREVAR-Mobile-debug-apk`.

Consulte `BUILD_ONLINE.md` para el procedimiento completo.


## Seguridad 0.2.0

- La base Room continúa cifrada con SQLCipher.
- Los adjuntos se cifran individualmente con AES-GCM y una clave no exportable de Android Keystore.
- Los archivos solo se descifran temporalmente en caché cuando el usuario pulsa **Abrir**.
- Se bloquean MIME no permitidos, adjuntos mayores de 25 MB, traversal de nombres de archivo, credenciales embebidas en URLs y HTTP público.
- La migración de base 1→2 conserva los datos existentes.
