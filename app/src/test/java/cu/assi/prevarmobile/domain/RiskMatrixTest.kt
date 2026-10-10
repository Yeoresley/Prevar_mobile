package cu.assi.prevarmobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RiskMatrixTest {
    @Test fun lowRisk() {
        val a = RiskMatrix.assess(1, 2)
        assertEquals(2, a.result)
        assertEquals(RiskMatrix.LEVEL_LOW, a.level)
    }

    @Test fun mediumRisk() {
        val a = RiskMatrix.assess(2, 2)
        assertEquals(4, a.result)
        assertEquals(RiskMatrix.LEVEL_MEDIUM, a.level)
    }

    @Test fun highRisk() {
        val a = RiskMatrix.assess(2, 3)
        assertEquals(6, a.result)
        assertEquals(RiskMatrix.LEVEL_HIGH, a.level)
    }
}
