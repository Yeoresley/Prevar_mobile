# Compilar PREVAR Mobile online

No es obligatorio instalar Android SDK en la PC para obtener el APK de prueba.
Este proyecto incluye GitHub Actions para compilar automáticamente un APK `debug` en la nube.

## Flujo recomendado para el MVP

1. Crear un repositorio privado en GitHub y subir el contenido de esta carpeta como raíz del repositorio.
2. Entrar en **Actions**.
3. Seleccionar **Build PREVAR Mobile APK**.
4. Pulsar **Run workflow**.
5. Al terminar correctamente, abrir la ejecución y descargar el artefacto **PREVAR-Mobile-debug-apk**.
6. Extraer `app-debug.apk` del ZIP del artefacto e instalarlo en el teléfono Android.

El workflow instala temporalmente en el runner de GitHub:
- JDK 17;
- Android SDK 35;
- Build Tools 35.0.0;
- Gradle 8.9.

Después ejecuta:
- validación estática del proyecto;
- pruebas unitarias;
- compilación `assembleDebug`;
- publicación del APK como artefacto descargable.

## Qué NO contiene el build online

- contraseñas de MariaDB;
- clave privada de licencias ASSI;
- credenciales PREVAR del usuario;
- firma de publicación Google Play.

El APK `debug` es adecuado para las pruebas de campo/LAN que estamos realizando.

## Cuándo instalar Android Studio localmente

No es necesario para esta primera compilación. Sí será recomendable cuando empecemos a depurar en profundidad el teléfono, usar Logcat/ADB, inspeccionar Room o trabajar iterativamente en la interfaz.
