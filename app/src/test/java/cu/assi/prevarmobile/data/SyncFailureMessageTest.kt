package cu.assi.prevarmobile.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncFailureMessageTest {
    @Test
    fun ioFailureReportsOfflineSafeState() {
        assertEquals(
            "Sin conexión con PREVAR. Los cambios quedan guardados en el teléfono y se enviarán cuando vuelva la conectividad.",
            syncFailureMessage(IOException("timeout"))
        )
    }

    @Test
    fun nonIoFailureKeepsOriginalMessage() {
        assertEquals("fallo lógico", syncFailureMessage(IllegalStateException("fallo lógico")))
    }
}
