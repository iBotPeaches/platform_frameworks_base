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

package com.android.systemui.cursorposition.domain.data.repository

import android.os.Handler
import com.android.app.displaylib.PerDisplayInstanceProviderWithTeardown
import com.android.systemui.cursorposition.data.repository.InputEventListenerBuilder
import com.android.systemui.cursorposition.data.repository.InputMonitorBuilder
import com.android.systemui.cursorposition.data.repository.SingleDisplayCursorPositionRepository
import com.android.systemui.cursorposition.data.repository.SingleDisplayCursorPositionRepositoryImpl

class TestCursorPositionRepositoryInstanceProvider(
    private val handler: Handler,
    private val listenerBuilder: InputEventListenerBuilder,
    private val inputMonitorBuilder: InputMonitorBuilder,
) : PerDisplayInstanceProviderWithTeardown<SingleDisplayCursorPositionRepository> {

    override fun destroyInstance(instance: SingleDisplayCursorPositionRepository) {
        instance.destroy()
    }

    override fun createInstance(displayId: Int): SingleDisplayCursorPositionRepository {
        return SingleDisplayCursorPositionRepositoryImpl(
            displayId,
            handler,
            listenerBuilder,
            inputMonitorBuilder,
        )
    }
}
