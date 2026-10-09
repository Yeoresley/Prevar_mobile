package cu.assi.prevarmobile.domain

object EvaluationValidator {
    const val RESULT_COMPLIES = 1
    const val RESULT_NOT_COMPLIES = 2
    const val RESULT_PENDING = 3

    data class Input(
        val result: Int,
        val fundamentation: String,
        val causes: String,
        val measureCount: Int
    )

    fun validate(input: Input, minChars: Int): List<String> {
        val errors = mutableListOf<String>()
        if (input.result !in setOf(RESULT_COMPLIES, RESULT_NOT_COMPLIES, RESULT_PENDING)) {
            errors += "Seleccione Cumple, No cumple o Pendiente."
            return errors
        }
        if (input.result == RESULT_PENDING) return errors

        validateText(input.fundamentation, minChars, "La fundamentación", errors)
        if (input.result == RESULT_NOT_COMPLIES) {
            validateText(input.causes, minChars, "Las causas y condiciones", errors)
            if (input.measureCount <= 0) {
                errors += "Una evaluación No cumple debe tener al menos una medida o acción asociada."
            }
        }
        return errors
    }

    private fun validateText(value: String, minChars: Int, label: String, errors: MutableList<String>) {
        val text = value.trim()
        if (text.length < minChars) errors += "$label debe contener al menos $minChars caracteres."
        if (text.contains("XX") || text.contains("xx")) {
            errors += "$label contiene marcadores XX/xx que deben sustituirse por valores reales."
        }
    }
}
