package io.nekohasekai.sfa.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class],
    version = 2,
    exportSchema = true,
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): Profile.Dao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Add icon column to profiles table with default value null
                    database.execSQL("ALTER TABLE profiles ADD COLUMN icon TEXT DEFAULT NULL")
                }
            }
    }
}
