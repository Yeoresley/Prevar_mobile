package cu.assi.prevarmobile.domain

object RiskMatrix {
    const val LEVEL_LOW = 1
    const val LEVEL_MEDIUM = 2
    const val LEVEL_HIGH = 3

    data class Assessment(val result: Int, val level: Int)

    fun assess(probability: Int, impact: Int): Assessment {
        require(probability in 1..3) { "La probabilidad debe estar entre 1 y 3." }
        require(impact in 1..3) { "El impacto debe estar entre 1 y 3." }
        val result = probability * impact
        val level = when {
            result <= 2 -> LEVEL_LOW
            result <= 4 -> LEVEL_MEDIUM
            else -> LEVEL_HIGH
        }
        return Assessment(result, level)
    }

    fun levelLabel(level: Int): String = when (level) {
        LEVEL_LOW -> "BAJO"
        LEVEL_MEDIUM -> "MEDIO"
        LEVEL_HIGH -> "ALTO"
        else -> "DESCONOCIDO"
    }
}
