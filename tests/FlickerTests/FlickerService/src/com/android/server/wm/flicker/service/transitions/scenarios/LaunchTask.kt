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

package com.android.server.wm.flicker.service.transitions.scenarios

import android.app.Instrumentation
import android.tools.Rotation
import android.tools.flicker.AssertionInvocationGroup
import android.tools.flicker.assertors.assertions.AppWindowCoversFullScreenAtStart
import android.tools.flicker.assertors.assertions.AppWindowOnTopAtEnd
import android.tools.flicker.assertors.assertions.AppWindowOnTopAtStart
import android.tools.flicker.assertors.assertions.BackgroundShowsInTransition
import android.tools.flicker.assertors.assertions.LayerBecomesInvisible
import android.tools.flicker.assertors.assertions.LayerBecomesVisible
import android.tools.flicker.assertors.assertions.LayerIsNeverVisible
import android.tools.flicker.assertors.assertions.AppWindowIsNeverVisible
import android.tools.flicker.config.AssertionTemplates
import android.tools.flicker.config.FlickerConfigEntry
import android.tools.flicker.config.ScenarioId
import android.tools.flicker.config.appclose.Components.CLOSING_APPS
import android.tools.flicker.config.appclose.Components.CLOSING_CHANGES
import android.tools.flicker.config.applaunch.Components.OPENING_CHANGES
import android.tools.flicker.config.common.Components.LAUNCHER
import android.tools.flicker.config.common.Components.WALLPAPER
import android.tools.flicker.extractors.TaggedCujTransitionMatcher
import android.tools.flicker.extractors.TaggedScenarioExtractorBuilder
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.traces.events.CujType
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.NewTasksAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Test


/**
 * This tests performs a transition between tasks
 */
abstract class LaunchTask(val rotation: Rotation = Rotation.ROTATION_0) {
    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val tapl = LauncherInstrumentation()

    private val launchNewTaskApp = NewTasksAppHelper(instrumentation)

    @Before
    fun setup() {
        tapl.setEnableRotation(true)
        ChangeDisplayOrientationRule.setRotation(rotation)
        tapl.setExpectedRotation(rotation.value)
        launchNewTaskApp.launchViaIntent(wmHelper)
    }

    @Test
    open fun openNewTask() {
        launchNewTaskApp.openNewTask(device, wmHelper)
    }

    @After
    fun tearDown() {
        launchNewTaskApp.exit(wmHelper)
    }

    companion object {
        /**
         * General task transition scenario that can be reused for any trace
         */
        val TASK_TRANSITION_SCENARIO =
            FlickerConfigEntry(
                scenarioId = ScenarioId("TASK_TRANSITION_SCENARIO"),
                extractor = TaggedScenarioExtractorBuilder()
                    .setTargetTag(CujType.CUJ_DEFAULT_TASK_TO_TASK_ANIMATION)
                    .setTransitionMatcher(
                        TaggedCujTransitionMatcher(associatedTransitionRequired = true)
                    )
                    .build(),
                assertions = listOf(
                    // Opening changes replace the closing ones
                    LayerBecomesInvisible(CLOSING_CHANGES),
                    AppWindowOnTopAtStart(CLOSING_CHANGES),
                    LayerBecomesVisible(OPENING_CHANGES),
                    AppWindowOnTopAtEnd(OPENING_CHANGES),

                    // There is a background color and it's covering the transition area
                    BackgroundShowsInTransition(CLOSING_CHANGES)
                ).associateBy({ it }, { AssertionInvocationGroup.BLOCKING })
            )

        /**
         * Scenario that is making assertions that are valid for the new task app but that do not
         * apply to other task transitions in general
         */
        val OPEN_NEW_TASK_APP_SCENARIO =
            FlickerConfigEntry(
                scenarioId = ScenarioId("OPEN_NEW_TASK_APP_SCENARIO"),
                extractor = TaggedScenarioExtractorBuilder()
                    .setTargetTag(CujType.CUJ_DEFAULT_TASK_TO_TASK_ANIMATION)
                    .setTransitionMatcher(
                        TaggedCujTransitionMatcher(associatedTransitionRequired = true)
                    )
                    .build(),
                assertions = AssertionTemplates.COMMON_ASSERTIONS +
                        listOf(
                            // Wallpaper and launcher never visible
                            LayerIsNeverVisible(WALLPAPER, mustExist = true),
                            LayerIsNeverVisible(LAUNCHER, mustExist = true),
                            AppWindowIsNeverVisible(LAUNCHER, mustExist = true),
                            // App window covers the display at start
                            AppWindowCoversFullScreenAtStart(CLOSING_APPS)
                        ).associateBy({ it }, { AssertionInvocationGroup.BLOCKING })
            )
    }
}