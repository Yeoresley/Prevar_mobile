package cu.assi.prevarmobile.security

import android.content.Context
import java.util.UUID

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("prevar_mobile_secure", Context.MODE_PRIVATE)
    private val box = KeystoreBox("PREVAR_MOBILE_SECRETS")

    val deviceUuid: String
        get() {
            val current = prefs.getString("device_uuid", null)
            if (!current.isNullOrBlank()) return current
            return UUID.randomUUID().toString().also { prefs.edit().putString("device_uuid", it).apply() }
        }

    var apiUrl: String?
        get() = prefs.getString("api_url", null)
        set(value) = prefs.edit().putString("api_url", value?.trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString("token_enc", null)?.let { runCatching { String(box.decrypt(it), Charsets.UTF_8) }.getOrNull() }
        set(value) {
            val editor = prefs.edit()
            if (value.isNullOrBlank()) editor.remove("token_enc")
            else editor.putString("token_enc", box.encrypt(value.toByteArray(Charsets.UTF_8)))
            editor.apply()
        }

    var userName: String?
        get() = prefs.getString("user_name", null)
        set(value) = prefs.edit().putString("user_name", value).apply()

    var unitId: Long
        get() = prefs.getLong("unit_id", 0L)
        set(value) = prefs.edit().putLong("unit_id", value).apply()

    fun clearPairing() {
        prefs.edit().remove("api_url").remove("token_enc").remove("user_name").remove("unit_id").apply()
    }
}
