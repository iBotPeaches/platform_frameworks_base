/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.settingslib.display;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.os.RemoteException;
import android.view.Display;
import android.view.DisplayAdjustments;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settingslib.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DisplayDensityUtilsTest {

    private static final float MAX_SCALE = 1.33f;
    private static final float MIN_SCALE = 0.85f;
    private static final float MIN_INTERVAL = 0.09f;
    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;
    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private DisplayManagerGlobal mDisplayManagerGlobal;
    @Mock
    private IWindowManager mIWindowManager;
    private IWindowManager mWindowManagerToRestore;
    private DisplayDensityUtils mDisplayDensityUtils;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(mDisplayManager).when(mContext).getSystemService((Class<Object>) any());
        mWindowManagerToRestore = WindowManagerGlobal.getWindowManagerService();
        WindowManagerGlobal.setWindowManagerServiceForSystemProcess(mIWindowManager);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getFraction(R.fraction.display_density_max_scale, 1, 1)).thenReturn(
                MAX_SCALE);
        when(mResources.getFraction(R.fraction.display_density_min_scale, 1, 1)).thenReturn(
                MIN_SCALE);
        when(mResources.getFraction(R.fraction.display_density_min_scale_interval, 1,
                1)).thenReturn(MIN_INTERVAL);
        when(mResources.getString(anyInt())).thenReturn("summary");
    }

    @After
    public void teardown() {
        WindowManagerGlobal.setWindowManagerServiceForSystemProcess(mWindowManagerToRestore);
    }

    @Test
    public void createDisplayDensityUtil_onlyDefaultDisplay() throws RemoteException {
        var info = createDisplayInfoForDisplay(
                Display.DEFAULT_DISPLAY, Display.TYPE_INTERNAL, 2560, 1600, 320);
        var display = new Display(mDisplayManagerGlobal, info.displayId, info,
                (DisplayAdjustments) null);
        doReturn(new Display[]{display}).when(mDisplayManager).getDisplays(any());
        doReturn(display).when(mDisplayManager).getDisplay(info.displayId);

        mDisplayDensityUtils = new DisplayDensityUtils(mContext);

        assertThat(mDisplayDensityUtils.getValues()).isEqualTo(new int[]{272, 320, 354, 390, 424});
    }

    @Test
    public void createDisplayDensityUtil_multipleInternalDisplays() throws RemoteException {
        // Default display
        var defaultDisplayInfo = createDisplayInfoForDisplay(Display.DEFAULT_DISPLAY,
                Display.TYPE_INTERNAL, 2000, 2000, 390);
        var defaultDisplay = new Display(mDisplayManagerGlobal, defaultDisplayInfo.displayId,
                defaultDisplayInfo,
                (DisplayAdjustments) null);
        doReturn(defaultDisplay).when(mDisplayManager).getDisplay(defaultDisplayInfo.displayId);

        // Create another internal display
        var internalDisplayInfo = createDisplayInfoForDisplay(1, Display.TYPE_INTERNAL,
                2000, 1000, 390);
        var internalDisplay = new Display(mDisplayManagerGlobal, internalDisplayInfo.displayId,
                internalDisplayInfo,
                (DisplayAdjustments) null);
        doReturn(internalDisplay).when(mDisplayManager).getDisplay(internalDisplayInfo.displayId);

        doReturn(new Display[]{defaultDisplay, internalDisplay}).when(mDisplayManager).getDisplays(
                anyString());

        mDisplayDensityUtils = new DisplayDensityUtils(mContext);

        assertThat(mDisplayDensityUtils.getValues()).isEqualTo(new int[]{330, 390, 426, 462, 500});
    }

    @Test
    public void createDisplayDensityUtil_forExternalDisplay() throws RemoteException {
        // Default display
        var defaultDisplayInfo = createDisplayInfoForDisplay(Display.DEFAULT_DISPLAY,
                Display.TYPE_INTERNAL, 2000, 2000, 390);
        var defaultDisplay = new Display(mDisplayManagerGlobal, defaultDisplayInfo.displayId,
                defaultDisplayInfo,
                (DisplayAdjustments) null);
        doReturn(defaultDisplay).when(mDisplayManager).getDisplay(defaultDisplayInfo.displayId);

        // Create external display
        var externalDisplayInfo = createDisplayInfoForDisplay(/* displayId= */ 2,
                Display.TYPE_EXTERNAL, 1920, 1080, 85);
        var externalDisplay = new Display(mDisplayManagerGlobal, externalDisplayInfo.displayId,
                externalDisplayInfo,
                (DisplayAdjustments) null);

        doReturn(new Display[]{externalDisplay, defaultDisplay}).when(mDisplayManager).getDisplays(
                any());
        doReturn(externalDisplay).when(mDisplayManager).getDisplay(externalDisplayInfo.displayId);

        mDisplayDensityUtils = new DisplayDensityUtils(mContext,
                (info) -> info.displayId == externalDisplayInfo.displayId);

        assertThat(mDisplayDensityUtils.getValues()).isEqualTo(new int[]{72, 85, 94, 102, 112});
    }

    private DisplayInfo createDisplayInfoForDisplay(int displayId, int displayType,
            int width, int height, int density) throws RemoteException {
        var displayInfo = new DisplayInfo();
        displayInfo.displayId = displayId;
        displayInfo.type = displayType;
        displayInfo.logicalWidth = width;
        displayInfo.logicalHeight = height;
        displayInfo.logicalDensityDpi = density;

        doReturn(displayInfo).when(mDisplayManagerGlobal).getDisplayInfo(displayInfo.displayId);
        doReturn(displayInfo.logicalDensityDpi).when(mIWindowManager).getInitialDisplayDensity(
                displayInfo.displayId);
        return displayInfo;
    }
}

