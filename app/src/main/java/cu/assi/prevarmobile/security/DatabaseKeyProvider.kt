package cu.assi.prevarmobile.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

class DatabaseKeyProvider(context: Context) {
    private val prefs = context.getSharedPreferences("prevar_db_security", Context.MODE_PRIVATE)
    private val box = KeystoreBox("PREVAR_MOBILE_DB_KEY")

    fun databasePassphrase(): ByteArray {
        val stored = prefs.getString("db_passphrase", null)
        if (stored != null) return box.decrypt(stored)
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("db_passphrase", box.encrypt(raw)).apply()
        return raw
    }
}
