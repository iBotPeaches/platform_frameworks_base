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

package com.android.systemui.dagger

import com.android.app.displaylib.DefaultDisplayOnlyInstanceRepositoryImpl
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.display.data.repository.DisplayComponentRepository
import com.android.systemui.display.data.repository.PerDisplayCoroutineScopeRepositoryModule
import com.android.systemui.model.SysUIStateInstanceProvider
import com.android.systemui.model.SysUiState
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import dagger.Module
import dagger.Provides

/** This module is meant to contain all the code to create the various [PerDisplayRepository<>]. */
@Module(
    includes = [PerDisplayCoroutineScopeRepositoryModule::class, DisplayComponentRepository::class]
)
class PerDisplayRepositoriesModule {

    @SysUISingleton
    @Provides
    fun provideSysUiStateRepository(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<SysUiState>,
        instanceProvider: SysUIStateInstanceProvider,
    ): PerDisplayRepository<SysUiState> {
        val debugName = "SysUiStatePerDisplayRepo"
        return if (ShadeWindowGoesAround.isEnabled) {
            repositoryFactory.create(debugName, instanceProvider)
        } else {
            DefaultDisplayOnlyInstanceRepositoryImpl(debugName, instanceProvider)
        }
    }
}
