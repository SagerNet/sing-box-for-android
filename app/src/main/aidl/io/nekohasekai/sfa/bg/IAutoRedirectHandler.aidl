package io.nekohasekai.sfa.bg;

import android.os.ParcelFileDescriptor;

interface IAutoRedirectHandler {
    int judgeFlow(int ipProtocol, String sourceAddress, int sourcePort, String destinationAddress, int destinationPort, in byte[] firstPacket);
    oneway void writeLog(int level, String message);
    ParcelFileDescriptor getRedirectListener();
    ParcelFileDescriptor getRouteAddressSet();
}
