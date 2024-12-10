package io.nekohasekai.sfa.ktx

import android.net.IpPrefix
import android.os.Build
import androidx.annotation.RequiresApi
import io.nekohasekai.libbox.RoutePrefix
import io.nekohasekai.libbox.StringBox
import io.nekohasekai.libbox.StringIterator
import java.net.InetAddress

val StringBox?.unwrap: String
    get() {
        if (this == null) return ""
        return value
    }

fun Iterable<String>.toStringIterator(): StringIterator {
    return object : StringIterator {
        val iterator = iterator()

        override fun len(): Int {
            // not used by core
            return 0
        }

        override fun hasNext(): Boolean {
            return iterator.hasNext()
        }

        override fun next(): String {
            return iterator.next()
        }
    }
}

fun StringIterator.toList(): List<String> {
    return mutableListOf<String>().apply {
        while (hasNext()) {
            add(next())
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun RoutePrefix.toIpPrefix() = IpPrefix(InetAddress.getByName(address()), prefix())