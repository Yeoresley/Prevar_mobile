# PREVAR Mobile Android MVP 0.4.0

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
- adjuntos, fotografías y PDF offline, cifrados con Android Keystore y sincronizados con PREVAR Web;
- captura de fotografía desde cámara, selección múltiple de imágenes/PDF, apertura controlada, descarga bajo demanda y eliminación sincronizada;
- borradores de riesgo alineados con PREVAR (objeto, riesgo, probabilidad, impacto, resultado, nivel y argumentación), con sincronización bidireccional;
- matriz de riesgo PREVAR: 1–2 BAJO, 3–4 MEDIO, 6–9 ALTO;
- política HTTPS segura por defecto: release rechaza HTTP; debug solo permite HTTP en LAN privada;
- cliente OkHttp sin logging en release y con TLS moderno para HTTPS;
- confianza TLS por instalación: el QR puede incluir la huella SHA-256 de la CA pública del servidor y PREVAR Mobile la verifica antes de confiar en ella, sin desactivar la validación de hostname.

Los adjuntos y borradores de riesgo se conservan offline y se sincronizan cuando PREVAR vuelve a estar disponible. Los binarios permanecen cifrados en el teléfono y solo se descifran temporalmente para abrirlos o transmitirlos.


## Compilación online

No es obligatorio instalar Android SDK localmente para generar el APK de prueba.
El repositorio incluye `.github/workflows/android-debug.yml`. En GitHub use **Actions → Build PREVAR Mobile APK → Run workflow** y descargue el artefacto `PREVAR-Mobile-debug-apk`.

Consulte `BUILD_ONLINE.md` para el procedimiento completo.


## Seguridad 0.4.0

- La base Room continúa cifrada con SQLCipher.
- Los adjuntos se cifran individualmente con AES-GCM y una clave no exportable de Android Keystore.
- Los archivos solo se descifran temporalmente en caché cuando el usuario pulsa **Abrir**.
- Se bloquean MIME no permitidos, adjuntos mayores de 25 MB, traversal de nombres de archivo, credenciales embebidas en URLs y HTTP público.
- La migración de base 1→2 conserva los datos existentes.


## Sincronización 0.4.0

- Los borradores de riesgo usan operaciones idempotentes `risk_draft.upsert` / `risk_draft.delete`.
- Las evidencias binarias usan `POST/GET/DELETE attachment`, SHA-256 y límite de 25 MB.
- Los adjuntos remotos se descargan bajo demanda y vuelven a cifrarse inmediatamente en el almacén privado del dispositivo.
- La migración Room 2→3 conserva los datos offline existentes y añade estado de sincronización.


## Integración Web 0.4.0

- La evidencia documental se adjunta mediante **Adjuntar** o **Tomar foto**; el campo de evidencia Web se muestra solo lectura en el móvil para evitar referencias de archivo inválidas.
- El servidor PREVAR 1.5 RC2 Bridge v3 materializa el archivo real en `static/files` y enlaza la evaluación Web.
- Los borradores de riesgo sincronizados se promueven a `gr_riesgo` y quedan visibles desde la pregunta correspondiente.


## Despliegue universal 0.4.0

PREVAR Mobile 0.4.0 puede vincularse de forma segura con instalaciones Windows o Linux/Docker que generen su propia autoridad certificadora local.

El QR de vinculación puede incluir `tls_ca_sha256` y `tls_ca_url`. La aplicación solo descarga la CA desde el mismo host indicado por el QR, comprueba que sea una CA X.509 válida, compara exactamente su SHA-256 y la almacena cifrada mediante Android Keystore. La validación TLS y de hostname permanece activa.

Los QR antiguos sin estos campos siguen utilizando la confianza del sistema y la CA interna incluida, preservando compatibilidad con PREVAR 1.5.
