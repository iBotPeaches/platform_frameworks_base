/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.data.repository

import android.view.Display
import com.android.systemui.shade.display.FakeShadeDisplayPolicy
import com.android.systemui.shade.display.ShadeDisplayPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeShadeDisplayRepository : MutableShadeDisplaysRepository {
    private val _displayId = MutableStateFlow(Display.DEFAULT_DISPLAY)
    private val _pendingDisplayId = MutableStateFlow(Display.DEFAULT_DISPLAY)

    fun setDisplayId(displayId: Int) {
        _displayId.value = displayId
    }

    fun setPendingDisplayId(displayId: Int) {
        _pendingDisplayId.value = displayId
    }

    override fun onDisplayChangedSucceeded(displayId: Int) {
        setDisplayId(displayId)
    }

    override val displayId: StateFlow<Int>
        get() = _displayId

    override val pendingDisplayId: StateFlow<Int>
        get() = _pendingDisplayId

    override val currentPolicy: ShadeDisplayPolicy
        get() = FakeShadeDisplayPolicy
}
