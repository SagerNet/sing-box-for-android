package io.nekohasekai.sfa.compose.model

import androidx.compose.runtime.Immutable
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.OutboundGroupItem
import io.nekohasekai.libbox.OutboundGroupItemIterator

@Immutable
data class Group(
    val tag: String,
    val type: String,
    val displayType: String,
    val selectable: Boolean,
    val selected: String,
    val isExpand: Boolean,
    val items: List<GroupItem>,
) {
    constructor(item: OutboundGroup) : this(
        item.tag,
        item.type,
        Libbox.proxyDisplayType(item.type),
        item.selectable,
        item.selected,
        item.isExpand,
        item.items.toList().map { GroupItem(it) },
    )
}

@Immutable
data class GroupItem(
    val tag: String,
    val type: String,
    val displayType: String,
    val urlTestTime: Long,
    val urlTestDelay: Int,
) {
    constructor(item: OutboundGroupItem) : this(
        item.tag,
        item.type,
        Libbox.proxyDisplayType(item.type),
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
