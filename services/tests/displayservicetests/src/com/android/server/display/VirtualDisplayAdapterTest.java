/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.display;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.display.DisplayManager;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplayConfig;
import android.media.projection.IMediaProjection;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableContext;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;
import com.android.server.display.feature.DisplayManagerFlags;
import com.android.server.testutils.TestHandler;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualDisplayAdapterTest {

    private static final int MAX_DEVICES = 3;
    private static final int MAX_DEVICES_PER_PACKAGE = 2;

    private static final float DEFAULT_BRIGHTNESS = 0.34f;
    private static final float DIM_BRIGHTNESS = 0.12f;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext());

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private VirtualDisplayAdapter.SurfaceControlDisplayFactory mMockSufaceControlDisplayFactory;

    @Mock
    private DisplayAdapter.Listener mMockListener;

    @Mock
    private IVirtualDisplayCallback mMockCallback;

    @Mock
    private IBinder mMockBinder;

    @Mock
    private IMediaProjection mMediaProjectionMock;

    @Mock
    private Surface mSurfaceMock;

    @Mock
    private VirtualDisplayConfig mVirtualDisplayConfigMock;

    private TestHandler mHandler;

    @Mock
    private DisplayManagerFlags mFeatureFlags;

    private VirtualDisplayAdapter mAdapter;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext.getOrCreateTestableResources().addOverride(R.integer.config_virtualDisplayLimit,
                MAX_DEVICES);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.config_virtualDisplayLimitPerPackage, MAX_DEVICES_PER_PACKAGE);

        mHandler = new TestHandler(null);
        mAdapter = new VirtualDisplayAdapter(new DisplayManagerService.SyncRoot(), mContext,
                mHandler, mMockListener, mMockSufaceControlDisplayFactory, mFeatureFlags);

        when(mMockCallback.asBinder()).thenReturn(mMockBinder);
    }

    @Test
    public void testCreateAndReleaseVirtualDisplay() {
        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder("test", /* width= */ 1,
                /* height= */ 1, /* densityDpi= */ 1).build();

        DisplayDevice result = mAdapter.createVirtualDisplayLocked(mMockCallback,
                /* projection= */ null, /* ownerUid= */ 10, /* packageName= */ "testpackage",
                /* uniqueId= */ "uniqueId", /* surface= */ null, /* flags= */ 0, config);
        assertNotNull(result);

        result = mAdapter.releaseVirtualDisplayLocked(mMockBinder);
        assertNotNull(result);
    }

    @Test
    public void testCreateVirtualDisplay_createDisplayDeviceInfoFromDefaults() {
        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(
                "testDisplayName", /* width= */ 640, /* height= */ 480, /* densityDpi= */ 240)
                .build();

        final String packageName = "testpackage";
        final String displayUniqueId = VirtualDisplayAdapter.generateDisplayUniqueId(
                packageName, Process.myUid(), config);

        DisplayDevice displayDevice = mAdapter.createVirtualDisplayLocked(
                mMockCallback, /* projection= */ null, /* ownerUid= */ 10,
                packageName, displayUniqueId, /* surface= */ null, /* flags= */ 0, config);

        assertNotNull(displayDevice);
        DisplayDeviceInfo info = displayDevice.getDisplayDeviceInfoLocked();
        assertNotNull(info);

        assertThat(info.width).isEqualTo(640);
        assertThat(info.height).isEqualTo(480);
        assertThat(info.densityDpi).isEqualTo(240);
        assertThat(info.xDpi).isEqualTo(240);
        assertThat(info.yDpi).isEqualTo(240);
        assertThat(info.name).isEqualTo("testDisplayName");
        assertThat(info.uniqueId).isEqualTo(displayUniqueId);
        assertThat(info.ownerPackageName).isEqualTo(packageName);
        assertThat(info.ownerUid).isEqualTo(10);
        assertThat(info.type).isEqualTo(Display.TYPE_VIRTUAL);
        assertThat(info.brightnessMinimum).isEqualTo(PowerManager.BRIGHTNESS_MIN);
        assertThat(info.brightnessMaximum).isEqualTo(PowerManager.BRIGHTNESS_MAX);
        assertThat(info.brightnessDefault).isEqualTo(PowerManager.BRIGHTNESS_MIN);
        assertThat(info.brightnessDim).isEqualTo(PowerManager.BRIGHTNESS_INVALID);
    }

    @Test
    public void testCreateVirtualDisplay_createDisplayDeviceInfoFromVirtualDisplayConfig() {
        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(
                "testDisplayName", /* width= */ 640, /* height= */ 480, /* densityDpi= */ 240)
                .setDefaultBrightness(DEFAULT_BRIGHTNESS)
                .setDimBrightness(DIM_BRIGHTNESS)
                .build();

        final String packageName = "testpackage";
        final String displayUniqueId = VirtualDisplayAdapter.generateDisplayUniqueId(
                packageName, Process.myUid(), config);

        DisplayDevice displayDevice = mAdapter.createVirtualDisplayLocked(
                mMockCallback, /* projection= */ null, /* ownerUid= */ 10,
                packageName, displayUniqueId, /* surface= */ null, /* flags= */ 0, config);

        assertNotNull(displayDevice);
        DisplayDeviceInfo info = displayDevice.getDisplayDeviceInfoLocked();
        assertNotNull(info);

        assertThat(info.brightnessDefault).isEqualTo(DEFAULT_BRIGHTNESS);
        assertThat(info.brightnessDim).isEqualTo(DIM_BRIGHTNESS);
    }

    @Test
    public void testCreatesVirtualDisplay_checkGeneratedDisplayUniqueIdPrefix() {
        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder("test", /* width= */ 1,
                /* height= */ 1, /* densityDpi= */ 1).build();

        final String packageName = "testpackage";
        final String displayUniqueId = VirtualDisplayAdapter.generateDisplayUniqueId(
                packageName, Process.myUid(), config);

        DisplayDevice result = mAdapter.createVirtualDisplayLocked(
                mMockCallback, /* projection= */ null, /* ownerUid= */ 10,
                packageName, displayUniqueId, /* surface= */ null, /* flags= */ 0, config);

        assertNotNull(result);

        final String uniqueId = result.getUniqueId();
        assertTrue(uniqueId.startsWith(VirtualDisplayAdapter.UNIQUE_ID_PREFIX + packageName));
    }

    @Test
    public void testDoesNotCreateVirtualDisplayForSameCallback() {
        VirtualDisplayConfig config1 = new VirtualDisplayConfig.Builder("test", /* width= */ 1,
                /* height= */ 1, /* densityDpi= */ 1).build();
        VirtualDisplayConfig config2 = new VirtualDisplayConfig.Builder("test2", /* width= */ 1,
                /* height= */ 1, /* densityDpi= */ 1).build();

        DisplayDevice result = mAdapter.createVirtualDisplayLocked(mMockCallback,
                /* projection= */ null, /* ownerUid= */ 10, /* packageName= */ "testpackage",
                /* uniqueId= */ "uniqueId1", /* surface= */ null, /* flags= */ 0, config1);
        assertNotNull(result);

        result = mAdapter.createVirtualDisplayLocked(mMockCallback,
                /* projection= */ null, /* ownerUid= */ 10, /* packageName= */ "testpackage",
                /* uniqueId= */ "uniqueId2", /* surface= */ null, /* flags= */ 0, config2);
        assertNull(result);
    }

    @Test
    public void testCreateManyVirtualDisplays_LimitFlagDisabled() {
        when(mFeatureFlags.isVirtualDisplayLimitEnabled()).thenReturn(false);

        // Displays for the same package
        for (int i = 0; i < MAX_DEVICES_PER_PACKAGE * 2; i++) {
            IVirtualDisplayCallback callback = createCallback();
            DisplayDevice device = mAdapter.createVirtualDisplayLocked(callback,
                    mMediaProjectionMock, 1234, "test.package", "123",
                    mSurfaceMock, /* flags= */ 0, mVirtualDisplayConfigMock);
            assertNotNull(device);
        }

        // Displays for different packages
        for (int i = 0; i < MAX_DEVICES * 2; i++) {
            IVirtualDisplayCallback callback = createCallback();
            DisplayDevice device = mAdapter.createVirtualDisplayLocked(callback,
                    mMediaProjectionMock, 1234 + i, "test.package", "123",
                    mSurfaceMock, /* flags= */ 0, mVirtualDisplayConfigMock);
            assertNotNull(device);
        }
    }

    @Test
    public void testCreateVirtualDisplay_MaxDisplaysPerPackage() {
        when(mFeatureFlags.isVirtualDisplayLimitEnabled()).thenReturn(true);
        List<IVirtualDisplayCallback> callbacks = new ArrayList<>();
        int ownerUid = 1234;

        for (int i = 0; i < MAX_DEVICES_PER_PACKAGE * 2; i++) {
            // Same owner UID
            IVirtualDisplayCallback callback = createCallback();
            DisplayDevice device = mAdapter.createVirtualDisplayLocked(callback,
                    mMediaProjectionMock, ownerUid, "test.package", "123",
                    mSurfaceMock, /* flags= */ 0, mVirtualDisplayConfigMock);
            if (i < MAX_DEVICES_PER_PACKAGE) {
                assertNotNull(device);
                callbacks.add(callback);
            } else {
                assertNull(device);
            }
        }

        // Release one display
        DisplayDevice device = mAdapter.releaseVirtualDisplayLocked(callbacks.get(0).asBinder());
        assertNotNull(device);
        callbacks.remove(0);

        // We should be able to create another display
        IVirtualDisplayCallback callback = createCallback();
        device = mAdapter.createVirtualDisplayLocked(callback, mMediaProjectionMock, ownerUid,
                "test.package", "123", mSurfaceMock, /* flags= */ 0,
                mVirtualDisplayConfigMock);
        assertNotNull(device);
        callbacks.add(callback);

        // But only one
        callback = createCallback();
        device = mAdapter.createVirtualDisplayLocked(callback, mMediaProjectionMock, ownerUid,
                "test.package", "123", mSurfaceMock, /* flags= */ 0,
                mVirtualDisplayConfigMock);
        assertNull(device);

        // Release all the displays
        for (IVirtualDisplayCallback cb : callbacks) {
            device = mAdapter.releaseVirtualDisplayLocked(cb.asBinder());
            assertNotNull(device);
        }
        callbacks.clear();

        // We should be able to create the max number of displays again
        for (int i = 0; i < MAX_DEVICES_PER_PACKAGE * 2; i++) {
            // Same owner UID
            callback = createCallback();
            device = mAdapter.createVirtualDisplayLocked(callback, mMediaProjectionMock, ownerUid,
                    "test.package", "123", mSurfaceMock, /* flags= */ 0,
                    mVirtualDisplayConfigMock);
            if (i < MAX_DEVICES_PER_PACKAGE) {
                assertNotNull(device);
                callbacks.add(callback);
            } else {
                assertNull(device);
            }
        }

        // We should be able to create a display for a different package
        callback = createCallback();
        device = mAdapter.createVirtualDisplayLocked(callback, mMediaProjectionMock, ownerUid + 1,
                "test.package", "123", mSurfaceMock, /* flags= */ 0,
                mVirtualDisplayConfigMock);
        assertNotNull(device);
        callbacks.add(callback);
    }

    @Test
    public void testCreateVirtualDisplay_MaxDisplays() {
        when(mFeatureFlags.isVirtualDisplayLimitEnabled()).thenReturn(true);
        List<IVirtualDisplayCallback> callbacks = new ArrayList<>();
        int firstOwnerUid = 1000;

        for (int i = 0; i < MAX_DEVICES * 2; i++) {
            // Different owner UID
            IVirtualDisplayCallback callback = createCallback();
            DisplayDevice device = mAdapter.createVirtualDisplayLocked(callback,
                    mMediaProjectionMock, firstOwnerUid + i, "test.package",
                    "123", mSurfaceMock, /* flags= */ 0, mVirtualDisplayConfigMock);
            if (i < MAX_DEVICES) {
                assertNotNull(device);
                callbacks.add(callback);
            } else {
                assertNull(device);
            }
        }

        // Release one display
        DisplayDevice device = mAdapter.releaseVirtualDisplayLocked(callbacks.get(0).asBinder());
        assertNotNull(device);
        callbacks.remove(0);

        // We should be able to create another display
        IVirtualDisplayCallback callback = createCallback();
        device = mAdapter.createVirtualDisplayLocked(callback, mMediaProjectionMock,
                firstOwnerUid, "test.package", "123", mSurfaceMock, /* flags= */ 0,
                mVirtualDisplayConfigMock);
        assertNotNull(device);
        callbacks.add(callback);

        // But only one
        callback = createCallback();
        device = mAdapter.createVirtualDisplayLocked(callback, mMediaProjectionMock,
                firstOwnerUid, "test.package", "123", mSurfaceMock, /* flags= */ 0,
                mVirtualDisplayConfigMock);
        assertNull(device);

        // Release all the displays
        for (IVirtualDisplayCallback iVirtualDisplayCallback : callbacks) {
            device = mAdapter.releaseVirtualDisplayLocked(iVirtualDisplayCallback.asBinder());
            assertNotNull(device);
        }
        callbacks.clear();

        // We should be able to create the max number of displays again
        for (int i = 0; i < MAX_DEVICES * 2; i++) {
            // Different owner UID
            callback = createCallback();
            device = mAdapter.createVirtualDisplayLocked(callback, mMediaProjectionMock,
                    firstOwnerUid + i, "test.package", "123", mSurfaceMock,
                    /* flags= */ 0, mVirtualDisplayConfigMock);
            if (i < MAX_DEVICES) {
                assertNotNull(device);
                callbacks.add(callback);
            } else {
                assertNull(device);
            }
        }
    }

    @EnableFlags(
            android.companion.virtualdevice.flags.Flags.FLAG_CORRECT_VIRTUAL_DISPLAY_POWER_STATE)
    @Test
    public void neverBlankDisplay_alwaysOn() {
        // A non-public non-mirror display is considered never blank.
        DisplayDevice device = mAdapter.createVirtualDisplayLocked(mMockCallback,
                /* projection= */ null, /* ownerUid= */ 10, /* packageName= */ "testpackage",
                "uniqueId", /* surface= */ mSurfaceMock, /* flags= */ 0,
                mVirtualDisplayConfigMock);

        DisplayDeviceInfo info = device.getDisplayDeviceInfoLocked();
        assertThat(info.state).isEqualTo(Display.STATE_ON);
        assertThat(info.flags & DisplayDeviceInfo.FLAG_NEVER_BLANK)
                .isEqualTo(DisplayDeviceInfo.FLAG_NEVER_BLANK);
    }

    @EnableFlags(
            android.companion.virtualdevice.flags.Flags.FLAG_CORRECT_VIRTUAL_DISPLAY_POWER_STATE)
    @Test
    public void virtualDisplayStateChange_propagatesToSurfaceControl() throws Exception {
        final String uniqueId = "uniqueId";
        final IBinder displayToken = new Binder();
        when(mMockSufaceControlDisplayFactory.createDisplay(
                any(), anyBoolean(), anyBoolean(), eq(uniqueId), anyFloat()))
                .thenReturn(displayToken);

        // The display needs to be public, otherwise it will be considered never blank.
        DisplayDevice device = mAdapter.createVirtualDisplayLocked(mMockCallback,
                /* projection= */ null, /* ownerUid= */ 10, /* packageName= */ "testpackage",
                uniqueId, /* surface= */ mSurfaceMock, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mVirtualDisplayConfigMock);

        DisplayDeviceInfo info = device.getDisplayDeviceInfoLocked();
        assertThat(info.state).isEqualTo(Display.STATE_UNKNOWN);
        assertThat(info.flags & DisplayDeviceInfo.FLAG_NEVER_BLANK).isEqualTo(0);

        // Any initial state change is processed because the display state is initially UNKNOWN
        Runnable stateOnRunnable = device.requestDisplayStateLocked(
                Display.STATE_ON, /* brightnessState= */ 1.0f, /* sdrBrightnessState= */ 1.0f,
                /* displayOffloadSession= */ null);
        assertThat(stateOnRunnable).isNotNull();
        stateOnRunnable.run();
        verify(mMockSufaceControlDisplayFactory)
                .setDisplayPowerMode(displayToken, SurfaceControl.POWER_MODE_NORMAL);
        verify(mMockCallback).onResumed();

        // Requesting the same display state is a no-op
        Runnable stateOnSecondRunnable = device.requestDisplayStateLocked(
                Display.STATE_ON, /* brightnessState= */ 1.0f, /* sdrBrightnessState= */ 1.0f,
                /* displayOffloadSession= */ null);
        assertThat(stateOnSecondRunnable).isNull();

        // A change to the display state is processed
        Runnable stateOffRunnable = device.requestDisplayStateLocked(
                Display.STATE_OFF, /* brightnessState= */ 1.0f, /* sdrBrightnessState= */ 1.0f,
                /* displayOffloadSession= */ null);
        assertThat(stateOffRunnable).isNotNull();
        stateOffRunnable.run();
        verify(mMockSufaceControlDisplayFactory)
                .setDisplayPowerMode(displayToken, SurfaceControl.POWER_MODE_OFF);
        verify(mMockCallback).onPaused();
    }

    @EnableFlags(
            android.companion.virtualdevice.flags.Flags.FLAG_CORRECT_VIRTUAL_DISPLAY_POWER_STATE)
    @Test
    public void createVirtualDisplayLocked_neverBlank_optimizesForPower() {
        final String uniqueId = "uniqueId";
        final IBinder displayToken = new Binder();
        final String name = "name";
        when(mVirtualDisplayConfigMock.getName()).thenReturn(name);
        when(mMockSufaceControlDisplayFactory.createDisplay(
                any(), anyBoolean(), anyBoolean(), eq(uniqueId), anyFloat()))
                .thenReturn(displayToken);

        // Use a private display to cause the display to be never blank.
        mAdapter.createVirtualDisplayLocked(mMockCallback,
                /* projection= */ null, /* ownerUid= */ 10, /* packageName= */ "testpackage",
                uniqueId, /* surface= */ mSurfaceMock, 0, mVirtualDisplayConfigMock);

        verify(mMockSufaceControlDisplayFactory).createDisplay(eq(name), eq(false), eq(true),
                eq(uniqueId), anyFloat());
    }

    @EnableFlags(
            android.companion.virtualdevice.flags.Flags.FLAG_CORRECT_VIRTUAL_DISPLAY_POWER_STATE)
    @Test
    public void createVirtualDisplayLocked_blankable_optimizesForPerformance() {
        final String uniqueId = "uniqueId";
        final IBinder displayToken = new Binder();
        final String name = "name";
        when(mVirtualDisplayConfigMock.getName()).thenReturn(name);
        when(mMockSufaceControlDisplayFactory.createDisplay(
                any(), anyBoolean(), anyBoolean(), eq(uniqueId), anyFloat()))
                .thenReturn(displayToken);

        // Use a public display to cause the display to be blankable
        mAdapter.createVirtualDisplayLocked(mMockCallback,
                /* projection= */ null, /* ownerUid= */ 10, /* packageName= */ "testpackage",
                uniqueId, /* surface= */ mSurfaceMock, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mVirtualDisplayConfigMock);

        verify(mMockSufaceControlDisplayFactory).createDisplay(eq(name), eq(false), eq(false),
                eq(uniqueId), anyFloat());
    }

    private IVirtualDisplayCallback createCallback() {
        return new IVirtualDisplayCallback.Stub() {

            @Override
            public void onPaused() {
            }

            @Override
            public void onResumed() {
            }

            @Override
            public void onStopped() {
            }
        };
    }
}
