package cu.assi.prevarmobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PrevarDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS attachment (
                        localUuid TEXT NOT NULL PRIMARY KEY,
                        evaluationId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        sha256 TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS risk_draft (
                        localUuid TEXT NOT NULL PRIMARY KEY,
                        evaluationId INTEGER NOT NULL,
                        objectText TEXT NOT NULL,
                        riskText TEXT NOT NULL,
                        probability INTEGER NOT NULL,
                        impact INTEGER NOT NULL,
                        result INTEGER NOT NULL,
                        level INTEGER NOT NULL,
                        argument TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachment ADD COLUMN serverVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachment ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE attachment ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE risk_draft ADD COLUMN serverVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE risk_draft ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE risk_draft ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyProvider(context).databasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            Room.databaseBuilder(context, AppDatabase::class.java, "prevar_mobile.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
        }
    }
}
