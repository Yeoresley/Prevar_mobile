package cu.assi.prevarmobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cu.assi.prevarmobile.security.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        QuestionnaireEntity::class,
        EvaluationEntity::class,
        MeasureEntity::class,
        WorkerEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
        AttachmentEntity::class,
        RiskDraftEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PrevarDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyProvider(context).databasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            Room.databaseBuilder(context, AppDatabase::class.java, "prevar_mobile.db")
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
