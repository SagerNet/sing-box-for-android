package io.nekohasekai.sfa.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, RemoteServer::class],
    version = 3,
    exportSchema = true,
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): Profile.Dao

    abstract fun remoteServerDao(): RemoteServer.Dao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Add icon column to profiles table with default value null
                    database.execSQL("ALTER TABLE profiles ADD COLUMN icon TEXT DEFAULT NULL")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS `remote_servers` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`userOrder` INTEGER NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`url` TEXT NOT NULL, " +
                            "`secret` TEXT NOT NULL)",
                    )
                }
            }
    }
}
