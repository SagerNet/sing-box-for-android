package io.nekohasekai.sfa.bg;

import android.os.ParcelFileDescriptor;

interface IRootShellSession {
    ParcelFileDescriptor getMasterFD();
    void resize(int rows, int cols);
    void signal(int sig);
    int waitFor();
    void close();
}
