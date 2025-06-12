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

package android.view;

import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.res.CompatibilityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputSensorInfo;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;

/**
 * Test {@link OrientationEventListener}.
 *
 * <p>Build/Install/Run:
 *  atest FrameworksCoreTests:OrientationEventListenerFrameworkTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class OrientationEventListenerFrameworkTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Context mContext;
    private SensorManager mSensorManager;

    @Before
    public void setup() {
        mContext = mock(Context.class);
        mSensorManager = mock(SensorManager.class);
        doReturn(mSensorManager).when(mContext).getSystemService(Context.SENSOR_SERVICE);
    }

    @After
    public void tearDown() {
        // Reset the override rotation for tests that use the default value.
        CompatibilityInfo.setOverrideDisplayRotation(WindowConfiguration.ROTATION_UNDEFINED);
    }

    @Test
    public void testConstructor() {
        new TestOrientationEventListener(mContext);

        new TestOrientationEventListener(mContext, SensorManager.SENSOR_DELAY_UI);
    }

    @Test
    public void testEnableAndDisable() {
        final TestOrientationEventListener listener = new TestOrientationEventListener(mContext);
        listener.enable();
        listener.disable();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testSensorOrientationUpdate() {
        final Sensor mockSensor = setupMockAccelerometerSensor();
        final TestOrientationEventListener listener = new TestOrientationEventListener(mContext);

        listener.enable();

        sendSensorEventWithOrientation270(mockSensor);

        assertEquals(1, listener.mReportedOrientations.size());
        assertEquals(270, (int) listener.mReportedOrientations.get(0));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testSensorOrientationUpdate_overriddenDisplayRotationReportedWhenSet() {
        final Sensor mockSensor = setupMockAccelerometerSensor();
        final TestOrientationEventListener listener = new TestOrientationEventListener(mContext);

        listener.enable();

        // This should change the reported sensor rotation.
        CompatibilityInfo.setOverrideDisplayRotation(Surface.ROTATION_180);

        sendSensorEventWithOrientation270(mockSensor);

        assertEquals(1, listener.mReportedOrientations.size());
        assertEquals(180, (int) listener.mReportedOrientations.get(0));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testSensorOrientationUpdate_overriddenDisplayRotationIsNegativeFromSensor() {
        final Sensor mockSensor = setupMockAccelerometerSensor();
        final TestOrientationEventListener listener = new TestOrientationEventListener(mContext);

        listener.enable();

        // Display rotation is counted in the opposite direction from the sensor orientation, thus
        // this call should change the reported sensor rotation to 90, as 360 - 270 = 90.
        CompatibilityInfo.setOverrideDisplayRotation(Surface.ROTATION_270);

        sendSensorEventWithOrientation270(mockSensor);

        assertEquals(1, listener.mReportedOrientations.size());
        assertEquals(90, (int) listener.mReportedOrientations.get(0));
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void testSensorOrientationUpdate_notOverriddenWhenCameraFeatureDisabled() {
        final Sensor mockSensor = setupMockAccelerometerSensor();
        final TestOrientationEventListener listener = new TestOrientationEventListener(mContext);

        listener.enable();

        CompatibilityInfo.setOverrideDisplayRotation(Surface.ROTATION_180);

        sendSensorEventWithOrientation270(mockSensor);

        assertEquals(1, listener.mReportedOrientations.size());
        // Sensor unchanged by override because the feature is disabled.
        assertEquals(270, (int) listener.mReportedOrientations.get(0));
    }

    @NonNull
    private Sensor setupMockAccelerometerSensor() {
        final Sensor mockSensor = new Sensor(mock(InputSensorInfo.class));
        doReturn(mockSensor).when(mSensorManager).getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        return mockSensor;
    }

    @NonNull
    private SensorEventListener getRegisteredEventListener() {
        // Get the SensorEventListenerImpl that has subscribed in `listener.enable()`.
        final ArgumentCaptor<SensorEventListener> listenerArgCaptor = ArgumentCaptor
                .forClass(SensorEventListener.class);
        verify(mSensorManager).registerListener(listenerArgCaptor.capture(), any(), anyInt());
        return listenerArgCaptor.getValue();
    }

    private void sendSensorEventWithOrientation270(@NonNull Sensor sensor) {
        final SensorEventListener sensorEventListener = getRegisteredEventListener();
        // Arbitrary values that return orientation 270.
        final SensorEvent sensorEvent = new SensorEvent(sensor, 1, 1L,
                new float[]{1.0f, 0.0f, 0.0f});
        sensorEventListener.onSensorChanged(sensorEvent);
    }

    private static class TestOrientationEventListener extends OrientationEventListener {
        final ArrayList<Integer> mReportedOrientations = new ArrayList<>();

        TestOrientationEventListener(Context context) {
            super(context);
        }

        TestOrientationEventListener(Context context, int rate) {
            super(context, rate);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            mReportedOrientations.add(orientation);
        }
    }
}
