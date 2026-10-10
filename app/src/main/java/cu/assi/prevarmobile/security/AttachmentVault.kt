package cu.assi.prevarmobile.security

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class AttachmentVault(private val context: Context) {
    data class StoredFile(val file: File, val sizeBytes: Long, val sha256: String)

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        val alias = "PREVAR_MOBILE_ATTACHMENTS"
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun store(input: InputStream, destination: File, maxBytes: Long): StoredFile {
        destination.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        var total = 0L
        try {
            destination.outputStream().use { raw ->
                raw.write(cipher.iv)
                CipherOutputStream(raw, cipher).use { encrypted ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        total += count
                        require(total <= maxBytes) { "El adjunto supera el límite de 25 MB." }
                        digest.update(buffer, 0, count)
                        encrypted.write(buffer, 0, count)
                    }
                }
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
        return StoredFile(destination, total, digest.digest().joinToString("") { "%02x".format(it) })
    }

    fun storeFile(source: File, destination: File, maxBytes: Long): StoredFile =
        source.inputStream().use { store(it, destination, maxBytes) }

    fun decryptedUri(encryptedFile: File, displayName: String): Uri {
        require(encryptedFile.exists()) { "El adjunto local ya no existe." }
        cleanupCache()
        val cacheRoot = File(context.cacheDir, "prevar_attachment_cache").apply { mkdirs() }
        val target = File(cacheRoot, displayName)
        encryptedFile.inputStream().use { raw ->
            val iv = ByteArray(12)
            require(raw.read(iv) == iv.size) { "Adjunto cifrado inválido." }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            CipherInputStream(raw, cipher).use { decrypted ->
                target.outputStream().use { output -> decrypted.copyTo(output) }
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }

    private fun cleanupCache() {
        val root = File(context.cacheDir, "prevar_attachment_cache")
        val cutoff = System.currentTimeMillis() - 10 * 60 * 1000L
        root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}
