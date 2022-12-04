package io.nekohasekai.sfa.aidl;

interface IServiceCallback {
  void onStatusChanged(int status);
  void alert(int type, String message);
  void writeLog(String message);
  void resetLogs(in List<String> messages);
}