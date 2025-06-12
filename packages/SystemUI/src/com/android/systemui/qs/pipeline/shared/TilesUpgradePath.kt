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

package com.android.systemui.qs.pipeline.shared

/** Upgrade paths indicating the source of the list of QS tiles. */
sealed interface TilesUpgradePath {

    sealed interface UpgradeWithTiles : TilesUpgradePath {
        val value: Set<TileSpec>
    }

    /** This indicates a set of tiles that was read from Settings on user start */
    @JvmInline value class ReadFromSettings(override val value: Set<TileSpec>) : UpgradeWithTiles

    /** This indicates a set of tiles that was restored from backup */
    @JvmInline value class RestoreFromBackup(override val value: Set<TileSpec>) : UpgradeWithTiles

    /**
     * This indicates that no tiles were read from Settings on user start so the default has been
     * stored.
     */
    data object DefaultSet : TilesUpgradePath
}
