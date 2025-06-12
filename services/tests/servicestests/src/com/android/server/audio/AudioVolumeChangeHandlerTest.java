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

package com.android.server.audio;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.INativeAudioVolumeGroupCallback;
import android.media.audio.common.AudioVolumeGroupChangeEvent;
import android.media.audiopolicy.IAudioVolumeChangeDispatcher;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

@MediumTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class AudioVolumeChangeHandlerTest {
    private static final long DEFAULT_TIMEOUT_MS = 1000;

    private AudioSystemAdapter mSpyAudioSystem;

    AudioVolumeChangeHandler mAudioVolumeChangedHandler;

    private final IAudioVolumeChangeDispatcher.Stub mMockDispatcher =
            mock(IAudioVolumeChangeDispatcher.Stub.class);

    @Before
    public void setUp() {
        mSpyAudioSystem = spy(new NoOpAudioSystemAdapter());
        when(mMockDispatcher.asBinder()).thenReturn(mock(IBinder.class));
        mAudioVolumeChangedHandler = new AudioVolumeChangeHandler(mSpyAudioSystem);
    }

    @Test
    public void registerListener_withInvalidCallback() {
        IAudioVolumeChangeDispatcher.Stub nullCb = null;
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mAudioVolumeChangedHandler.registerListener(nullCb);
        });

        assertWithMessage("Exception for invalid registration").that(thrown).hasMessageThat()
                .contains("Volume group callback");
    }

    @Test
    public void unregisterListener_withInvalidCallback() {
        IAudioVolumeChangeDispatcher.Stub nullCb = null;
        mAudioVolumeChangedHandler.registerListener(mMockDispatcher);

        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            mAudioVolumeChangedHandler.unregisterListener(nullCb);
        });

        assertWithMessage("Exception for invalid un-registration").that(thrown).hasMessageThat()
                .contains("Volume group callback");
    }

    @Test
    public void registerListener() {
        mAudioVolumeChangedHandler.registerListener(mMockDispatcher);

        verify(mSpyAudioSystem).registerAudioVolumeGroupCallback(any());
    }

    @Test
    public void onAudioVolumeGroupChanged() throws Exception {
        mAudioVolumeChangedHandler.registerListener(mMockDispatcher);
        AudioVolumeGroupChangeEvent volEvent = new AudioVolumeGroupChangeEvent();
        volEvent.groupId = 666;
        volEvent.flags = AudioVolumeGroupChangeEvent.VOLUME_FLAG_FROM_KEY;

        captureRegisteredNativeCallback().onAudioVolumeGroupChanged(volEvent);

        verify(mMockDispatcher,  timeout(DEFAULT_TIMEOUT_MS)).onAudioVolumeGroupChanged(
                eq(volEvent.groupId), eq(volEvent.flags));
    }

    @Test
    public void onAudioVolumeGroupChanged_withMultipleCallback() throws Exception {
        int callbackCount = 10;
        List<IAudioVolumeChangeDispatcher.Stub> validCbs =
                new ArrayList<IAudioVolumeChangeDispatcher.Stub>();
        for (int i = 0; i < callbackCount; i++) {
            IAudioVolumeChangeDispatcher.Stub cb = mock(IAudioVolumeChangeDispatcher.Stub.class);
            when(cb.asBinder()).thenReturn(mock(IBinder.class));
            validCbs.add(cb);
        }
        for (IAudioVolumeChangeDispatcher.Stub cb : validCbs) {
            mAudioVolumeChangedHandler.registerListener(cb);
        }
        AudioVolumeGroupChangeEvent volEvent = new AudioVolumeGroupChangeEvent();
        volEvent.groupId = 666;
        volEvent.flags = AudioVolumeGroupChangeEvent.VOLUME_FLAG_FROM_KEY;
        captureRegisteredNativeCallback().onAudioVolumeGroupChanged(volEvent);

        for (IAudioVolumeChangeDispatcher.Stub cb : validCbs) {
            verify(cb,  timeout(DEFAULT_TIMEOUT_MS)).onAudioVolumeGroupChanged(
                    eq(volEvent.groupId), eq(volEvent.flags));
        }
    }

    private INativeAudioVolumeGroupCallback captureRegisteredNativeCallback() {
        ArgumentCaptor<INativeAudioVolumeGroupCallback> nativeAudioVolumeGroupCallbackCaptor =
                ArgumentCaptor.forClass(INativeAudioVolumeGroupCallback.class);
        verify(mSpyAudioSystem, timeout(DEFAULT_TIMEOUT_MS))
                .registerAudioVolumeGroupCallback(nativeAudioVolumeGroupCallbackCaptor.capture());
        return nativeAudioVolumeGroupCallbackCaptor.getValue();
    }
}
