package cu.assi.prevarmobile.security

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class TlsMaterial(
    val socketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager
)

object PinnedCaTrust {
    private const val MAX_CA_BYTES = 128 * 1024

    fun normalizeFingerprint(raw: String): String {
        val value = raw.trim().lowercase().replace(Regex("[^0-9a-f]"), "")
        require(value.length == 64 && value.matches(Regex("[0-9a-f]{64}"))) {
            "La huella SHA-256 del certificado PREVAR no es válida."
        }
        return value
    }

    fun verifyCaBytes(bytes: ByteArray, expectedSha256: String): ByteArray {
        require(bytes.isNotEmpty() && bytes.size <= MAX_CA_BYTES) {
            "El certificado PREVAR recibido no tiene un tamaño válido."
        }

        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        require(certificate.basicConstraints >= 0) {
            "El certificado recibido no corresponde a una autoridad certificadora."
        }
        certificate.checkValidity()

        val der = certificate.encoded
        val actual = MessageDigest.getInstance("SHA-256").digest(der)
        val expected = hexToBytes(normalizeFingerprint(expectedSha256))
        require(MessageDigest.isEqual(actual, expected)) {
            "La identidad TLS de PREVAR no coincide con el código QR."
        }
        return der
    }

    fun downloadVerifiedCa(apiUrl: String, caUrl: String, expectedSha256: String): ByteArray {
        val api = URI(apiUrl)
        val ca = URI(caUrl)
        require(api.scheme.equals("https", ignoreCase = true)) {
            "PREVAR Mobile requiere HTTPS para vinculación segura."
        }
        require(ca.scheme.equals("http", ignoreCase = true) || ca.scheme.equals("https", ignoreCase = true)) {
            "La dirección del certificado PREVAR no es válida."
        }
        require(!api.host.isNullOrBlank() && api.host.equals(ca.host, ignoreCase = true)) {
            "El certificado PREVAR debe descargarse del mismo servidor indicado por el QR."
        }
        require(ca.userInfo.isNullOrBlank()) {
            "La dirección del certificado PREVAR no puede contener credenciales."
        }

        val connection = ca.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/x-pem-file, application/pkix-cert, */*")
        try {
            val code = connection.responseCode
            require(code == HttpURLConnection.HTTP_OK) {
                "PREVAR no pudo entregar su certificado TLS (HTTP $code)."
            }
            val declared = connection.contentLengthLong
            require(declared <= 0 || declared <= MAX_CA_BYTES) {
                "El certificado PREVAR excede el tamaño permitido."
            }
            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                val output = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_CA_BYTES) {
                        "El certificado PREVAR excede el tamaño permitido."
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            return verifyCaBytes(bytes, expectedSha256)
        } finally {
            connection.disconnect()
        }
    }

    fun materialFromDer(der: ByteArray): TlsMaterial {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        require(certificate.basicConstraints >= 0) {
            "La autoridad certificadora PREVAR almacenada no es válida."
        }
        certificate.checkValidity()

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("prevar-server-ca", certificate)
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        val trustManager = tmf.trustManagers.filterIsInstance<X509TrustManager>().single()
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        return TlsMaterial(context.socketFactory, trustManager)
    }

    private fun hexToBytes(value: String): ByteArray =
        ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}
