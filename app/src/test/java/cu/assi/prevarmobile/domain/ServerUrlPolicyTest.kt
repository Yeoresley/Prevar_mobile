package cu.assi.prevarmobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlPolicyTest {
    @Test fun httpsAlwaysAllowed() {
        assertEquals("https://prevar.example/mobile-api/v1", ServerUrlPolicy.normalize("https://prevar.example/mobile-api/v1/", false))
    }

    @Test fun privateHttpAllowedOnlyForDebugPolicy() {
        assertEquals("http://192.168.1.20/mobile-api/v1", ServerUrlPolicy.normalize("http://192.168.1.20/mobile-api/v1", true))
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlPolicy.normalize("http://192.168.1.20/mobile-api/v1", false)
        }
    }

    @Test fun publicHttpIsRejectedEvenInDebug() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlPolicy.normalize("http://example.com/mobile-api/v1", true)
        }
    }

    @Test fun embeddedCredentialsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlPolicy.normalize("https://admin:secret@example.com/mobile-api/v1", false)
        }
    }
}
