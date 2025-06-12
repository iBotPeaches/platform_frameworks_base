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

package com.android.systemui.qs.panels.domain

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.mainResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.backup.BackupHelper.Companion.ACTION_RESTORE_FINISHED
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.common.shared.model.PackageChangeModel.Empty.packageName
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.qs.panels.data.repository.QSPreferencesRepository
import com.android.systemui.qs.panels.data.repository.defaultLargeTilesRepository
import com.android.systemui.qs.panels.domain.interactor.qsPreferencesInteractor
import com.android.systemui.qs.pipeline.data.repository.DefaultTilesQSHostRepository
import com.android.systemui.qs.pipeline.data.repository.defaultTilesRepository
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.pipeline.shared.TilesUpgradePath
import com.android.systemui.settings.userFileManager
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.userRepository
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LargeTilesUpgradePathsTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().apply { defaultTilesRepository = DefaultTilesQSHostRepository(mainResources) }

    private val defaultTiles = kosmos.defaultTilesRepository.defaultTiles.toSet()

    private val underTest = kosmos.qsPreferencesInteractor

    private val Kosmos.userId
        get() = userRepository.getSelectedUserInfo().id

    private val Kosmos.intent
        get() =
            Intent(ACTION_RESTORE_FINISHED).apply {
                `package` = packageName
                putExtra(Intent.EXTRA_USER_ID, kosmos.userId)
                flags = Intent.FLAG_RECEIVER_REGISTERED_ONLY
            }

    /**
     * This test corresponds to the case of a fresh start.
     *
     * The resulting large tiles are the default set of large tiles.
     */
    @Test
    fun defaultTiles_noDataInSharedPreferences_defaultLargeTiles() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)

            underTest.setInitialOrUpgradeLargeTilesSpecs(TilesUpgradePath.DefaultSet, userId)

            assertThat(largeTiles).isEqualTo(defaultLargeTilesRepository.defaultLargeTiles)
        }

    /**
     * This test corresponds to a user that upgraded in place from a build that didn't support large
     * tiles to one that does. The current tiles of the user are read from settings.
     *
     * The resulting large tiles are those that were read from Settings.
     */
    @Test
    fun upgradeInPlace_noDataInSharedPreferences_allLargeTiles() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)
            val tiles = setOf("a", "b", "c").toTileSpecs()

            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.ReadFromSettings(tiles),
                userId,
            )

            assertThat(largeTiles).isEqualTo(tiles)
        }

    /**
     * This test corresponds to a fresh start, and then the user restarts the device, without ever
     * having modified the set of large tiles.
     *
     * The resulting large tiles are the default large tiles that were set on the fresh start
     */
    @Test
    fun defaultSet_restartDevice_largeTilesDontChange() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)

            underTest.setInitialOrUpgradeLargeTilesSpecs(TilesUpgradePath.DefaultSet, userId)

            // User restarts the device, this will send a read from settings with the default
            // set of tiles

            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.ReadFromSettings(defaultTiles),
                userId,
            )

            assertThat(largeTiles).isEqualTo(defaultLargeTilesRepository.defaultLargeTiles)
        }

    /**
     * This test corresponds to a fresh start, following the user changing the sizes of some tiles.
     * After that, the user restarts the device.
     *
     * The resulting set of large tiles are those that the user determined before restarting the
     * device.
     */
    @Test
    fun defaultSet_someSizeChanges_restart_correctSet() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)
            underTest.setInitialOrUpgradeLargeTilesSpecs(TilesUpgradePath.DefaultSet, userId)

            underTest.setLargeTilesSpecs(largeTiles!! + setOf("a", "b").toTileSpecs())
            val largeTilesBeforeRestart = largeTiles!!

            // Restart

            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.ReadFromSettings(defaultTiles),
                userId,
            )
            assertThat(largeTiles).isEqualTo(largeTilesBeforeRestart)
        }

    /**
     * This test corresponds to a user that upgraded, and after that performed some size changes.
     * After that, the user restarts the device.
     *
     * The resulting set of large tiles are those that the user determined before restarting the
     * device.
     */
    @Test
    fun readFromSettings_changeSizes_restart_newLargeSet() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)
            val readTiles = setOf("a", "b", "c").toTileSpecs()

            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.ReadFromSettings(readTiles),
                userId,
            )
            underTest.setLargeTilesSpecs(emptySet())

            assertThat(largeTiles).isEmpty()

            // Restart
            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.ReadFromSettings(readTiles),
                userId,
            )
            assertThat(largeTiles).isEmpty()
        }

    /**
     * This test corresponds to a user that upgraded from a build that didn't support tile sizes to
     * one that does, via restore from backup. Note that there's no file in SharedPreferences to
     * restore.
     *
     * The resulting set of large tiles are those that were restored from the backup.
     */
    @Test
    fun restoreFromBackup_noDataInSharedPreferences_allLargeTiles() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)
            val tiles = setOf("a", "b", "c").toTileSpecs()

            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.RestoreFromBackup(tiles),
                userId,
            )

            assertThat(largeTiles).isEqualTo(tiles)
        }

    /**
     * This test corresponds to a user that upgraded from a build that didn't support tile sizes to
     * one that does, via restore from backup. However, the restore happens after SystemUI's
     * initialization has set the tiles to default. Note that there's no file in SharedPreferences
     * to restore.
     *
     * The resulting set of large tiles are those that were restored from the backup.
     */
    @Test
    fun restoreFromBackup_afterDefault_noDataInSharedPreferences_allLargeTiles() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)
            underTest.setInitialOrUpgradeLargeTilesSpecs(TilesUpgradePath.DefaultSet, userId)

            val tiles = setOf("a", "b", "c").toTileSpecs()

            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.RestoreFromBackup(tiles),
                userId,
            )

            assertThat(largeTiles).isEqualTo(tiles)
        }

    /**
     * This test corresponds to a user that restored from a build that supported different sizes
     * tiles. First the list of tiles is restored in Settings and then a file containing some large
     * tiles overrides the current shared preferences file
     *
     * The resulting set of large tiles are those that were restored from the shared preferences
     * backup (and not the full list).
     */
    @Test
    fun restoreFromBackup_thenRestoreOfSharedPrefs_sharedPrefsAreLarge() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)
            val tiles = setOf("a", "b", "c").toTileSpecs()
            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.RestoreFromBackup(tiles),
                userId,
            )

            val tilesFromBackupOfSharedPrefs = setOf("a")
            setLargeTilesSpecsInSharedPreferences(tilesFromBackupOfSharedPrefs)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)

            assertThat(largeTiles).isEqualTo(tilesFromBackupOfSharedPrefs.toTileSpecs())
        }

    /**
     * This test corresponds to a user that restored from a build that supported different sizes
     * tiles. However, this restore of settings happened after SystemUI's restore of the SharedPrefs
     * containing the user's previous selections to large/small tiles.
     *
     * The resulting set of large tiles are those that were restored from the shared preferences
     * backup (and not the full list).
     */
    @Test
    fun restoreFromBackup_afterRestoreOfSharedPrefs_sharedPrefsAreLarge() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)
            val tiles = setOf("a", "b", "c").toTileSpecs()
            val tilesFromBackupOfSharedPrefs = setOf("a")

            setLargeTilesSpecsInSharedPreferences(tilesFromBackupOfSharedPrefs)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)

            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.RestoreFromBackup(tiles),
                userId,
            )

            assertThat(largeTiles).isEqualTo(tilesFromBackupOfSharedPrefs.toTileSpecs())
        }

    /**
     * This test corresponds to a user that upgraded from a build that didn't support tile sizes to
     * one that does, via restore from backup. After that, the user modifies the size of some tiles
     * and then restarts the device.
     *
     * The resulting set of large tiles are those after the user modifications.
     */
    @Test
    fun restoreFromBackup_changeSizes_restart_newLargeSet() =
        kosmos.runTest {
            val largeTiles by collectLastValue(underTest.largeTilesSpecs)
            val readTiles = setOf("a", "b", "c").toTileSpecs()

            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.RestoreFromBackup(readTiles),
                userId,
            )
            underTest.setLargeTilesSpecs(emptySet())

            assertThat(largeTiles).isEmpty()

            // Restart
            underTest.setInitialOrUpgradeLargeTilesSpecs(
                TilesUpgradePath.ReadFromSettings(readTiles),
                userId,
            )
            assertThat(largeTiles).isEmpty()
        }

    private companion object {
        private const val LARGE_TILES_SPECS_KEY = "large_tiles_specs"

        private fun Kosmos.getSharedPreferences(): SharedPreferences =
            userFileManager.getSharedPreferences(
                QSPreferencesRepository.FILE_NAME,
                Context.MODE_PRIVATE,
                userRepository.getSelectedUserInfo().id,
            )

        private fun Kosmos.setLargeTilesSpecsInSharedPreferences(specs: Set<String>) {
            getSharedPreferences().edit().putStringSet(LARGE_TILES_SPECS_KEY, specs).apply()
        }

        private fun Kosmos.getLargeTilesSpecsFromSharedPreferences(): Set<String> {
            return getSharedPreferences().getStringSet(LARGE_TILES_SPECS_KEY, emptySet())!!
        }

        private fun Set<String>.toTileSpecs(): Set<TileSpec> {
            return map { TileSpec.create(it) }.toSet()
        }
    }
}
