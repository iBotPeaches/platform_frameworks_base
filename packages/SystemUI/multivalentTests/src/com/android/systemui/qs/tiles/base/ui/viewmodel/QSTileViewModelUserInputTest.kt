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

package com.android.systemui.qs.tiles.base.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.classifier.FalsingManagerFake
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.FakeTileDetailsViewModel
import com.android.systemui.qs.tiles.base.domain.interactor.FakeDisabledByPolicyInteractor
import com.android.systemui.qs.tiles.base.domain.interactor.FakeDisabledByPolicyInteractor.Companion.DISABLED_RESTRICTION
import com.android.systemui.qs.tiles.base.domain.interactor.FakeDisabledByPolicyInteractor.Companion.DISABLED_RESTRICTION_2
import com.android.systemui.qs.tiles.base.domain.interactor.FakeDisabledByPolicyInteractor.Companion.ENABLED_RESTRICTION
import com.android.systemui.qs.tiles.base.domain.interactor.FakeQSTileDataInteractor
import com.android.systemui.qs.tiles.base.domain.interactor.FakeQSTileUserActionInteractor
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.qs.tiles.base.shared.logging.QSTileLogger
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTilePolicy
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.shared.model.QSTileUserAction
import com.android.systemui.qs.tiles.base.ui.analytics.QSTileAnalytics
import com.android.systemui.qs.tiles.base.ui.model.QSTileDataToStateMapper
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/** Tests all possible [QSTileUserAction]s. If you need */
@MediumTest
@RunWith(AndroidJUnit4::class)
class QSTileViewModelUserInputTest : SysuiTestCase() {

    @Mock private lateinit var qsTileLogger: QSTileLogger
    @Mock private lateinit var qsTileAnalytics: QSTileAnalytics

    // TODO(b/299909989): this should be parametrised. b/299096521 blocks this.
    private val userAction: QSTileUserAction = QSTileUserAction.Click(null)

    private var tileConfig =
        QSTileConfigTestBuilder.build {
            policy = QSTilePolicy.Restricted(listOf(ENABLED_RESTRICTION))
        }

    private val userRepository = FakeUserRepository()
    private val tileDataInteractor = FakeQSTileDataInteractor<String>()
    private val tileUserActionInteractor = FakeQSTileUserActionInteractor<String>()
    private val disabledByPolicyInteractor = FakeDisabledByPolicyInteractor()
    private val falsingManager = FalsingManagerFake()

