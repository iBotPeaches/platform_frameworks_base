package com.android.test.input;

import android.view.MotionEvent;

interface IAnrTestService {
    /**
     * Provide the activity information. This includes:
     * windowToken: the windowToken of the activity window
     * displayId: the display id on which the activity is positioned
     * pid: the pid of the activity
     */
    void provideActivityInfo(IBinder windowToken, int displayId, int pid);
    /**
     * Provide the MotionEvent received by the remote activity.
     */
    void notifyMotion(in MotionEvent event);
}
