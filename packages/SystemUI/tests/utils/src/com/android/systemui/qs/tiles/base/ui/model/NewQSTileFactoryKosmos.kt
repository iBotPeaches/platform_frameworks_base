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

package com.android.systemui.qs.tiles.base.ui.model

import android.os.UserHandle
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.qs.instanceIdSequenceFake
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileUserAction
import com.android.systemui.qs.tiles.base.shared.model.qSTileConfigProvider
import com.android.systemui.qs.tiles.base.ui.viewmodel.QSTileViewModel
import com.android.systemui.qs.tiles.base.ui.viewmodel.QSTileViewModelFactory
import com.android.systemui.qs.tiles.base.ui.viewmodel.qsTileViewModelAdaperFactory
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

var Kosmos.newFactoryTileMap by Kosmos.Fixture { emptyMap<String, Provider<QSTileViewModel>>() }

val Kosmos.customTileViewModelFactory: QSTileViewModelFactory.Component by
    Kosmos.Fixture {
        mock {
            whenever(create(any())).thenAnswer { invocation ->
                val tileSpec = invocation.getArgument<TileSpec>(0)
                val config =
                    QSTileConfig(
                        tileSpec,
                        QSTileUIConfig.Empty,
                        instanceIdSequenceFake.newInstanceId(),
                        category = TileCategory.PROVIDED_BY_APP,
                    )
                object : QSTileViewModel {
                    override val state: StateFlow<QSTileState?> =
                        MutableStateFlow(QSTileState.build(null, tileSpec.spec) {})
                    override val config: QSTileConfig = config
                    override val isAvailable: StateFlow<Boolean> = MutableStateFlow(true)

                    override var currentTileUser = currentTilesInteractor.userId.value

                    override fun onUserChanged(user: UserHandle) {
                        currentTileUser = user.identifier
                    }

                    override fun forceUpdate() {}

                    override fun onActionPerformed(userAction: QSTileUserAction) {}

                    override fun destroy() {}
                }
            }
        }
    }

var Kosmos.newQSTileFactory by
    Kosmos.Fixture {
        NewQSTileFactory(
            qSTileConfigProvider,
            qsTileViewModelAdaperFactory,
            newFactoryTileMap,
            customTileViewModelFactory,
        )
    }
