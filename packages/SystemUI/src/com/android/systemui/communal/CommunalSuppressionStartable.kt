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

package com.android.systemui.communal

import com.android.systemui.CoreStartable
import com.android.systemui.communal.data.model.SuppressionReason
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.dagger.CommunalTableLog
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@SysUISingleton
class CommunalSuppressionStartable
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val suppressionFlows: Set<@JvmSuppressWildcards Flow<SuppressionReason?>>,
    private val communalSettingsInteractor: CommunalSettingsInteractor,
    @CommunalTableLog private val tableLogBuffer: TableLogBuffer,
) : CoreStartable {
    override fun start() {
        getSuppressionReasons()
            .onEach { reasons -> communalSettingsInteractor.setSuppressionReasons(reasons) }
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer,
                columnName = "suppressionReasons",
                initialValue = emptyList(),
            )
            .flowOn(bgDispatcher)
            .launchIn(applicationScope)
    }

    private fun getSuppressionReasons(): Flow<List<SuppressionReason>> {
        if (!communalSettingsInteractor.isCommunalFlagEnabled()) {
            return flowOf(listOf(SuppressionReason.ReasonFlagDisabled))
        }
        return combine(suppressionFlows) { reasons -> reasons.filterNotNull() }
    }
}
