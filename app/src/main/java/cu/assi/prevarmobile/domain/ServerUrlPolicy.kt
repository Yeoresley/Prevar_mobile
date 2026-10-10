package cu.assi.prevarmobile.domain

import java.net.URI

object ServerUrlPolicy {
    fun normalize(raw: String, allowPrivateHttp: Boolean): String {
        val trimmed = raw.trim().trimEnd('/')
        val uri = runCatching { URI(trimmed) }.getOrElse { throw IllegalArgumentException("Dirección del servidor no válida.") }
        require(uri.userInfo.isNullOrBlank()) { "La dirección del servidor no puede incluir credenciales." }
        require(uri.fragment.isNullOrBlank()) { "La dirección del servidor no puede incluir fragmentos." }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("La dirección del servidor no contiene un host válido.")
        return when (uri.scheme?.lowercase()) {
            "https" -> trimmed
            "http" -> {
                require(allowPrivateHttp && isPrivateHost(host)) {
                    "PREVAR Mobile exige HTTPS. HTTP solo se admite en compilaciones de depuración dentro de una LAN privada."
                }
                trimmed
            }
            else -> throw IllegalArgumentException("La dirección del servidor debe usar HTTPS.")
        }
    }

    internal fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host.endsWith(".local")) return true
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }
}
