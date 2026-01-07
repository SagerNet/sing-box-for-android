package io.nekohasekai.sfa.bg;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LogEntry implements Parcelable {
    public static final int LEVEL_DEBUG = 0;
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_WARN = 2;
    public static final int LEVEL_ERROR = 3;

    public final int level;
    public final long timestamp;
    @NonNull
    public final String source;
    @NonNull
    public final String message;
    @Nullable
    public final String stackTrace;

    public LogEntry(int level, long timestamp, @NonNull String source, @NonNull String message, @Nullable String stackTrace) {
        this.level = level;
        this.timestamp = timestamp;
        this.source = source;
        this.message = message;
        this.stackTrace = stackTrace;
    }

    protected LogEntry(Parcel in) {
        level = in.readInt();
        timestamp = in.readLong();
        source = in.readString();
        message = in.readString();
        stackTrace = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(level);
        dest.writeLong(timestamp);
        dest.writeString(source);
        dest.writeString(message);
        dest.writeString(stackTrace);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<LogEntry> CREATOR = new Creator<>() {
        @Override
        public LogEntry createFromParcel(Parcel in) {
            return new LogEntry(in);
        }

        @Override
        public LogEntry[] newArray(int size) {
            return new LogEntry[size];
        }
    };
}
