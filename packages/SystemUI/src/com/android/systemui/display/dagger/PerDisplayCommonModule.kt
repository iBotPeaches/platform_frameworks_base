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

package com.android.systemui.display.dagger

import android.content.Context
import android.view.Display
import com.android.app.displaylib.DisplayRepository
import com.android.systemui.coroutines.newTracingContext
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** Module providing common dependencies for per-display singletons. */
@Module
class PerDisplayCommonModule {

    @Provides
    @PerDisplaySingleton
    fun provideDisplay(@DisplayId displayId: Int, displayRepository: DisplayRepository): Display {
        return displayRepository.getDisplay(displayId)
            ?: error("Couldn't get the display with id=$displayId")
    }

    @Provides
    @PerDisplaySingleton
    @DisplayAware
    fun provideDisplayContext(
        display: Display,
        @Application context: Context,
    ): Context {
        return context.createDisplayContext(display)
    }

    @Provides
    @PerDisplaySingleton
    @DisplayAware
    fun provideDisplayCoroutineScope(
        @Background backgroundDispatcher: CoroutineDispatcher,
        @DisplayId displayId: Int,
    ): CoroutineScope {
        return CoroutineScope(
            backgroundDispatcher + newTracingContext("DisplayScope(id=$displayId)")
        )
    }
}
