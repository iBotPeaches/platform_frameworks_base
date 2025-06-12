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

import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import dagger.BindsInstance
import dagger.Subcomponent
import javax.inject.Qualifier
import javax.inject.Scope
import kotlinx.coroutines.CoroutineScope

/**
 * Subcomponent for SysUI classes that should be instantiated once per display.
 *
 * All display specific classes should be provided with the @DisplayAware annotation. Once the
 * display is removed, [displayCoroutineScope] gets cancelled. This means that if classes have some
 * teardown step it should be executed when the scope is cancelled. Also note that the scope is
 * cancelled in the background, so any teardown logic should be threadsafe. Cancelling on the main
 * thread is not feasible as it would cause jank.
 */
@PerDisplaySingleton
@Subcomponent(modules = [PerDisplayCommonModule::class])
interface SystemUIDisplaySubcomponent {

    @get:DisplayAware val displayCoroutineScope: CoroutineScope

    @Subcomponent.Factory
    interface Factory {
        fun create(@BindsInstance @DisplayId displayId: Int): SystemUIDisplaySubcomponent
    }

    /** Scope annotation for singletons associated to a display. */
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    @Scope
    annotation class PerDisplaySingleton

    /** Qualifier used to represent that the object is provided/bound with the proper display. */
    @Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class DisplayAware

    /** Annotates the display id inside the subcomponent. */
    @Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class DisplayId
}
