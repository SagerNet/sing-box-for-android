package io.nekohasekai.sfa.ktx

import android.os.Parcel
import android.os.Parcelable

fun Parcelable.marshall(): ByteArray {
    val parcel = Parcel.obtain()
    writeToParcel(parcel, 0)
    val content = parcel.marshall()
    parcel.recycle()
    return content
}

fun <T> ByteArray.unmarshall(constructor: (Parcel) -> T): T {
    val parcel = Parcel.obtain()
    parcel.unmarshall(this, 0, size)
    parcel.setDataPosition(0) // This is extremely important!
    val result = constructor(parcel)
    parcel.recycle()
    return result
}