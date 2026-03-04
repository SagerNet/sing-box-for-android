package io.nekohasekai.sfa.bg;

import io.nekohasekai.sfa.bg.ParceledListSlice;

interface INeighborTableCallback {
    oneway void onNeighborTableUpdated(in ParceledListSlice entries);
}
