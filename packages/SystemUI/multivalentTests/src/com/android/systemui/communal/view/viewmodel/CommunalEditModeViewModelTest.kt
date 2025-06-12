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

package com.android.systemui.communal.view.viewmodel

import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.accessibilityManager
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLogger
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.data.model.CommunalSmartspaceTimer
import com.android.systemui.communal.data.repository.fakeCommunalMediaRepository
import com.android.systemui.communal.data.repository.fakeCommunalSmartspaceRepository
import com.android.systemui.communal.data.repository.fakeCommunalTutorialRepository
import com.android.systemui.communal.data.repository.fakeCommunalWidgetRepository
import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.communal.domain.interactor.communalInteractor
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.domain.model.CommunalContentModel
import com.android.systemui.communal.shared.log.CommunalMetricsLogger
import com.android.systemui.communal.shared.log.CommunalUiEvent
import com.android.systemui.communal.shared.model.EditModeState
import com.android.systemui.communal.ui.viewmodel.CommunalEditModeViewModel
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.media.controls.ui.controller.mediaCarouselController
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class CommunalEditModeViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val testableResources = context.orCreateTestableResources

    private val Kosmos.packageManager by Kosmos.Fixture { mock<PackageManager>() }

    private val Kosmos.metricsLogger by Kosmos.Fixture { mock<CommunalMetricsLogger>() }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            CommunalEditModeViewModel(
                communalSceneInteractor,
                communalInteractor,
                communalSettingsInteractor,
                keyguardTransitionInteractor,
                mock<MediaHost>(),
                uiEventLogger,
                logcatLogBuffer("CommunalEditModeViewModelTest"),
                testDispatcher,
                metricsLogger,
                context,
                accessibilityManager,
                packageManager,
                WIDGET_PICKER_PACKAGE_NAME,
                kosmos.mediaCarouselController,
            )
        }

    @Before
    fun setUp() {
        kosmos.fakeUserRepository.setUserInfos(listOf(MAIN_USER_INFO))
        kosmos.fakeUserTracker.set(userInfos = listOf(MAIN_USER_INFO), selectedUserIndex = 0)
        kosmos.fakeFeatureFlagsClassic.set(Flags.COMMUNAL_SERVICE_ENABLED, true)
    }

    @Test
    fun communalContent_onlyWidgetsAndCtaTileAreShownInEditMode() =
        kosmos.runTest {
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )

            // Widgets available.
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 0, rank = 30)
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, rank = 20)

            // Smartspace available.
            fakeCommunalSmartspaceRepository.setTimers(
                listOf(
                    CommunalSmartspaceTimer(
                        smartspaceTargetId = "target",
                        createdTimestampMillis = 0L,
                        remoteViews = Mockito.mock(RemoteViews::class.java),
                    )
                )
            )

            // Media playing.
            fakeCommunalMediaRepository.mediaActive()

            val communalContent by collectLastValue(underTest.communalContent)

            // Only Widgets and CTA tile are shown.
            assertThat(communalContent?.size).isEqualTo(2)
            assertThat(communalContent?.get(0))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)
            assertThat(communalContent?.get(1))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)
        }

    @Test
    fun selectedKey_onReorderWidgets_isSet() =
        kosmos.runTest {
            val selectedKey by collectLastValue(underTest.selectedKey)

            underTest.setSelectedKey(null)
            assertThat(selectedKey).isNull()

            val key = CommunalContentModel.KEY.widget(123)
            underTest.onReorderWidgetStart(key)
            assertThat(selectedKey).isEqualTo(key)
        }

    @Test
    fun isCommunalContentVisible_isTrue_whenEditModeShowing() =
        kosmos.runTest {
            val isCommunalContentVisible by collectLastValue(underTest.isCommunalContentVisible)
            communalSceneInteractor.setEditModeState(EditModeState.SHOWING)
            assertThat(isCommunalContentVisible).isEqualTo(true)
        }

    @Test
    fun isCommunalContentVisible_isFalse_whenEditModeNotShowing() =
        kosmos.runTest {
            val isCommunalContentVisible by collectLastValue(underTest.isCommunalContentVisible)
            communalSceneInteractor.setEditModeState(null)
            assertThat(isCommunalContentVisible).isEqualTo(false)
        }

    @Test
    fun deleteWidget() =
        kosmos.runTest {
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )

            // Widgets available.
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 0, rank = 30)
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, rank = 20)

            val communalContent by collectLastValue(underTest.communalContent)

            // Widgets and CTA tile are shown.
            assertThat(communalContent?.size).isEqualTo(2)
            assertThat(communalContent?.get(0))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)
            assertThat(communalContent?.get(1))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)

            underTest.onDeleteWidget(
                id = 0,
                key = "key_0",
                componentName = ComponentName("test_package", "test_class"),
                rank = 30,
            )

            // Only one widget and CTA tile remain.
            assertThat(communalContent?.size).isEqualTo(1)
            val item = communalContent?.get(0)
            val appWidgetId =
                if (item is CommunalContentModel.WidgetContent) item.appWidgetId else null
            assertThat(appWidgetId).isEqualTo(1)
        }

    @Test
    fun deleteWidget_clearsSelectedKey() =
        kosmos.runTest {
            val selectedKey by collectLastValue(underTest.selectedKey)
            underTest.setSelectedKey("test_key")
            assertThat(selectedKey).isEqualTo("test_key")

            // Selected key is deleted.
            underTest.onDeleteWidget(
                id = 0,
                key = "test_key",
                componentName = ComponentName("test_package", "test_class"),
                rank = 30,
            )

            assertThat(selectedKey).isNull()
        }

    @Test
    fun reorderWidget_uiEventLogging_start() =
        kosmos.runTest {
            underTest.onReorderWidgetStart(CommunalContentModel.KEY.widget(123))

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.logs[0].eventId)
                .isEqualTo(CommunalUiEvent.COMMUNAL_HUB_REORDER_WIDGET_START.id)
        }

    @Test
    fun reorderWidget_uiEventLogging_end() =
        kosmos.runTest {
            underTest.onReorderWidgetEnd()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.logs[0].eventId)
                .isEqualTo(CommunalUiEvent.COMMUNAL_HUB_REORDER_WIDGET_FINISH.id)
        }

    @Test
    fun reorderWidget_uiEventLogging_cancel() =
        kosmos.runTest {
            underTest.onReorderWidgetCancel()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.logs[0].eventId)
                .isEqualTo(CommunalUiEvent.COMMUNAL_HUB_REORDER_WIDGET_CANCEL.id)
        }

    @Test
    fun onOpenWidgetPicker_launchesWidgetPickerActivity() {
        kosmos.runTest {
            var activityStarted = false
            val success =
                underTest.onOpenWidgetPicker(testableResources.resources) { _ ->
                    run { activityStarted = true }
                }

            assertTrue(activityStarted)
            assertTrue(success)
        }
    }

    @Test
    fun onOpenWidgetPicker_activityLaunchThrowsException_failure() {
        kosmos.runTest {
            val success =
                underTest.onOpenWidgetPicker(testableResources.resources) { _ ->
                    run { throw ActivityNotFoundException() }
                }

            assertFalse(success)
        }
    }

    @Test
    fun showDisclaimer_trueAfterEditModeShowing() =
        kosmos.runTest {
            val showDisclaimer by collectLastValue(underTest.showDisclaimer)

            assertThat(showDisclaimer).isFalse()
            underTest.setEditModeState(EditModeState.SHOWING)
            assertThat(showDisclaimer).isTrue()
        }

    @Test
    fun showDisclaimer_falseWhenDismissed() =
        kosmos.runTest {
            underTest.setEditModeState(EditModeState.SHOWING)
            fakeUserRepository.setSelectedUserInfo(MAIN_USER_INFO)

            val showDisclaimer by collectLastValue(underTest.showDisclaimer)

            assertThat(showDisclaimer).isTrue()
            underTest.onDisclaimerDismissed()
            assertThat(showDisclaimer).isFalse()
        }

    @Test
    fun showDisclaimer_trueWhenTimeout() =
        kosmos.runTest {
            underTest.setEditModeState(EditModeState.SHOWING)
            fakeUserRepository.setSelectedUserInfo(MAIN_USER_INFO)

            val showDisclaimer by collectLastValue(underTest.showDisclaimer)

            assertThat(showDisclaimer).isTrue()
            underTest.onDisclaimerDismissed()
            assertThat(showDisclaimer).isFalse()
            testScope.advanceTimeBy(CommunalInteractor.DISCLAIMER_RESET_MILLIS + 1.milliseconds)
            assertThat(showDisclaimer).isTrue()
        }

    @Test
    fun scrollPosition_persistedOnEditCleanup() =
        kosmos.runTest {
            val index = 2
            val offset = 30
            underTest.onScrollPositionUpdated(index, offset)
            underTest.cleanupEditModeState()

            assertThat(communalInteractor.firstVisibleItemIndex).isEqualTo(index)
            assertThat(communalInteractor.firstVisibleItemOffset).isEqualTo(offset)
        }

    @Test
    fun onNewWidgetAdded_accessibilityDisabled_doNothing() =
        kosmos.runTest {
            whenever(accessibilityManager.isEnabled).thenReturn(false)

            val provider =
                mock<AppWidgetProviderInfo> {
                    on { loadLabel(packageManager) }.thenReturn("Test Clock")
                }
            underTest.onNewWidgetAdded(provider)

            verify(accessibilityManager, never()).sendAccessibilityEvent(any())
        }

    @Test
    fun onNewWidgetAdded_accessibilityEnabled_sendAccessibilityAnnouncement() =
        kosmos.runTest {
            whenever(accessibilityManager.isEnabled).thenReturn(true)

            val provider =
                mock<AppWidgetProviderInfo> {
                    on { loadLabel(packageManager) }.thenReturn("Test Clock")
                }
            underTest.onNewWidgetAdded(provider)

            val captor = argumentCaptor<AccessibilityEvent>()
            verify(accessibilityManager).sendAccessibilityEvent(captor.capture())

            val event = captor.firstValue
            assertThat(event.eventType).isEqualTo(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            assertThat(event.contentDescription).isEqualTo("Test Clock widget added to lock screen")
        }

    @Test
    fun onResizeWidget_logsMetrics() =
        kosmos.runTest {
            val appWidgetId = 123
            val spanY = 2
            val widgetIdToRankMap = mapOf(appWidgetId to 1)
            val componentName = ComponentName("test.package", "TestWidget")
            val rank = 1

            underTest.onResizeWidget(
                appWidgetId = appWidgetId,
                spanY = spanY,
                widgetIdToRankMap = widgetIdToRankMap,
                componentName = componentName,
                rank = rank,
            )

            verify(metricsLogger)
                .logResizeWidget(
                    componentName = componentName.flattenToString(),
                    rank = rank,
                    spanY = spanY,
                )
        }

    private companion object {
        val MAIN_USER_INFO = UserInfo(0, "primary", UserInfo.FLAG_MAIN)
        const val WIDGET_PICKER_PACKAGE_NAME = "widget_picker_package_name"
    }
}
