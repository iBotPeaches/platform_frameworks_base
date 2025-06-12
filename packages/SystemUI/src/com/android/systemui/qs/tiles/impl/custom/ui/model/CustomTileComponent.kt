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

package com.android.systemui.qs.tiles.impl.custom.ui.model

import com.android.systemui.qs.tiles.base.shared.model.QSTileScope
import com.android.systemui.qs.tiles.base.ui.model.QSTileComponent
import com.android.systemui.qs.tiles.impl.custom.data.repository.CustomTilePackageUpdatesRepository
import com.android.systemui.qs.tiles.impl.custom.domain.interactor.CustomTileInteractor
import com.android.systemui.qs.tiles.impl.custom.domain.interactor.CustomTileServiceInteractor
import com.android.systemui.qs.tiles.impl.custom.domain.model.CustomTileDataModel
import com.android.systemui.qs.tiles.impl.custom.shared.model.QSTileConfigModule
import dagger.Subcomponent

@QSTileScope
@Subcomponent(modules = [QSTileConfigModule::class, CustomTileModule::class])
interface CustomTileComponent : QSTileComponent<CustomTileDataModel> {

    fun customTileInterfaceInteractor(): CustomTileServiceInteractor

    fun customTileInteractor(): CustomTileInteractor

    fun customTilePackageUpdatesRepository(): CustomTilePackageUpdatesRepository

    @Subcomponent.Builder
    interface Builder {

        fun qsTileConfigModule(module: QSTileConfigModule): Builder

        fun build(): CustomTileComponent
    }
}
