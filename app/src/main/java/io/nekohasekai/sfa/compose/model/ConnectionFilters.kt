package io.nekohasekai.sfa.compose.model

import libbox.Libbox

enum class ConnectionStateFilter(val libboxValue: Int) {
    All(Libbox.ConnectionStateAll.toInt()),
    Active(Libbox.ConnectionStateActive.toInt()),
    Closed(Libbox.ConnectionStateClosed.toInt()),
}

enum class ConnectionSort {
    ByDate,
    ByTraffic,
    ByTrafficTotal,
}
