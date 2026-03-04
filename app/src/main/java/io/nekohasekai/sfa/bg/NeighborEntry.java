package io.nekohasekai.sfa.bg;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

public class NeighborEntry implements Parcelable {
  @NonNull public final String address;
  @NonNull public final String macAddress;
  @NonNull public final String hostname;

  public NeighborEntry(@NonNull String address, @NonNull String macAddress, @NonNull String hostname) {
    this.address = address;
    this.macAddress = macAddress;
    this.hostname = hostname;
  }

  protected NeighborEntry(Parcel in) {
    address = in.readString();
    macAddress = in.readString();
    hostname = in.readString();
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeString(address);
    dest.writeString(macAddress);
    dest.writeString(hostname);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<NeighborEntry> CREATOR =
      new Creator<>() {
        @Override
        public NeighborEntry createFromParcel(Parcel in) {
          return new NeighborEntry(in);
        }

        @Override
        public NeighborEntry[] newArray(int size) {
          return new NeighborEntry[size];
        }
      };
}
