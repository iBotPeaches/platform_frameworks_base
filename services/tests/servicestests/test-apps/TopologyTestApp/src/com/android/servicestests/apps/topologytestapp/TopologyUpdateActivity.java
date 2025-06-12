/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.servicestests.apps.topologytestapp;

import android.app.Activity;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayTopology;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.util.function.Consumer;

/**
 * A simple activity listening to topology updates
 */
public class TopologyUpdateActivity extends Activity {
    public static final int MESSAGE_LAUNCHED = 1;
    public static final int MESSAGE_CALLBACK = 2;

    private static final String TAG = TopologyUpdateActivity.class.getSimpleName();

    private static final String TEST_MESSENGER = "MESSENGER";

    private Messenger mMessenger;
    private DisplayManager mDisplayManager;
    private final Consumer<DisplayTopology> mTopologyListener = this::callback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mMessenger = intent.getParcelableExtra(TEST_MESSENGER, Messenger.class);
        mDisplayManager = getApplicationContext().getSystemService(DisplayManager.class);
        mDisplayManager.registerTopologyListener(getMainExecutor(), mTopologyListener);
        launched();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisplayManager.unregisterTopologyListener(mTopologyListener);
    }

    private void launched() {
        try {
            Message msg = Message.obtain();
            msg.what = MESSAGE_LAUNCHED;
            msg.arg1 = android.os.Process.myPid();
            msg.arg2 = Process.myUid();
            Log.d(TAG, "Launched");
            mMessenger.send(msg);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
    }

    private void callback(DisplayTopology topology) {
        try {
            Message msg = Message.obtain();
            msg.what = MESSAGE_CALLBACK;
            msg.obj = topology;
            Log.d(TAG, "Msg " + topology);
            mMessenger.send(msg);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
    }
}
