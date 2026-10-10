package cu.assi.prevarmobile.domain

object AttachmentPolicy {
    const val MAX_BYTES = 25L * 1024L * 1024L

    fun validateMime(mimeType: String) {
        require(mimeType.startsWith("image/") || mimeType == "application/pdf") {
            "Solo se admiten imágenes y documentos PDF."
        }
    }

    fun validateSize(sizeBytes: Long) {
        require(sizeBytes in 0..MAX_BYTES) { "El adjunto supera el límite de 25 MB." }
    }

    fun sanitizeFileName(value: String): String {
        val base = value.replace('\\', '/').substringAfterLast('/')
        val cleaned = base
            .replace(Regex("[^A-Za-z0-9._áéíóúÁÉÍÓÚñÑ -]"), "_")
            .trim()
            .trim('.')
        return cleaned.take(120).ifBlank { "adjunto" }
    }
}
