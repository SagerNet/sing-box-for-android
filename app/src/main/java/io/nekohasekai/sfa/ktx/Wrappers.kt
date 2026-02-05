package io.nekohasekai.sfa.ktx

import android.net.IpPrefix
import android.os.Build
import androidx.annotation.RequiresApi
import libbox.ConnectionIterator
import libbox.LogEntry
import libbox.LogIterator
import libbox.RoutePrefix
import libbox.StringBox
import libbox.StringIterator
import java.net.InetAddress
import libbox.Connection as LibboxConnection

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

        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): String = iterator.next()
    }
}

fun StringIterator.toList(): List<String> = mutableListOf<String>().apply {
    while (hasNext()) {
        add(next())
    }
}

fun LogIterator.toList(): List<LogEntry> = mutableListOf<LogEntry>().apply {
    while (hasNext()) {
        add(next())
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun RoutePrefix.toIpPrefix() = IpPrefix(InetAddress.getByName(address()), prefix())

fun ConnectionIterator.toList(): List<LibboxConnection> = mutableListOf<LibboxConnection>().apply {
    while (hasNext()) {
        add(next())
    }
}
