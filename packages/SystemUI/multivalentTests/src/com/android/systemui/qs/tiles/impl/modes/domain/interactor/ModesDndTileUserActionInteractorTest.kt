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

package com.android.systemui.qs.tiles.impl.modes.domain.interactor

import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.notification.modes.TestModeBuilder.MANUAL_DND
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.mainCoroutineContext
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.shared.QSSettingsPackageRepository
import com.android.systemui.qs.tiles.base.domain.actions.QSTileIntentUserInputHandlerSubject
import com.android.systemui.qs.tiles.base.domain.actions.qsTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.domain.model.QSTileInputTestKtx
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesDndTileModel
import com.android.systemui.statusbar.policy.data.repository.zenModeRepository
import com.android.systemui.statusbar.policy.domain.interactor.zenModeInteractor
import com.android.systemui.statusbar.policy.ui.dialog.mockModesDialogDelegate
import com.android.systemui.statusbar.policy.ui.dialog.modesDialogEventLogger
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(android.app.Flags.FLAG_MODES_UI)
class ModesDndTileUserActionInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val inputHandler = kosmos.qsTileIntentUserInputHandler
    private val mockDialogDelegate = kosmos.mockModesDialogDelegate
    private val zenModeRepository = kosmos.zenModeRepository
    private val zenModeInteractor = kosmos.zenModeInteractor
    private val settingsPackageRepository = mock<QSSettingsPackageRepository>()

    private val underTest =
        ModesDndTileUserActionInteractor(
            kosmos.mainCoroutineContext,
            inputHandler,
            mockDialogDelegate,
            zenModeInteractor,
            kosmos.modesDialogEventLogger,
            settingsPackageRepository,
        )

    @Before
    fun setUp() {
        whenever(settingsPackageRepository.getSettingsPackageName()).thenReturn(SETTINGS_PACKAGE)
    }

    @Test
    fun handleClick_dndActive_deactivatesDnd() =
        testScope.runTest {
            val dndMode by collectLastValue(zenModeInteractor.dndMode)
            zenModeRepository.activateMode(MANUAL_DND)
            assertThat(dndMode?.isActive).isTrue()

            underTest.handleInput(QSTileInputTestKtx.click(data = ModesDndTileModel(true, null)))

            assertThat(dndMode?.isActive).isFalse()
        }

    @Test
    fun handleClick_dndInactive_activatesDnd() =
        testScope.runTest {
            val dndMode by collectLastValue(zenModeInteractor.dndMode)
            assertThat(dndMode?.isActive).isFalse()

            underTest.handleInput(QSTileInputTestKtx.click(data = ModesDndTileModel(false, null)))

            assertThat(dndMode?.isActive).isTrue()
        }

    @Test
    fun handleLongClick_active_opensSettings() =
        testScope.runTest {
            zenModeRepository.activateMode(MANUAL_DND)
            runCurrent()

            underTest.handleInput(QSTileInputTestKtx.longClick(ModesDndTileModel(true, null)))

            QSTileIntentUserInputHandlerSubject.assertThat(inputHandler).handledOneIntentInput {
                assertThat(it.intent.`package`).isEqualTo(SETTINGS_PACKAGE)
                assertThat(it.intent.action).isEqualTo(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS)
                assertThat(it.intent.getStringExtra(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID))
                    .isEqualTo(MANUAL_DND.id)
            }
        }

    @Test
    fun handleLongClick_inactive_opensSettings() =
        testScope.runTest {
            zenModeRepository.activateMode(MANUAL_DND)
            zenModeRepository.deactivateMode(MANUAL_DND)
            runCurrent()

            underTest.handleInput(QSTileInputTestKtx.longClick(ModesDndTileModel(false, null)))

            QSTileIntentUserInputHandlerSubject.assertThat(inputHandler).handledOneIntentInput {
                assertThat(it.intent.`package`).isEqualTo(SETTINGS_PACKAGE)
                assertThat(it.intent.action).isEqualTo(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS)
                assertThat(it.intent.getStringExtra(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID))
                    .isEqualTo(MANUAL_DND.id)
            }
        }

    companion object {
        private const val SETTINGS_PACKAGE = "the.settings.package"
    }
}
