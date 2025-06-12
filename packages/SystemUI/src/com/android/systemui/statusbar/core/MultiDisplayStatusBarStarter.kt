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

package com.android.systemui.statusbar.core

import android.view.Display
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.data.repository.DisplayRepository
import com.android.systemui.statusbar.data.repository.LightBarControllerStore
import com.android.systemui.statusbar.data.repository.PrivacyDotWindowControllerStore
import com.android.systemui.util.kotlin.pairwiseBy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.onStart

/**
 * Responsible for creating and starting the status bar components for each display. Also does it
 * for newly added displays.
 */
@SysUISingleton
class MultiDisplayStatusBarStarter
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val multiDisplayStatusBarOrchestratorStore: MultiDisplayStatusBarOrchestratorStore,
    private val displayRepository: DisplayRepository,
    private val statusBarInitializerStore: StatusBarInitializerStore,
    private val privacyDotWindowControllerStore: PrivacyDotWindowControllerStore,
    private val lightBarControllerStore: LightBarControllerStore,
) : CoreStartable {

    init {
        StatusBarConnectedDisplays.unsafeAssertInNewMode()
    }

    override fun start() {
        applicationScope.launch {
            displayRepository.displayIdsWithSystemDecorations
                .pairwiseBy { previousDisplays, currentDisplays ->
                    currentDisplays - previousDisplays
                }
                .onStart { emit(displayRepository.displayIdsWithSystemDecorations.value) }
                .collect { newDisplays ->
                    newDisplays.forEach { createAndStartComponentsForDisplay(it) }
                }
        }
    }

    private fun createAndStartComponentsForDisplay(displayId: Int) {
        createAndStartOrchestratorForDisplay(displayId)
        createAndStartInitializerForDisplay(displayId)
        startPrivacyDotForDisplay(displayId)
        createLightBarControllerForDisplay(displayId)
    }

    private fun createLightBarControllerForDisplay(displayId: Int) {
        // Explicitly not calling `start()`, because the store is already calling `start()`.
        // This is to maintain the legacy behavior with NavigationBar, that was already expecting
        // LightBarController to start at construction time.
        lightBarControllerStore.forDisplay(displayId)
    }

    private fun createAndStartOrchestratorForDisplay(displayId: Int) {
        multiDisplayStatusBarOrchestratorStore.forDisplay(displayId)?.start()
    }

    private fun createAndStartInitializerForDisplay(displayId: Int) {
        statusBarInitializerStore.forDisplay(displayId)?.start()
    }

    private fun startPrivacyDotForDisplay(displayId: Int) {
        if (displayId == Display.DEFAULT_DISPLAY) {
            // For the default display, privacy dot is started via ScreenDecorations
            return
        }
        privacyDotWindowControllerStore.forDisplay(displayId)?.start()
    }
}
