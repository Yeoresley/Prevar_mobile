package cu.assi.prevarmobile.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationValidatorTest {
    @Test fun pendingNeedsNothing() {
        assertTrue(EvaluationValidator.validate(EvaluationValidator.Input(3, "", "", 0), 100).isEmpty())
    }

    @Test fun positiveNeedsFundamentation() {
        val errors = EvaluationValidator.validate(EvaluationValidator.Input(1, "corto", "", 0), 10)
        assertTrue(errors.isNotEmpty())
    }

    @Test fun negativeNeedsCausesAndMeasure() {
        val text = "1234567890"
        val errors = EvaluationValidator.validate(EvaluationValidator.Input(2, text, text, 0), 10)
        assertTrue(errors.any { it.contains("medida") })
    }

    @Test fun markersAreRejected() {
        val text = "Fundamentación válida pero con XX pendiente"
        val errors = EvaluationValidator.validate(EvaluationValidator.Input(1, text, "", 0), 10)
        assertTrue(errors.any { it.contains("XX/xx") })
    }
}
