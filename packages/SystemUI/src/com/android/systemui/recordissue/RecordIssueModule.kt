/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.recordissue

import com.android.systemui.Flags
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.RecordIssueTile
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.base.ui.viewmodel.QSTileViewModel
import com.android.systemui.qs.tiles.base.ui.viewmodel.QSTileViewModelFactory
import com.android.systemui.qs.tiles.base.ui.viewmodel.StubQSTileViewModel
import com.android.systemui.qs.tiles.impl.irecording.data.model.IssueRecordingModel
import com.android.systemui.qs.tiles.impl.irecording.domain.interactor.IssueRecordingDataInteractor
import com.android.systemui.qs.tiles.impl.irecording.domain.interactor.IssueRecordingUserActionInteractor
import com.android.systemui.qs.tiles.impl.irecording.ui.mapper.IssueRecordingMapper
import com.android.systemui.res.R
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface RecordIssueModule {
    /** Inject RecordIssueTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(TILE_SPEC)
    fun bindRecordIssueTile(recordIssueTile: RecordIssueTile): QSTileImpl<*>

    companion object {

        const val TILE_SPEC = "record_issue"

        @Provides
        @IntoMap
        @StringKey(TILE_SPEC)
        fun provideRecordIssueTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.qs_record_issue_icon_off,
                        labelRes = R.string.qs_record_issue_label,
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        /** Inject FlashlightTile into tileViewModelMap in QSModule */
        @Provides
        @IntoMap
        @StringKey(TILE_SPEC)
        fun provideIssueRecordingTileViewModel(
            factory: QSTileViewModelFactory.Static<IssueRecordingModel>,
            mapper: IssueRecordingMapper,
            stateInteractor: IssueRecordingDataInteractor,
            userActionInteractor: IssueRecordingUserActionInteractor,
        ): QSTileViewModel =
            if (Flags.qsNewTilesFuture())
                factory.create(
                    TileSpec.create(TILE_SPEC),
                    userActionInteractor,
                    stateInteractor,
                    mapper,
                )
            else StubQSTileViewModel
    }
}
