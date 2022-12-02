package io.nekohasekai.sfa.db.preference

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [KeyValueEntity::class], version = 1
)
abstract class KeyValueDatabase : RoomDatabase() {

    abstract fun keyValuePairDao(): KeyValueEntity.Dao

}
