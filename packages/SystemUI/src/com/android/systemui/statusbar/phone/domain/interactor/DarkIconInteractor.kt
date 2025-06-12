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
package com.android.systemui.statusbar.phone.domain.interactor

import android.graphics.Rect
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher.DarkChange
import com.android.systemui.statusbar.phone.data.repository.DarkIconRepository
import com.android.systemui.statusbar.phone.domain.model.DarkState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** States pertaining to calculating colors for icons in dark mode. */
@SysUISingleton
class DarkIconInteractor @Inject constructor(private val repository: DarkIconRepository) {
    /** Dark-mode state for tinting icons. */
    fun darkState(displayId: Int): Flow<DarkState> =
        repository.darkState(displayId).map { DarkState(it.areas, it.tint, it.darkIntensity) }

    /**
     * Given a display id: returns a flow of [IsAreaDark], a function that can tell you if a given
     * [Rect] should be tinted dark or not. This flow ignores [DarkChange.tint] and
     * [DarkChange.darkIntensity]
     */
    fun isAreaDark(displayId: Int): Flow<IsAreaDark> {
        return repository.darkState(displayId).toIsAreaDark()
    }

    companion object {
        /**
         * Convenience function to convert between the repository's [darkState] into [IsAreaDark]
         * type flows.
         */
        @JvmStatic
        fun Flow<DarkChange>.toIsAreaDark(): Flow<IsAreaDark> =
            map { darkChange ->
                    DarkStateWithoutIntensity(darkChange.areas, darkChange.darkIntensity < 0.5f)
                }
                .distinctUntilChanged()
                .map { darkState ->
                    IsAreaDark { viewBounds: Rect ->
                        if (DarkIconDispatcher.isInAreas(darkState.areas, viewBounds)) {
                            darkState.isDark
                        } else {
                            false
                        }
                    }
                }
                .conflate()
                .distinctUntilChanged()
    }
}

/** So we can map between [DarkState] and a single boolean, but based on intensity */
private data class DarkStateWithoutIntensity(val areas: Collection<Rect>, val isDark: Boolean)

/** Given a region on screen, determine if the foreground should be dark or light */
fun interface IsAreaDark {
    fun isDark(viewBounds: Rect): Boolean
}
