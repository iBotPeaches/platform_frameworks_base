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

package com.android.systemui.utils

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.utils.windowmanager.WindowManagerProviderImpl
import com.google.common.truth.Truth.assertThat
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
@SmallTest
class WindowManagerProviderImplTest : SysuiTestCase() {

    private val windowManagerProvider = WindowManagerProviderImpl()
    private val windowManagerFromSystemService = mContext.getSystemService(WindowManager::class.java)

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_VIEW_CAPTURE_TRACING)
    fun viewCaptureTracingEnabled_verifyWMInstanceDoesNotMatchContextOne() {
        val windowManagerFromProvider = windowManagerProvider.getWindowManager(mContext)
        assertThat(windowManagerFromProvider).isNotEqualTo(windowManagerFromSystemService)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_VIEW_CAPTURE_TRACING)
    fun viewCaptureTracingDisabled_verifyWMInstanceMatchesContextOne() {
        mContext.addMockSystemService(WindowManager::class.java, windowManagerFromSystemService)

        val windowManagerFromProvider = windowManagerProvider.getWindowManager(mContext)
        assertThat(windowManagerFromProvider).isEqualTo(windowManagerFromSystemService)
    }
}