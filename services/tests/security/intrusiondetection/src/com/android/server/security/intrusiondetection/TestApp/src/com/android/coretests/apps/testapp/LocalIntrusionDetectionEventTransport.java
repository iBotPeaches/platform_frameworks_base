/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 with the License.
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

package com.android.coretests.apps.testapp;

import android.app.admin.SecurityLog;
import android.app.admin.SecurityLog.SecurityEvent;
import android.content.Context;
import android.content.Intent;
import android.security.intrusiondetection.IntrusionDetectionEvent;
import android.security.intrusiondetection.IntrusionDetectionEventTransport;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that extends {@link IntrusionDetectionEventTransport} to provide a
 * local transport mechanism for testing purposes. This implementation overrides
 * the {@link #initialize()}, {@link #addData(List)}, and {@link #release()} methods
 * to manage events locally within the test environment.
 *
 * For now, the implementation returns true for all methods since we don't
 * have a real data source to send events to.
 */
public class LocalIntrusionDetectionEventTransport extends IntrusionDetectionEventTransport {
    private List<IntrusionDetectionEvent> mEvents = new ArrayList<>();

    private static final String ACTION_SECURITY_EVENT_RECEIVED =
            "com.android.coretests.apps.testapp.ACTION_SECURITY_EVENT_RECEIVED";
    private static final String TAG = "LocalIntrusionDetectionEventTransport";
    private static final String TEST_SECURITY_EVENT_TAG = "test_security_event_tag";
    private static Context sContext;

    public LocalIntrusionDetectionEventTransport(Context context) {
        sContext = context;
    }

    // Broadcast an intent to the CTS test service to indicate that the security
    // event was received.
    private static void broadcastSecurityEventReceived() {
        try {
            Intent intent = new Intent(ACTION_SECURITY_EVENT_RECEIVED);
            sContext.sendBroadcast(intent);
            Log.i(TAG, "LIZ_TESTING: sent broadcast");
        } catch (Exception e) {
            Log.e(TAG, "Exception sending broadcast", e);
        }
    }

    private static void checkIfSecurityEventReceivedFromCts(List<IntrusionDetectionEvent> events) {
        // Loop through the events and check if any of them are the security event
        // that uses the TEST_SECURITY_EVENT_TAG tag, which is set by the CTS test.
        for (IntrusionDetectionEvent event : events) {
            if (event.getType() == IntrusionDetectionEvent.SECURITY_EVENT) {
                SecurityEvent securityEvent = event.getSecurityEvent();
                Object[] eventData = (Object[]) securityEvent.getData();
                if (securityEvent.getTag() == SecurityLog.TAG_KEY_GENERATED
                        && eventData[1].equals(TEST_SECURITY_EVENT_TAG)) {
                    broadcastSecurityEventReceived();
                    return;
                }
            }
        }
    }

    @Override
    public boolean initialize() {
        return true;
    }

    @Override
    public boolean addData(List<IntrusionDetectionEvent> events) {
        // Our CTS tests will generate a security event. In order to
        // verify the event is received with the appropriate data, we will
        // check the events locally and set a property value that can be
        // read by the test.
        checkIfSecurityEventReceivedFromCts(events);
        mEvents.addAll(events);
        return true;
    }

    @Override
    public boolean release() {
        return true;
    }

    public List<IntrusionDetectionEvent> getEvents() {
        return mEvents;
    }
}
