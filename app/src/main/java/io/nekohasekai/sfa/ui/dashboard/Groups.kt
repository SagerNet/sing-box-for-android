package io.nekohasekai.sfa.ui.dashboard

import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.OutboundGroupItem
import io.nekohasekai.libbox.OutboundGroupItemIterator

data class Group(
    val tag: String,
    val type: String,
    val selectable: Boolean,
    var selected: String,
    var isExpand: Boolean,
    var items: List<GroupItem>,
) {
    constructor(item: OutboundGroup) : this(
        item.tag,
        item.type,
        item.selectable,
        item.selected,
        item.isExpand,
        item.items.toList().map { GroupItem(it) },
    )
}

data class GroupItem(
    val tag: String,
    val type: String,
    val urlTestTime: Long,
    val urlTestDelay: Int,
) {
    constructor(item: OutboundGroupItem) : this(
        item.tag,
        item.type,
        item.urlTestTime,
        item.urlTestDelay,
    )
}

internal fun OutboundGroupItemIterator.toList(): List<OutboundGroupItem> {
    val list = mutableListOf<OutboundGroupItem>()
    while (hasNext()) {
        list.add(next())
    }
    return list
}