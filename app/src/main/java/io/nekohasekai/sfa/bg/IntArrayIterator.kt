package io.nekohasekai.sfa.bg

import io.nekohasekai.libbox.Int32Iterator

class IntArrayIterator(private val array: IntArray) : Int32Iterator {
    private var index = 0

    override fun len(): Int = array.size

    override fun hasNext(): Boolean = index < array.size

    override fun next(): Int = array[index++]
}
