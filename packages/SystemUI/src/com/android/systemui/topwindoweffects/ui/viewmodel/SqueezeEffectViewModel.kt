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

package com.android.systemui.topwindoweffects.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.keyevent.domain.interactor.KeyEventInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class SqueezeEffectViewModel
@AssistedInject
constructor(
    keyEventInteractor: KeyEventInteractor
) : ExclusiveActivatable() {
    private val hydrator = Hydrator("SqueezeEffectViewModel.hydrator")

    val isPowerButtonPressed: Boolean by hydrator.hydratedStateOf(
        traceName = "isPowerButtonPressed",
        initialValue = false,
        source = keyEventInteractor.isPowerButtonDown
    )

    val isPowerButtonLongPressed: Boolean by hydrator.hydratedStateOf(
        traceName = "isPowerButtonLongPressed",
        initialValue = false,
        source = keyEventInteractor.isPowerButtonLongPressed
    )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(): SqueezeEffectViewModel
    }
}