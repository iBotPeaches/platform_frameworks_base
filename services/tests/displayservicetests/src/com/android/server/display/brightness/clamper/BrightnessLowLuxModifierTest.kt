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
package com.android.server.display.brightness.clamper

import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.testing.TestableContext
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.display.DisplayDeviceConfig
import com.android.server.display.brightness.BrightnessReason
import com.android.server.display.feature.flags.Flags
import com.android.server.testutils.TestHandler
import com.android.server.testutils.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

private const val USER_ID = UserHandle.USER_CURRENT

class BrightnessLowLuxModifierTest {

    private var mockClamperChangeListener =
        mock<BrightnessClamperController.ClamperChangeListener>()

    val context = TestableContext(
        InstrumentationRegistry.getInstrumentation().getContext())

    private val testHandler = TestHandler(null)
    private lateinit var modifier: BrightnessLowLuxModifier

    private var mockDisplayDeviceConfig = mock<DisplayDeviceConfig>()

    private val LOW_LUX_BRIGHTNESS = 0.1f

    @Before
    fun setUp() {
        modifier =
            BrightnessLowLuxModifier(
                testHandler,
                mockClamperChangeListener,
                context,
                mockDisplayDeviceConfig
            )

        // values below transition point (even dimmer range)
        // nits: 0.1 -> backlight 0.02 -> brightness -> 0.1
        whenever(mockDisplayDeviceConfig.getBacklightFromNits(/* nits= */ 1.0f))
                .thenReturn(0.02f)
        whenever(mockDisplayDeviceConfig.getBrightnessFromBacklight(/* backlight = */ 0.02f))
                .thenReturn(LOW_LUX_BRIGHTNESS)

        // values above transition point (normal range)
        // nits: 10 -> backlight 0.2 -> brightness -> 0.3
        whenever(mockDisplayDeviceConfig.getBacklightFromNits(/* nits= */ 2f))
                .thenReturn(0.15f)
        whenever(mockDisplayDeviceConfig.getBrightnessFromBacklight(/* backlight = */ 0.15f))
                .thenReturn(0.24f)

        // min nits when lux of 400
        whenever(mockDisplayDeviceConfig.getMinNitsFromLux(/* lux= */ 400f))
                .thenReturn(1.0f)

        testHandler.flush()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EVEN_DIMMER)
    fun testLuxRestrictsBrightnessRange() {
        // test that high lux prevents low brightness range.
        modifier.setAmbientLux(400f)

        testHandler.flush()

        assertThat(modifier.isActive).isTrue()
        // Test restriction from lux setting
        assertThat(modifier.brightnessReason).isEqualTo(BrightnessReason.MODIFIER_MIN_LUX)
        assertThat(modifier.brightnessLowerBound).isEqualTo(LOW_LUX_BRIGHTNESS)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_EVEN_DIMMER)
    fun testEnabledEvenWhenAutobrightnessIsOff() {
        // test that high lux prevents low brightness range.
        modifier.setAmbientLux(400f)
        testHandler.flush()

        assertThat(modifier.isActive).isTrue()
        // Test restriction from lux setting
        assertThat(modifier.brightnessReason).isEqualTo(BrightnessReason.MODIFIER_MIN_LUX)
        assertThat(modifier.brightnessLowerBound).isEqualTo(LOW_LUX_BRIGHTNESS)

        modifier.setAmbientLux(400f)
        testHandler.flush()

        assertThat(modifier.isActive).isTrue()
        // Test restriction from lux setting
        assertThat(modifier.brightnessReason).isEqualTo(BrightnessReason.MODIFIER_MIN_LUX)
        assertThat(modifier.brightnessLowerBound).isEqualTo(LOW_LUX_BRIGHTNESS)
    }
}