    private val testCoroutineDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testCoroutineDispatcher)

    private lateinit var underTest: QSTileViewModel

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest = createViewModel(testScope)
    }

    @Test
    fun userInputTriggersData() =
        testScope.runTest {
            tileDataInteractor.emitData("initial_data")
            underTest.state.launchIn(backgroundScope)
            runCurrent()

            underTest.onActionPerformed(userAction)
            runCurrent()

            assertThat(tileDataInteractor.triggers.last())
                .isInstanceOf(DataUpdateTrigger.UserInput::class.java)
            verify(qsTileLogger)
                .logUserAction(eq(userAction), eq(tileConfig.tileSpec), eq(true), eq(true))
            verify(qsTileLogger)
                .logUserActionPipeline(
                    eq(tileConfig.tileSpec),
                    eq(userAction),
                    any(),
                    eq("initial_data"),
                )
            verify(qsTileAnalytics).trackUserAction(eq(tileConfig), eq(userAction))
        }

    @Test
    fun disabledByPolicyUserInputIsSkipped() =
        testScope.runTest {
            tileConfig =
                QSTileConfigTestBuilder.build {
                    policy = QSTilePolicy.Restricted(listOf(DISABLED_RESTRICTION))
                }
            underTest = createViewModel(testScope)
            underTest.state.launchIn(backgroundScope)

            runCurrent()

            underTest.onActionPerformed(userAction)
            runCurrent()

            assertThat(tileDataInteractor.triggers.last())
                .isNotInstanceOf(DataUpdateTrigger.UserInput::class.java)
            verify(qsTileLogger)
                .logUserActionRejectedByPolicy(
                    eq(userAction),
                    eq(tileConfig.tileSpec),
                    eq(DISABLED_RESTRICTION),
                )
            verify(qsTileAnalytics, never()).trackUserAction(any(), any())
        }

    @Test
    fun disabledByPolicySecondRestriction_userInputIsSkipped() =
        testScope.runTest {
            tileConfig =
                QSTileConfigTestBuilder.build {
                    policy =
                        QSTilePolicy.Restricted(listOf(ENABLED_RESTRICTION, DISABLED_RESTRICTION))
                }

            underTest = createViewModel(testScope)

            underTest.state.launchIn(backgroundScope)

            runCurrent()

            underTest.onActionPerformed(userAction)
            runCurrent()

            assertThat(tileDataInteractor.triggers.last())
                .isNotInstanceOf(DataUpdateTrigger.UserInput::class.java)
            verify(qsTileLogger)
                .logUserActionRejectedByPolicy(
                    eq(userAction),
                    eq(tileConfig.tileSpec),
                    eq(DISABLED_RESTRICTION),
                )
            verify(qsTileAnalytics, never()).trackUserAction(any(), any())
        }

    /** This tests that the policies are applied sequentially */
    @Test
    fun disabledByPolicySecondRestriction_onlyFirstIsTriggered() =
        testScope.runTest {
            tileConfig =
                QSTileConfigTestBuilder.build {
                    policy =
                        QSTilePolicy.Restricted(
                            listOf(DISABLED_RESTRICTION, DISABLED_RESTRICTION_2)
                        )
                }

            underTest = createViewModel(testScope)

            underTest.state.launchIn(backgroundScope)

            runCurrent()

            underTest.onActionPerformed(userAction)
            runCurrent()

            assertThat(tileDataInteractor.triggers.last())
                .isNotInstanceOf(DataUpdateTrigger.UserInput::class.java)
            verify(qsTileLogger)
                .logUserActionRejectedByPolicy(
                    eq(userAction),
                    eq(tileConfig.tileSpec),
                    eq(DISABLED_RESTRICTION),
                )
            verify(qsTileLogger, never())
                .logUserActionRejectedByPolicy(
                    eq(userAction),
                    eq(tileConfig.tileSpec),
                    eq(DISABLED_RESTRICTION_2),
                )
            verify(qsTileAnalytics, never()).trackUserAction(any(), any())
        }

    @Test
    fun falsedUserInputIsSkipped() =
        testScope.runTest {
            underTest.state.launchIn(backgroundScope)
            falsingManager.setFalseLongTap(true)
            falsingManager.setFalseTap(true)
            runCurrent()

            underTest.onActionPerformed(userAction)
            runCurrent()

            assertThat(tileDataInteractor.triggers.last())
                .isNotInstanceOf(DataUpdateTrigger.UserInput::class.java)
            verify(qsTileLogger)
                .logUserActionRejectedByFalsing(eq(userAction), eq(tileConfig.tileSpec))
            verify(qsTileAnalytics, never()).trackUserAction(any(), any())
        }

    @Test
    fun userInputIsThrottled() =
        testScope.runTest {
            val inputCount = 100
            underTest.state.launchIn(backgroundScope)

            repeat(inputCount) { underTest.onActionPerformed(userAction) }
            runCurrent()

            assertThat(tileDataInteractor.triggers.size).isLessThan(inputCount)
        }

    private fun createViewModel(scope: TestScope): QSTileViewModel =
        QSTileViewModelImpl(
            tileConfig,
            { tileUserActionInteractor },
            { tileDataInteractor },
            {
                object : QSTileDataToStateMapper<String> {
                    override fun map(config: QSTileConfig, data: String): QSTileState =
                        QSTileState.build(Icon.Resource(0, ContentDescription.Resource(0)), data) {}
                }
            },
            disabledByPolicyInteractor,
            userRepository,
            falsingManager,
            qsTileAnalytics,
            qsTileLogger,
            FakeSystemClock(),
            testCoroutineDispatcher,
            testCoroutineDispatcher,
            scope.backgroundScope,
            FakeTileDetailsViewModel("QSTileViewModelUserInputTest"),
        )
}
