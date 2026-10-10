# Firma de PREVAR Mobile Release

La clave de firma no se almacena en el repositorio.

Defina estas variables antes de compilar el APK Release:

- `PREVAR_ANDROID_KEYSTORE`: ruta absoluta al archivo .jks/.keystore.
- `PREVAR_ANDROID_STORE_PASSWORD`: contraseña del almacén.
- `PREVAR_ANDROID_KEY_ALIAS`: alias de la clave.
- `PREVAR_ANDROID_KEY_PASSWORD`: contraseña de la clave.

Con las cuatro variables presentes:

```bat
gradlew.bat assembleRelease
```

Sin esas variables, `assembleRelease` continúa generando un APK sin firmar para validación técnica de CI.

La clave privada y sus contraseñas deben permanecer bajo custodia de ASSI y fuera de Git, ZIP de distribución, chats y respaldos públicos.
