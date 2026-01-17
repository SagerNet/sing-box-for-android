package io.nekohasekai.sfa.bg;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

public class PackageEntry implements Parcelable {
  @NonNull public final String packageName;

  public PackageEntry(@NonNull String packageName) {
    this.packageName = packageName;
  }

  protected PackageEntry(Parcel in) {
    packageName = in.readString();
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeString(packageName);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<PackageEntry> CREATOR =
      new Creator<>() {
        @Override
        public PackageEntry createFromParcel(Parcel in) {
          return new PackageEntry(in);
        }

        @Override
        public PackageEntry[] newArray(int size) {
          return new PackageEntry[size];
        }
      };
}
