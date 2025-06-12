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

package com.android.systemui.qs.panels.data.repository

import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import com.android.systemui.backup.BackupHelper
import com.android.systemui.backup.BackupHelper.Companion.ACTION_RESTORE_FINISHED
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.qs.panels.shared.model.PanelsLog
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.pipeline.shared.TilesUpgradePath
import com.android.systemui.settings.UserFileManager
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.util.kotlin.SharedPreferencesExt.observe
import com.android.systemui.util.kotlin.emitOnStart
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** Repository for QS user preferences. */
@SysUISingleton
class QSPreferencesRepository
@Inject
constructor(
    private val userFileManager: UserFileManager,
    private val userRepository: UserRepository,
    private val defaultLargeTilesRepository: DefaultLargeTilesRepository,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @PanelsLog private val logBuffer: LogBuffer,
    broadcastDispatcher: BroadcastDispatcher,
) {
    private val logger by lazy { Logger(logBuffer, TAG) }

    private val backupRestorationEvents: Flow<Unit> =
        broadcastDispatcher
            .broadcastFlow(
                filter = IntentFilter(ACTION_RESTORE_FINISHED),
                flags = Context.RECEIVER_NOT_EXPORTED,
                permission = BackupHelper.PERMISSION_SELF,
            )
            .onEach { logger.i("Restored state for QS preferences.") }
            .emitOnStart()

    /** Set of [TileSpec] to display as large tiles for the current user. */
    val largeTilesSpecs: Flow<Set<TileSpec>> =
        combine(backupRestorationEvents, userRepository.selectedUserInfo, ::Pair)
            .flatMapLatest { (_, userInfo) ->
                val prefs = getSharedPrefs(userInfo.id)
                prefs.observe().emitOnStart().map {
                    prefs
                        .getStringSet(
                            LARGE_TILES_SPECS_KEY,
                            defaultLargeTilesRepository.defaultLargeTiles.map { it.spec }.toSet(),
                        )
                        ?.map { TileSpec.create(it) }
                        ?.toSet() ?: defaultLargeTilesRepository.defaultLargeTiles
                }
            }
            .flowOn(backgroundDispatcher)

    /** Sets for the current user the set of [TileSpec] to display as large tiles. */
    fun writeLargeTileSpecs(specs: Set<TileSpec>) {
        with(getSharedPrefs(userRepository.getSelectedUserInfo().id)) {
            writeLargeTileSpecs(specs)
            setLargeTilesDefault(false)
        }
    }

    suspend fun deleteLargeTileDataJob() {
        userRepository.selectedUserInfo.collect { userInfo ->
            getSharedPrefs(userInfo.id)
                .edit()
                .remove(LARGE_TILES_SPECS_KEY)
                .remove(LARGE_TILES_DEFAULT_KEY)
                .apply()
        }
    }

    private fun SharedPreferences.writeLargeTileSpecs(specs: Set<TileSpec>) {
        edit().putStringSet(LARGE_TILES_SPECS_KEY, specs.map { it.spec }.toSet()).apply()
    }

    /**
     * Sets the initial set of large tiles. One of the following cases will happen:
     * * If we are setting the default set (no value stored in settings for the list of tiles), set
     *   the large tiles based on [defaultLargeTilesRepository]. We do this to signal future reboots
     *   that we have performed the upgrade path once. In this case, we will mark that we set them
     *   as the default in case a restore needs to modify them later.
     * * If we got a list of tiles restored from a device and nothing has modified the list of
     *   tiles, set all the restored tiles to large. Note that if we also restored a set of large
     *   tiles before this was called, [LARGE_TILES_DEFAULT_KEY] will be false and we won't
     *   overwrite it.
     * * If we got a list of tiles from settings, we consider that we upgraded in place and then we
     *   will set all those tiles to large IF there's no current set of large tiles.
     *
     * Even if largeTilesSpec is read Eagerly before we know if we are in an initial state, because
     * we are not writing the default values to the SharedPreferences, the file will not contain the
     * key and this call will succeed, as long as there hasn't been any calls to setLargeTilesSpecs
     * for that user before.
     */
    fun setInitialOrUpgradeLargeTiles(upgradePath: TilesUpgradePath, userId: Int) {
        with(getSharedPrefs(userId)) {
            when (upgradePath) {
                is TilesUpgradePath.DefaultSet -> {
                    writeLargeTileSpecs(defaultLargeTilesRepository.defaultLargeTiles)
                    logger.i("Large tiles set to default on init")
                    setLargeTilesDefault(true)
                }
                is TilesUpgradePath.RestoreFromBackup -> {
                    if (
                        getBoolean(LARGE_TILES_DEFAULT_KEY, false) ||
                            !contains(LARGE_TILES_SPECS_KEY)
                    ) {
                        writeLargeTileSpecs(upgradePath.value)
                        logger.i("Tiles restored from backup set to large: ${upgradePath.value}")
                        setLargeTilesDefault(false)
                    }
                }
                is TilesUpgradePath.ReadFromSettings -> {
                    if (!contains(LARGE_TILES_SPECS_KEY)) {
                        writeLargeTileSpecs(upgradePath.value)
                        logger.i("Tiles read from settings set to large: ${upgradePath.value}")
                        setLargeTilesDefault(false)
                    }
                }
            }
        }
    }

    private fun SharedPreferences.setLargeTilesDefault(value: Boolean) {
        edit().putBoolean(LARGE_TILES_DEFAULT_KEY, value).apply()
    }

    private fun getSharedPrefs(userId: Int): SharedPreferences {
        return userFileManager.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE, userId)
    }

    companion object {
        private const val TAG = "QSPreferencesRepository"
        private const val LARGE_TILES_SPECS_KEY = "large_tiles_specs"
        private const val LARGE_TILES_DEFAULT_KEY = "large_tiles_default"
        const val FILE_NAME = "quick_settings_prefs"
    }
}
