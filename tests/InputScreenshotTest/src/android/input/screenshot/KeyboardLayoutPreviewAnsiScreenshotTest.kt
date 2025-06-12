/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.input.screenshot

import android.content.Context
import android.hardware.input.KeyboardLayout
import android.os.LocaleList
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import platform.test.screenshot.DeviceEmulationSpec

/** A screenshot test for Keyboard layout preview for Ansi physical layout. */
@RunWith(Parameterized::class)
class KeyboardLayoutPreviewAnsiScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() = DeviceEmulationSpec.PhoneMinimal
    }

    @get:Rule
    val screenshotRule = InputScreenshotTestRule(
            emulationSpec,
            "frameworks/base/tests/InputScreenshotTest/assets"
    )

    @Test
    fun test() {
        screenshotRule.screenshotTest("layout-preview-ansi") {
            context: Context -> LayoutPreview.createLayoutPreview(
                context,
                KeyboardLayout(
                    "descriptor",
                    "layout",
                    /* collection= */null,
                    /* priority= */0,
                    LocaleList(Locale.US),
                    /* layoutType= */0,
                    /* vid= */0,
                    /* pid= */0
                )
            )
        }
    }
}