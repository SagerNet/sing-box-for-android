package io.nekohasekai.sfa.database.preference

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

@Entity
class KeyValueEntity() : Parcelable {
    companion object {
        const val TYPE_UNINITIALIZED = 0
        const val TYPE_BOOLEAN = 1
        const val TYPE_FLOAT = 2
        const val TYPE_LONG = 3
        const val TYPE_STRING = 4
        const val TYPE_STRING_SET = 5

        @JvmField
        val CREATOR = object : Parcelable.Creator<KeyValueEntity> {
            override fun createFromParcel(parcel: Parcel): KeyValueEntity {
                return KeyValueEntity(parcel)
            }

            override fun newArray(size: Int): Array<KeyValueEntity?> {
                return arrayOfNulls(size)
            }
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * FROM KeyValueEntity")
        fun all(): List<KeyValueEntity>

        @Query("SELECT * FROM KeyValueEntity WHERE `key` = :key")
        operator fun get(key: String): KeyValueEntity?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun put(value: KeyValueEntity): Long

        @Query("DELETE FROM KeyValueEntity WHERE `key` = :key")
        fun delete(key: String): Int

        @Query("DELETE FROM KeyValueEntity")
        fun reset(): Int

        @Insert
        fun insert(list: List<KeyValueEntity>)
    }

    @PrimaryKey
    var key: String = ""
    var valueType: Int = TYPE_UNINITIALIZED
    var value: ByteArray = ByteArray(0)

    val boolean: Boolean?
        get() = if (valueType == TYPE_BOOLEAN) ByteBuffer.wrap(value).get() != 0.toByte() else null
    val float: Float?
        get() = if (valueType == TYPE_FLOAT) ByteBuffer.wrap(value).float else null

    val long: Long
        get() = ByteBuffer.wrap(value).long

    val string: String?
        get() = if (valueType == TYPE_STRING) String(value) else null
    val stringSet: Set<String>?
        get() = if (valueType == TYPE_STRING_SET) {
            val buffer = ByteBuffer.wrap(value)
            val result = HashSet<String>()
            while (buffer.hasRemaining()) {
                val chArr = ByteArray(buffer.int)
                buffer.get(chArr)
                result.add(String(chArr))
            }
            result
        } else null

    @Ignore
    constructor(key: String) : this() {
        this.key = key
    }

    // putting null requires using DataStore
    fun put(value: Boolean): KeyValueEntity {
        valueType = TYPE_BOOLEAN
        this.value = ByteBuffer.allocate(1).put((if (value) 1 else 0).toByte()).array()
        return this
    }

    fun put(value: Float): KeyValueEntity {
        valueType = TYPE_FLOAT
        this.value = ByteBuffer.allocate(4).putFloat(value).array()
        return this
    }

    fun put(value: Long): KeyValueEntity {
        valueType = TYPE_LONG
        this.value = ByteBuffer.allocate(8).putLong(value).array()
        return this
    }

    fun put(value: String): KeyValueEntity {
        valueType = TYPE_STRING
        this.value = value.toByteArray()
        return this
    }

    fun put(value: Set<String>): KeyValueEntity {
        valueType = TYPE_STRING_SET
        val stream = ByteArrayOutputStream()
        val intBuffer = ByteBuffer.allocate(4)
        for (v in value) {
            intBuffer.rewind()
            stream.write(intBuffer.putInt(v.length).array())
            stream.write(v.toByteArray())
        }
        this.value = stream.toByteArray()
        return this
    }

    @Suppress("IMPLICIT_CAST_TO_ANY")
    override fun toString(): String {
        return when (valueType) {
            TYPE_BOOLEAN -> boolean
            TYPE_FLOAT -> float
            TYPE_LONG -> long
            TYPE_STRING -> string
            TYPE_STRING_SET -> stringSet
            else -> null
        }?.toString() ?: "null"
    }

    constructor(parcel: Parcel) : this() {
        key = parcel.readString()!!
        valueType = parcel.readInt()
        value = parcel.createByteArray()!!
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(key)
        parcel.writeInt(valueType)
        parcel.writeByteArray(value)
    }

    override fun describeContents(): Int {
        return 0
    }

}
