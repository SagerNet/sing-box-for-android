package io.nekohasekai.sfa.database

import android.os.Parcel
import android.os.Parcelable
import androidx.room.TypeConverter
import io.nekohasekai.sfa.ktx.marshall
import io.nekohasekai.sfa.ktx.unmarshall

class TypedProfile() : Parcelable {

    var content = ""

    constructor(reader: Parcel) : this() {
        val version = reader.readInt()
        content = reader.readString() ?: ""
    }

    override fun writeToParcel(writer: Parcel, flags: Int) {
        writer.writeInt(0)
        writer.writeString(content)
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