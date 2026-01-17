package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IPackageInstaller extends IInterface {

  int createSession(
      PackageInstaller.SessionParams params,
      String installerPackageName,
      String installerAttributionTag,
      int userId)
      throws RemoteException;

  IPackageInstallerSession openSession(int sessionId) throws RemoteException;

  void abandonSession(int sessionId) throws RemoteException;

  abstract class Stub extends Binder implements IPackageInstaller {
    public static IPackageInstaller asInterface(IBinder binder) {
      throw new UnsupportedOperationException();
    }
  }
}
