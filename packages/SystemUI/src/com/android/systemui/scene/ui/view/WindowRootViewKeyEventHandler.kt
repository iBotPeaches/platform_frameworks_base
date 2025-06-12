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

package com.android.systemui.scene.ui.view

import android.view.KeyEvent
import com.android.systemui.classifier.FalsingCollector
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyevent.domain.interactor.SysUIKeyEventHandler
import dagger.Lazy
import javax.inject.Inject

@SysUISingleton
class WindowRootViewKeyEventHandler
@Inject
constructor(
    val sysUIKeyEventHandlerLazy: Lazy<SysUIKeyEventHandler>,
    val falsingCollector: FalsingCollector,
) {
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return sysUIKeyEventHandlerLazy.get().dispatchKeyEvent(event)
    }

    fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        return sysUIKeyEventHandlerLazy.get().dispatchKeyEventPreIme(event)
    }

    fun interceptMediaKey(event: KeyEvent): Boolean {
        return sysUIKeyEventHandlerLazy.get().interceptMediaKey(event)
    }

    /** Collects the KeyEvent without intercepting it. */
    fun collectKeyEvent(event: KeyEvent) {
        falsingCollector.onKeyEvent(event)
    }
}
