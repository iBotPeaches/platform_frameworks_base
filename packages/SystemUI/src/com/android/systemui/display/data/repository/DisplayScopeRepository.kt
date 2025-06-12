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
import com.android.app.displaylib.PerDisplayInstanceProvider
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Provides per display instances of [CoroutineScope].
 *
 * This is used to create a [PerDisplayRepository] of [CoroutineScope].
 *
 * Note this scope is cancelled when the display is removed, see [DisplayComponentRepository]. This
 * class is essentially only needed to reuse the application background scope for the default
 * display, and get the display scope from the correct [SystemUIDisplaySubcomponent].
 *
 * We should eventually delete this, when all classes using display scoped instances are in the
 * correct dagger scope ([PerDisplaySingleton])
 */
@SysUISingleton
class DisplayScopeRepositoryInstanceProvider
@Inject
constructor(
    @Background private val backgroundApplicationScope: CoroutineScope,
    private val displayComponentRepository: PerDisplayRepository<SystemUIDisplaySubcomponent>,
) : PerDisplayInstanceProvider<CoroutineScope> {

    override fun createInstance(displayId: Int): CoroutineScope? {
        return if (displayId == Display.DEFAULT_DISPLAY) {
            // The default display is connected all the time, therefore we can optimise by reusing
            // the application scope, and don't need to create a new scope.
            backgroundApplicationScope
        } else {
            // The scope is automatically cancelled from the component when the display is removed.
            displayComponentRepository[displayId]?.displayCoroutineScope
        }
    }
}

@Module
object PerDisplayCoroutineScopeRepositoryModule {
    @SysUISingleton
    @Provides
    fun provideDisplayCoroutineScopeRepository(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<CoroutineScope>,
        instanceProvider: DisplayScopeRepositoryInstanceProvider,
    ): PerDisplayRepository<CoroutineScope> {
        return repositoryFactory.create(
            debugName = "CoroutineScopePerDisplayRepo",
            instanceProvider,
        )
    }
}
