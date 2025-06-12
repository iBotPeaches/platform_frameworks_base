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

package com.android.systemui.cursorposition.data.repository

import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.data.repository.DisplayRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Repository for cursor position of multi displays. */
interface MultiDisplayCursorPositionRepository {
    val cursorPositions: Flow<CursorPosition?>
}

/**
 * Implementation of [MultiDisplayCursorPositionRepository] that aggregates cursor position updates
 * from multiple displays.
 *
 * This class uses a [DisplayRepository] to track added displays and a [PerDisplayRepository] to
 * manage [SingleDisplayCursorPositionRepository] instances for each display. [PerDisplayRepository]
 * would destroy the instance if the display is removed. This class combines the cursor position
 * from all displays into a single cursorPositions StateFlow.
 */
@SysUISingleton
class MultiDisplayCursorPositionRepositoryImpl
@Inject
constructor(
    private val displayRepository: DisplayRepository,
    @Background private val backgroundScope: CoroutineScope,
    private val cursorRepositories: PerDisplayRepository<SingleDisplayCursorPositionRepository>,
) : MultiDisplayCursorPositionRepository {

    private val allDisplaysCursorPositions: Flow<CursorPosition> =
        displayRepository.displayAdditionEvent
            .mapNotNull { c -> c?.displayId }
            .onStart { emitAll(displayRepository.displayIds.value.asFlow()) }
            .flatMapMerge {
                val repo = cursorRepositories[it]
                repo?.cursorPositions ?: emptyFlow()
            }

    override val cursorPositions: StateFlow<CursorPosition?> =
        allDisplaysCursorPositions.stateIn(backgroundScope, SharingStarted.WhileSubscribed(), null)
}
