package cu.assi.prevarmobile.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictResolutionTest {
    @Test
    fun rebasePreservesMobileFieldsAndUpdatesVersion() {
        val original = """{
          "uuid":"11111111-1111-1111-1111-111111111111",
          "kind":"evaluation.update",
          "evaluation_id":42,
          "base_version":3,
          "fields":{"evalua":2,"com":"respuesta móvil","causas":"causa móvil"}
        }""".trimIndent()

        val gson = Gson()
        val rebased = gson.fromJson(rebaseEvaluationOperation(original, 9, gson), JsonObject::class.java)

        assertEquals(9L, rebased.get("base_version").asLong)
        assertEquals(42L, rebased.get("evaluation_id").asLong)
        assertEquals("respuesta móvil", rebased.getAsJsonObject("fields").get("com").asString)
        assertEquals("causa móvil", rebased.getAsJsonObject("fields").get("causas").asString)
    }
}
