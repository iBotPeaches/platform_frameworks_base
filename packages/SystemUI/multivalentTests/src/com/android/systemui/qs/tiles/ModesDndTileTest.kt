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

package com.android.systemui.qs.tiles

import android.app.Flags
import android.os.Handler
import android.platform.test.annotations.EnableFlags
import android.service.quicksettings.Tile
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.MetricsLogger
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.systemui.SysuiTestCase
import com.android.systemui.classifier.FalsingManagerFake
import com.android.systemui.kosmos.mainCoroutineContext
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.shared.QSSettingsPackageRepository
import com.android.systemui.qs.tiles.base.domain.actions.FakeQSTileIntentUserInputHandler
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigProvider
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.impl.modes.domain.interactor.ModesDndTileDataInteractor
import com.android.systemui.qs.tiles.impl.modes.domain.interactor.ModesDndTileUserActionInteractor
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesDndTileModel
import com.android.systemui.qs.tiles.impl.modes.ui.ModesDndTileMapper
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.data.repository.zenModeRepository
import com.android.systemui.statusbar.policy.domain.interactor.zenModeInteractor
import com.android.systemui.statusbar.policy.ui.dialog.ModesDialogDelegate
import com.android.systemui.statusbar.policy.ui.dialog.modesDialogEventLogger
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.settings.FakeSettings
import com.android.systemui.util.settings.SecureSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@EnableFlags(Flags.FLAG_MODES_UI)
@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
class ModesDndTileTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val testDispatcher = kosmos.testDispatcher

    @Mock private lateinit var qsHost: QSHost

    @Mock private lateinit var metricsLogger: MetricsLogger

    @Mock private lateinit var statusBarStateController: StatusBarStateController

    @Mock private lateinit var activityStarter: ActivityStarter

    @Mock private lateinit var qsLogger: QSLogger

    @Mock private lateinit var uiEventLogger: QsEventLogger

    @Mock private lateinit var qsTileConfigProvider: QSTileConfigProvider

    @Mock private lateinit var dialogDelegate: ModesDialogDelegate

    @Mock private lateinit var settingsPackageRepository: QSSettingsPackageRepository

    private val inputHandler = FakeQSTileIntentUserInputHandler()
    private val zenModeRepository = kosmos.zenModeRepository
    private val tileDataInteractor =
        ModesDndTileDataInteractor(context, kosmos.zenModeInteractor, testDispatcher)
    private val mapper = ModesDndTileMapper(context.resources, context.theme)

    private lateinit var userActionInteractor: ModesDndTileUserActionInteractor
    private lateinit var secureSettings: SecureSettings
    private lateinit var testableLooper: TestableLooper
    private lateinit var underTest: ModesDndTile

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        testableLooper = TestableLooper.get(this)
        secureSettings = FakeSettings()

        // Allow the tile to load resources
        whenever(qsHost.context).thenReturn(context)
        whenever(qsHost.userContext).thenReturn(context)

        whenever(qsTileConfigProvider.getConfig(any()))
            .thenReturn(
                QSTileConfigTestBuilder.build {
                    uiConfig =
                        QSTileUIConfig.Resource(
                            iconRes = R.drawable.qs_dnd_icon_off,
                            labelRes = R.string.quick_settings_dnd_label,
                        )
                }
            )

        userActionInteractor =
            ModesDndTileUserActionInteractor(
                kosmos.mainCoroutineContext,
                inputHandler,
                dialogDelegate,
                kosmos.zenModeInteractor,
                kosmos.modesDialogEventLogger,
                settingsPackageRepository,
            )

        underTest =
            ModesDndTile(
                qsHost,
                uiEventLogger,
                testableLooper.looper,
                Handler(testableLooper.looper),
                FalsingManagerFake(),
                metricsLogger,
                statusBarStateController,
                activityStarter,
                qsLogger,
                qsTileConfigProvider,
                tileDataInteractor,
                mapper,
                userActionInteractor,
            )

        underTest.initialize()
        underTest.setListening(Object(), true)

        testableLooper.processAllMessages()
    }

    @After
    fun tearDown() {
        underTest.destroy()
        testableLooper.processAllMessages()
    }

    @Test
    fun stateUpdatesOnChange() =
        testScope.runTest {
            assertThat(underTest.state.state).isEqualTo(Tile.STATE_INACTIVE)

            zenModeRepository.activateMode(TestModeBuilder.MANUAL_DND)
            runCurrent()
            testableLooper.processAllMessages()

            assertThat(underTest.state.state).isEqualTo(Tile.STATE_ACTIVE)
        }

    @Test
    fun handleUpdateState_withModel_updatesState() =
        testScope.runTest {
            val tileState =
                BooleanState().apply {
                    state = Tile.STATE_INACTIVE
                    secondaryLabel = "Old secondary label"
                }
            val model = ModesDndTileModel(isActivated = true, extraStatus = "Until 14:30")

            underTest.handleUpdateState(tileState, model)

            assertThat(tileState.state).isEqualTo(Tile.STATE_ACTIVE)
            assertThat(tileState.secondaryLabel).isEqualTo("Until 14:30")
        }

    @Test
    fun handleUpdateState_withNull_updatesState() =
        testScope.runTest {
            val tileState =
                BooleanState().apply {
                    state = Tile.STATE_INACTIVE
                    secondaryLabel = "Old secondary label"
                }
            zenModeRepository.activateMode(TestModeBuilder.MANUAL_DND)
            runCurrent()

            underTest.handleUpdateState(tileState, null)

            assertThat(tileState.state).isEqualTo(Tile.STATE_ACTIVE)
            assertThat(tileState.secondaryLabel).isEqualTo(null) // Tile will use default On text
        }
}
