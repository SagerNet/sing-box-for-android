package io.nekohasekai.sfa.bg;

import android.os.ParcelFileDescriptor;

interface IBridgeSession {
    ParcelFileDescriptor getFileDescriptor();
    String getName();
    boolean isInet6Active();
    void setEgress(String interfaceName);
    void close();
}
