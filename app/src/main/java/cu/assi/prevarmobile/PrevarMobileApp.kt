package cu.assi.prevarmobile

import android.app.Application
import com.google.gson.Gson
import cu.assi.prevarmobile.data.PrevarRepository
import cu.assi.prevarmobile.data.local.AppDatabase
import cu.assi.prevarmobile.data.remote.ApiFactory
import cu.assi.prevarmobile.security.SecureStore

class PrevarMobileApp : Application() {
    lateinit var store: SecureStore
        private set
    lateinit var repository: PrevarRepository
        private set

    override fun onCreate() {
        super.onCreate()
        store = SecureStore(this)
        val database = AppDatabase.get(this)
        repository = PrevarRepository(database.dao(), store, ApiFactory(store), Gson())
    }
}
