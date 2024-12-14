package io.nekohasekai.sfa.database

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.room.TypeConverter
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.TypedProfile.Type.values
import io.nekohasekai.sfa.ktx.marshall
import io.nekohasekai.sfa.ktx.unmarshall
import java.util.Date

class TypedProfile() : Parcelable {

    enum class Type {
        Local, Remote;

        fun getString(context: Context): String {
            return when (this) {
                Local -> context.getString(R.string.profile_type_local)
                Remote -> context.getString(R.string.profile_type_remote)
            }
        }

        companion object {
            fun valueOf(value: Int): Type {
                for (it in values()) {
                    if (it.ordinal == value) {
                        return it
                    }
                }
                return Local
            }
        }
    }

    var path = ""
    var type = Type.Local
    var remoteURL: String = ""
    var lastUpdated: Date = Date(0)
    var autoUpdate: Boolean = false
    var autoUpdateInterval = 60

    constructor(reader: Parcel) : this() {
        val version = reader.readInt()
        path = reader.readString() ?: ""
        type = Type.valueOf(reader.readInt())
        remoteURL = reader.readString() ?: ""
        autoUpdate = reader.readInt() == 1
        lastUpdated = Date(reader.readLong())
        if (version >= 1) {
            autoUpdateInterval = reader.readInt()
        }
    }

    override fun writeToParcel(writer: Parcel, flags: Int) {
        writer.writeInt(1)
        writer.writeString(path)
        writer.writeInt(type.ordinal)
        writer.writeString(remoteURL)
        writer.writeInt(if (autoUpdate) 1 else 0)
        writer.writeLong(lastUpdated.time)
        writer.writeInt(autoUpdateInterval)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TypedProfile> {
        override fun createFromParcel(parcel: Parcel): TypedProfile {
            return TypedProfile(parcel)
        }

        override fun newArray(size: Int): Array<TypedProfile?> {
            return arrayOfNulls(size)
        }
    }

    class Convertor {

        @TypeConverter
        fun marshall(profile: TypedProfile) = profile.marshall()

        @TypeConverter
        fun unmarshall(content: ByteArray) =
            content.unmarshall(::TypedProfile)

    }

}