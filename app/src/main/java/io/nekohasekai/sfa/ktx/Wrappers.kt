package io.nekohasekai.sfa.ktx

import io.nekohasekai.libbox.StringIterator

fun StringIterator.toList(): List<String> {
    return mutableListOf<String>().apply {
        while (hasNext()) {
            add(next())
        }
    }
}