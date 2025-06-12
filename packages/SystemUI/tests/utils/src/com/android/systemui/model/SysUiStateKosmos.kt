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

package com.android.systemui.model

import android.view.Display
import com.android.systemui.common.domain.interactor.SysUIStateDisplaysInteractor
import com.android.systemui.display.data.repository.FakePerDisplayRepository
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import org.mockito.Mockito.spy

val Kosmos.sysUiState by Fixture { sysUiStateFactory.create(Display.DEFAULT_DISPLAY) }
val Kosmos.sysUIStateDispatcher by Fixture { SysUIStateDispatcher() }

val Kosmos.sysUiStateFactory by Fixture {
    object : SysUiStateImpl.Factory {
        override fun create(displayId: Int): SysUiStateImpl {
            return spy(
                SysUiStateImpl(displayId, sceneContainerPlugin, dumpManager, sysUIStateDispatcher)
            )
        }
    }
}

val Kosmos.fakeSysUIStatePerDisplayRepository by Fixture {
    FakePerDisplayRepository<SysUiState>().apply { add(Display.DEFAULT_DISPLAY, sysUiState) }
}

val Kosmos.sysuiStateInteractor by Fixture {
    SysUIStateDisplaysInteractor(fakeSysUIStatePerDisplayRepository, displayRepository)
}

val Kosmos.sysUiStateOverrideFactory by Fixture {
    { displayId: Int ->
        SysUIStateOverride(
            displayId,
            sceneContainerPlugin,
            dumpManager,
            sysUiState,
            sysUIStateDispatcher,
        )
    }
}
