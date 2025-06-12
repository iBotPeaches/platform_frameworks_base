/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.apps.inputmethod.simpleime.ims;

import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;

/** Wrapper of {@link InputMethodService} to expose interfaces for testing purpose. */
public class InputMethodServiceWrapper extends InputMethodService {

    private static final String TAG = "InputMethodServiceWrapper";

    /** Last created instance of this wrapper. */
    @NonNull
    private static WeakReference<InputMethodServiceWrapper> sInstance = new WeakReference<>(null);

    /** IME show event ({@link #onStartInputView}). */
    public static final int EVENT_SHOW = 0;

    /** IME hide event ({@link #onFinishInputView}). */
    public static final int EVENT_HIDE = 1;

    /** IME configuration change event ({@link #onConfigurationChanged}). */
    public static final int EVENT_CONFIG = 2;

    /** The type of event that can be waited with a latch. */
    @IntDef(value = {
            EVENT_SHOW,
            EVENT_HIDE,
            EVENT_CONFIG,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Event {}

    /** The IME event type that the current latch, if any, waits on. */
    @Event
    private int mLatchEvent;

    private boolean mInputViewStarted;

    /**
     * @see #setCountDownLatchForTesting
     */
    @Nullable
    private CountDownLatch mCountDownLatch;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        super.onCreate();
        sInstance = new WeakReference<>(this);
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        Log.i(TAG, "onStartInput() editor=" + dumpEditorInfo(info) + ", restarting=" + restarting);
        super.onStartInput(info, restarting);
    }

    @Override
    public void onFinishInput() {
        Log.i(TAG, "onFinishInput()");
        super.onFinishInput();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        Log.i(TAG, "onStartInputView() editor=" + dumpEditorInfo(info)
                + ", restarting=" + restarting);
        super.onStartInputView(info, restarting);
        mInputViewStarted = true;
        if (mCountDownLatch != null && mLatchEvent == EVENT_SHOW) {
            mCountDownLatch.countDown();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        Log.i(TAG, "onFinishInputView()");
        super.onFinishInputView(finishingInput);
        mInputViewStarted = false;

        if (mCountDownLatch != null && mLatchEvent == EVENT_HIDE) {
            mCountDownLatch.countDown();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(TAG, "onConfigurationChanged() " + newConfig);
        super.onConfigurationChanged(newConfig);

        if (mCountDownLatch != null && mLatchEvent == EVENT_CONFIG) {
            mCountDownLatch.countDown();
        }
    }

    public boolean getCurrentInputViewStarted() {
        return mInputViewStarted;
    }

    /**
     * Sets the latch used to wait for the IME event.
     *
     * @param latch      the latch to wait on.
     * @param latchEvent the event to set the latch on.
     */
    public void setCountDownLatchForTesting(@Nullable CountDownLatch latch, @Event int latchEvent) {
        mCountDownLatch = latch;
        mLatchEvent = latchEvent;
    }

    /** Gets the last created instance of this wrapper, if available. */
    @Nullable
    public static InputMethodServiceWrapper getInstance() {
        return sInstance.get();
    }

    /**
     * Gets the string representation of the IME event that is being waited on.
     *
     * @param eventType the IME event type.
     */
    @NonNull
    public static String eventToString(@Event int eventType) {
        return switch (eventType) {
            case EVENT_SHOW -> "onStartInputView";
            case EVENT_HIDE -> "onFinishInputView";
            case EVENT_CONFIG -> "onConfigurationChanged";
            default -> "unknownEvent";
        };
    }

    @NonNull
    private String dumpEditorInfo(EditorInfo info) {
        if (info == null) {
            return "null";
        }
        return "EditorInfo{packageName=" + info.packageName
                + " fieldId=" + info.fieldId
                + " hintText=" + info.hintText
                + " privateImeOptions=" + info.privateImeOptions
                + "}";
    }
}
