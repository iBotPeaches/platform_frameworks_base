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

package com.android.systemui.display.data.repository

import android.view.Display
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.testScope
import kotlinx.coroutines.CoroutineScope

val Kosmos.displayRepository by Fixture { FakeDisplayRepository() }

fun Kosmos.createFakeDisplaySubcomponent(
    coroutineScope: CoroutineScope = testScope.backgroundScope
): SystemUIDisplaySubcomponent {
    return object : SystemUIDisplaySubcomponent {
        override val displayCoroutineScope: CoroutineScope
            get() = coroutineScope
    }
}

val Kosmos.sysuiDefaultDisplaySubcomponent by Fixture {
    createFakeDisplaySubcomponent(testScope.backgroundScope)
}

val Kosmos.fakeSysuiDisplayComponentFactory by Fixture {
    object : SystemUIDisplaySubcomponent.Factory {
        override fun create(displayId: Int): SystemUIDisplaySubcomponent {
            return sysuiDefaultDisplaySubcomponent
        }
    }
}

val Kosmos.displaySubcomponentPerDisplayRepository by Fixture {
    FakePerDisplayRepository<SystemUIDisplaySubcomponent>().apply {
        add(Display.DEFAULT_DISPLAY, sysuiDefaultDisplaySubcomponent)
    }
}
