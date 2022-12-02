package io.nekohasekai.sfa.aidl;

interface IVPNServiceCallback {
  void onStatusChanged(int status);
  void alert(int type, String message);
  void writeLog(String message);
}