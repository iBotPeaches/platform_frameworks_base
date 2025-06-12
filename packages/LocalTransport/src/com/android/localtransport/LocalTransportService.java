/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.localtransport;

import static com.android.localtransport.LocalTransport.DEBUG;
import static com.android.localtransport.LocalTransport.TAG;

import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class LocalTransportService extends Service {

    @Nullable
    private static LocalTransport sTransport;

    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(TAG, "LocalTransportService.onCreate()");
        }
        if (sTransport == null) {
            LocalTransportParameters parameters =
                    new LocalTransportParameters(getMainThreadHandler(), getContentResolver());
            sTransport = new LocalTransport(this, parameters);
        }
        sTransport.getParameters().start();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "LocalTransportService.onDestroy()");
        }
        sTransport.getParameters().stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "LocalTransportService.onBind(" + intent + "): parameters="
                    + sTransport.getParameters());
        }
        return sTransport.getBinder();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (sTransport == null) {
            pw.println("No sTransport");
            return;
        }
        sTransport.dump(pw, args);
    }
}
