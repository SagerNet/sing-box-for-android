package io.nekohasekai.sfa.aidl;

import io.nekohasekai.sfa.aidl.IVPNServiceCallback;

interface IVPNService {
  int getStatus();
  void registerCallback(in IVPNServiceCallback callback);
  oneway void unregisterCallback(in IVPNServiceCallback callback);
}