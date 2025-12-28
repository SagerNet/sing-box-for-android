package io.nekohasekai.sfa.ui.connections

import io.nekohasekai.libbox.Libbox

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
