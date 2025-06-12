/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.model;


import static android.view.Display.DEFAULT_DISPLAY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.kosmos.KosmosJavaAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SysUiStateTest extends SysuiTestCase {
    private static final int FLAG_1 = 1;
    private static final int FLAG_2 = 1 << 1;
    private static final int FLAG_3 = 1 << 2;
    private static final int FLAG_4 = 1 << 3;
    private static final int DISPLAY_ID = DEFAULT_DISPLAY;

    private KosmosJavaAdapter mKosmos;
    private SysUiState.SysUiStateCallback mCallback;
    private SysUiState mFlagsContainer;
    private SceneContainerPlugin mSceneContainerPlugin;
    private DumpManager mDumpManager;
    private SysUIStateDispatcher mSysUIStateDispatcher;

    private SysUiState createInstance(int displayId) {
        var sysuiState = new SysUiStateImpl(displayId, mSceneContainerPlugin, mDumpManager,
                mSysUIStateDispatcher);
        sysuiState.addCallback(mCallback);
        return sysuiState;
    }

    @Before
    public void setup() {
        mKosmos = new KosmosJavaAdapter(this);
        mFlagsContainer = mKosmos.getSysuiState();
        mSceneContainerPlugin = mKosmos.getSceneContainerPlugin();
        mCallback = mock(SysUiState.SysUiStateCallback.class);
        mDumpManager = mock(DumpManager.class);
        mSysUIStateDispatcher = mKosmos.getSysUIStateDispatcher();
        mFlagsContainer = createInstance(DEFAULT_DISPLAY);
    }

    @Test
    public void addSingle_setFlag() {
        setFlags(FLAG_1);

        verify(mCallback, times(1)).onSystemUiStateChanged(FLAG_1, DEFAULT_DISPLAY);
    }

    @Test
    public void addMultiple_setFlag() {
        setFlags(FLAG_1);
        setFlags(FLAG_2);

        verify(mCallback, times(1)).onSystemUiStateChanged(FLAG_1, DEFAULT_DISPLAY);
        verify(mCallback, times(1)).onSystemUiStateChanged(FLAG_1 | FLAG_2, DEFAULT_DISPLAY);
    }

    @Test
    public void addMultipleRemoveOne_setFlag() {
        setFlags(FLAG_1);
        setFlags(FLAG_2);
        mFlagsContainer.setFlag(FLAG_1, false).commitUpdate(DISPLAY_ID);

        verify(mCallback, times(1)).onSystemUiStateChanged(FLAG_1, DEFAULT_DISPLAY);
        verify(mCallback, times(1)).onSystemUiStateChanged(FLAG_1 | FLAG_2, DEFAULT_DISPLAY);
        verify(mCallback, times(1)).onSystemUiStateChanged(FLAG_2, DEFAULT_DISPLAY);
    }

    @Test
    public void addMultiple_setFlags() {
        setFlags(FLAG_1, FLAG_2, FLAG_3, FLAG_4);

        int expected = FLAG_1 | FLAG_2 | FLAG_3 | FLAG_4;
        verify(mCallback, times(1)).onSystemUiStateChanged(expected, DEFAULT_DISPLAY);
    }

    @Test
    public void addMultipleRemoveOne_setFlags() {
        setFlags(FLAG_1, FLAG_2, FLAG_3, FLAG_4);
        mFlagsContainer.setFlag(FLAG_2, false).commitUpdate(DISPLAY_ID);

        int expected1 = FLAG_1 | FLAG_2 | FLAG_3 | FLAG_4;
        verify(mCallback, times(1)).onSystemUiStateChanged(expected1, DEFAULT_DISPLAY);
        int expected2 = FLAG_1 | FLAG_3 | FLAG_4;
        verify(mCallback, times(1)).onSystemUiStateChanged(expected2, DEFAULT_DISPLAY);
    }

    @Test
    public void removeCallback() {
        mFlagsContainer.removeCallback(mCallback);
        setFlags(FLAG_1, FLAG_2, FLAG_3, FLAG_4);

        int expected = FLAG_1 | FLAG_2 | FLAG_3 | FLAG_4;
        verify(mCallback, times(0)).onSystemUiStateChanged(expected, DEFAULT_DISPLAY);
    }

    @Test
    public void setFlag_receivedForDefaultDisplay() {
        setFlags(FLAG_1);

        verify(mCallback, times(1)).onSystemUiStateChanged(FLAG_1, DEFAULT_DISPLAY);
    }


    @Test
    public void init_registersWithDumpManager() {
        mFlagsContainer.start();

        verify(mDumpManager).registerNormalDumpable(any(), eq(mFlagsContainer));
    }

    @Test
    public void destroy_unregistersWithDumpManager() {
        mFlagsContainer.destroy();

        verify(mDumpManager).unregisterDumpable(anyString());
    }

    private void setFlags(int... flags) {
        setFlags(mFlagsContainer, flags);
    }

    private void setFlags(SysUiState instance, int... flags) {
        for (int flag : flags) {
            instance.setFlag(flag, true);
        }
        instance.commitUpdate();
    }
}
