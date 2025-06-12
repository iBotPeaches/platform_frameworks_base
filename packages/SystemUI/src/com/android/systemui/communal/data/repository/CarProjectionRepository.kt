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

package com.android.systemui.communal.data.repository

import android.app.UiModeManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface CarProjectionRepository {
    /** Whether car projection is active. */
    val projectionActive: Flow<Boolean>

    /**
     * Checks the system for the current car projection state.
     *
     * @return True if projection is active, false otherwise.
     */
    suspend fun isProjectionActive(): Boolean
}

@SysUISingleton
class CarProjectionRepositoryImpl
@Inject
constructor(
    private val uiModeManager: UiModeManager,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : CarProjectionRepository {
    override val projectionActive: Flow<Boolean> =
        conflatedCallbackFlow {
                val listener =
                    UiModeManager.OnProjectionStateChangedListener { _, _ -> trySend(Unit) }
                uiModeManager.addOnProjectionStateChangedListener(
                    UiModeManager.PROJECTION_TYPE_AUTOMOTIVE,
                    bgDispatcher.asExecutor(),
                    listener,
                )
                awaitClose { uiModeManager.removeOnProjectionStateChangedListener(listener) }
            }
            .emitOnStart()
            .map { isProjectionActive() }
            .flowOn(bgDispatcher)

    override suspend fun isProjectionActive(): Boolean =
        withContext(bgDispatcher) {
            (uiModeManager.activeProjectionTypes and UiModeManager.PROJECTION_TYPE_AUTOMOTIVE) != 0
        }
}
