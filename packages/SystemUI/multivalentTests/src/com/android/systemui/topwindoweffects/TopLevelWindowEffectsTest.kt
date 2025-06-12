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

package com.android.systemui.topwindoweffects

import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyevent.data.repository.fakeKeyEventRepository
import com.android.systemui.keyevent.data.repository.keyEventRepository
import com.android.systemui.keyevent.domain.interactor.KeyEventInteractor
import com.android.systemui.keyevent.domain.interactor.keyEventInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.topwindoweffects.data.repository.fakeSqueezeEffectRepository
import com.android.systemui.topwindoweffects.domain.interactor.SqueezeEffectInteractor
import com.android.systemui.topwindoweffects.ui.compose.EffectsWindowRoot
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectViewModel
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class TopLevelWindowEffectsTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    @Mock
    private lateinit var windowManager: WindowManager

    @Mock
    private lateinit var viewModelFactory: SqueezeEffectViewModel.Factory

    private val Kosmos.underTest by Kosmos.Fixture {
        TopLevelWindowEffects(
            context = mContext,
            applicationScope = testScope.backgroundScope,
            windowManager = windowManager,
            keyEventInteractor = keyEventInteractor,
            viewModelFactory = viewModelFactory,
            squeezeEffectInteractor = SqueezeEffectInteractor(
                squeezeEffectRepository = fakeSqueezeEffectRepository
            )
        )
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        doNothing().whenever(windowManager).addView(any<View>(), any<WindowManager.LayoutParams>())
        doNothing().whenever(windowManager).removeView(any<View>())
        doNothing().whenever(windowManager).removeView(any<EffectsWindowRoot>())
    }

    @Test
    fun noWindowWhenSqueezeEffectDisabled() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = false

            underTest.start()

            verify(windowManager, never()).addView(any<View>(), any<WindowManager.LayoutParams>())
        }

    @Test
    fun addViewToWindowWhenSqueezeEffectEnabled() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isSqueezeEffectEnabled.value = true
            fakeKeyEventRepository.setPowerButtonDown(true)

            underTest.start()

            verify(windowManager, times(1)).addView(any<View>(),
                any<WindowManager.LayoutParams>())
        }
}