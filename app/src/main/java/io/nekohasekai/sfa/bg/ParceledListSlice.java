/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nekohasekai.sfa.bg;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

public class ParceledListSlice<T extends Parcelable> implements Parcelable {
    private static final int MAX_IPC_SIZE = 64 * 1024;

    private final List<T> mList;

    public ParceledListSlice(List<T> list) {
        mList = list;
    }

    private ParceledListSlice(Parcel in, ClassLoader loader) {
        final int n = in.readInt();
        mList = new ArrayList<>(n);
        if (n <= 0) {
            return;
        }

        int i = 0;
        while (i < n) {
            if (in.readInt() == 0) {
                break;
            }
            @SuppressWarnings("unchecked")
            T item = (T) in.readParcelable(loader);
            mList.add(item);
            i++;
        }
        if (i >= n) {
            return;
        }
        final IBinder retriever = in.readStrongBinder();
        while (i < n) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInt(i);
            try {
                retriever.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0);
            } catch (RemoteException e) {
                reply.recycle();
                data.recycle();
                return;
            }
            while (i < n && reply.readInt() != 0) {
                @SuppressWarnings("unchecked")
                T item = (T) reply.readParcelable(loader);
                mList.add(item);
                i++;
            }
            reply.recycle();
            data.recycle();
        }
    }

    public List<T> getList() {
        return mList;
    }

    @Override
    public int describeContents() {
        int contents = 0;
        for (int i = 0; i < mList.size(); i++) {
            contents |= mList.get(i).describeContents();
        }
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        final int n = mList.size();
        dest.writeInt(n);
        if (n <= 0) {
            return;
        }
        int i = 0;
        while (i < n && dest.dataSize() < MAX_IPC_SIZE) {
            dest.writeInt(1);
            dest.writeParcelable(mList.get(i), flags);
            i++;
        }
        if (i < n) {
            dest.writeInt(0);
            final int start = i;
            Binder retriever = new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                        throws RemoteException {
                    if (code != FIRST_CALL_TRANSACTION) {
                        return super.onTransact(code, data, reply, flags);
                    }
                    int i = data.readInt();
                    if (i < start || i > n) {
                        return false;
                    }
                    while (i < n && reply.dataSize() < MAX_IPC_SIZE) {
                        reply.writeInt(1);
                        reply.writeParcelable(mList.get(i), flags);
                        i++;
                    }
                    if (i < n) {
                        reply.writeInt(0);
                    }
                    return true;
                }
            };
            dest.writeStrongBinder(retriever);
        }
    }

    public static final Parcelable.ClassLoaderCreator<ParceledListSlice> CREATOR =
            new Parcelable.ClassLoaderCreator<ParceledListSlice>() {
                @Override
                public ParceledListSlice createFromParcel(Parcel in) {
                    return new ParceledListSlice(in, null);
                }

                @Override
                public ParceledListSlice createFromParcel(Parcel in, ClassLoader loader) {
                    return new ParceledListSlice(in, loader);
                }

                @Override
                public ParceledListSlice[] newArray(int size) {
                    return new ParceledListSlice[size];
                }
            };
}
