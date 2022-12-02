package io.nekohasekai.sfa.aidl;

import io.nekohasekai.sfa.aidl.IServiceCallback;

interface IService {
  int getStatus();
  void registerCallback(in IServiceCallback callback);
  oneway void unregisterCallback(in IServiceCallback callback);
}