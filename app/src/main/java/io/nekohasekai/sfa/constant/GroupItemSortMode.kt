package io.nekohasekai.sfa.constant

import androidx.annotation.DrawableRes
import io.nekohasekai.sfa.R

enum class GroupItemSortMode(@DrawableRes val drawableId: Int) {
    DEFAULT(R.drawable.ic_sort_default),
    DELAY_ASC(R.drawable.ic_sort_delay_asc),
    DELAY_DESC(R.drawable.ic_sort_delay_desc),
    TAG_ASC(R.drawable.ic_sort_tag_asc),
    TAG_DESC(R.drawable.ic_sort_tag_desc);

    companion object {
        fun from(value: Int): GroupItemSortMode = when (value) {
            DEFAULT.ordinal -> DEFAULT
            DELAY_ASC.ordinal -> DELAY_ASC
            DELAY_DESC.ordinal -> DELAY_DESC
            TAG_ASC.ordinal -> TAG_ASC
            TAG_DESC.ordinal -> TAG_DESC
            else -> throw IllegalArgumentException()
        }
    }

    fun next(): GroupItemSortMode {
        val nextIndex = (ordinal + 1) % values().size
        return values()[nextIndex]
    }
}