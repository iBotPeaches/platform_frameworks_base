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

package com.android.systemui.statusbar.notification.stack

import android.os.testableLooper
import android.testing.TestableLooper.RunWithLooper
import androidx.dynamicanimation.animation.SpringForce
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.FakeFeatureFlagsClassic
import com.android.systemui.haptics.msdl.fakeMSDLPlayer
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotificationTestHelper
import com.android.systemui.statusbar.notification.stack.MagneticNotificationRowManagerImpl.State
import com.android.systemui.testKosmos
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class MagneticNotificationRowManagerImplTest : SysuiTestCase() {

    private val featureFlags = FakeFeatureFlagsClassic()
    private val kosmos = testKosmos()
    private val childrenNumber = 5
    private val stackScrollLayout = mock<NotificationStackScrollLayout>()
    private val sectionsManager = mock<NotificationSectionsManager>()
    private val msdlPlayer = kosmos.fakeMSDLPlayer
    private var canRowBeDismissed = true
    private var magneticAnimationsCancelled = MutableList(childrenNumber) { false }

    private val underTest = kosmos.magneticNotificationRowManagerImpl

    private lateinit var notificationTestHelper: NotificationTestHelper
    private lateinit var children: NotificationChildrenContainer
    private lateinit var swipedRow: ExpandableNotificationRow

    @Before
    fun setUp() {
        allowTestableLooperAsMainThread()
        notificationTestHelper =
            NotificationTestHelper(mContext, mDependency, kosmos.testableLooper, featureFlags)
        children = notificationTestHelper.createGroup(childrenNumber).childrenContainer
        swipedRow = children.attachedChildren[childrenNumber / 2]
        children.attachedChildren.forEachIndexed { index, row ->
            row.magneticRowListener = row.magneticRowListener.asTestableListener(index)
        }
        magneticAnimationsCancelled.replaceAll { false }
    }

    @Test
    fun setMagneticAndRoundableTargets_onIdle_targetsGetSet() =
        kosmos.testScope.runTest {
            // WHEN the targets are set for a row
            setTargets()

            // THEN the magnetic and roundable targets are defined and the state is TARGETS_SET
            assertThat(underTest.currentState).isEqualTo(State.TARGETS_SET)
            assertThat(underTest.currentMagneticListeners.isNotEmpty()).isTrue()
            assertThat(underTest.isSwipedViewRoundableSet).isTrue()
        }

    @Test
    fun setMagneticRowTranslation_whenTargetsAreSet_startsPulling() =
        kosmos.testScope.runTest {
            // GIVEN targets are set
            setTargets()

            // WHEN setting a translation for the swiped row
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // THEN the state moves to PULLING
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
        }

    @Test
    fun setMagneticRowTranslation_whenIdle_doesNotSetMagneticTranslation() =
        kosmos.testScope.runTest {
            // GIVEN an IDLE state
            // WHEN setting a translation for the swiped row
            val row = children.attachedChildren[childrenNumber / 2]
            underTest.setMagneticRowTranslation(row, translation = 100f)

            // THEN no magnetic translations are set
            val canSetMagneticTranslation =
                underTest.setMagneticRowTranslation(row, translation = 100f)
            assertThat(canSetMagneticTranslation).isFalse()
        }

    @Test
    fun setMagneticRowTranslation_whenRowIsNotSwiped_doesNotSetMagneticTranslation() =
        kosmos.testScope.runTest {
            // GIVEN that targets are set
            setTargets()

            // WHEN setting a translation for a row that is not being swiped
            val differentRow = children.attachedChildren[childrenNumber / 2 - 1]
            val canSetMagneticTranslation =
                underTest.setMagneticRowTranslation(differentRow, translation = 100f)

            // THEN no magnetic translations are set
            assertThat(canSetMagneticTranslation).isFalse()
        }

    @Test
    fun setMagneticRowTranslation_whenDismissible_belowThreshold_whenPulling_setsTranslations() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the rows are being pulled
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN setting a translation that will fall below the threshold
            val translation = 50f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the targets continue to be pulled and translations are set
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
            assertThat(swipedRow.translation).isEqualTo(underTest.swipedRowMultiplier * translation)
        }

    @Test
    fun setMagneticRowTranslation_whenNotDismissible_belowThreshold_whenPulling_setsTranslations() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the rows are being pulled
            canRowBeDismissed = false
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN setting a translation that will fall below the threshold
            val translation = 50f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the targets continue to be pulled and reduced translations are set
            val expectedTranslation = getReducedTranslation(translation)
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
            assertThat(swipedRow.translation).isEqualTo(expectedTranslation)
        }

    @Test
    fun setMagneticRowTranslation_whenDismissible_aboveThreshold_whilePulling_detaches() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the rows are being pulled
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN setting a translation that will fall above the threshold
            val translation = 150f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the swiped view detaches and the correct detach haptics play
            assertThat(underTest.currentState).isEqualTo(State.DETACHED)
            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
        }

    @Test
    fun setMagneticRowTranslation_whenNotDismissible_aboveThreshold_whilePulling_doesNotDetach() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the rows are being pulled
            canRowBeDismissed = false
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN setting a translation that will fall above the threshold
            val translation = 150f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the swiped view does not detach and the reduced translation is set
            val expectedTranslation = getReducedTranslation(translation)
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
            assertThat(swipedRow.translation).isEqualTo(expectedTranslation)
        }

    @Test
    fun setMagneticRowTranslation_whileDetached_setsTranslationAndStaysDetached() =
        kosmos.testScope.runTest {
            // GIVEN that the swiped view has been detached
            setDetachedState()

            // WHEN setting a new translation
            val translation = 300f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the swiped view continues to be detached
            assertThat(underTest.currentState).isEqualTo(State.DETACHED)
        }

    @Test
    fun onMagneticInteractionEnd_whilePulling_goesToIdle() =
        kosmos.testScope.runTest {
            // GIVEN targets are set
            setTargets()

            // WHEN setting a translation for the swiped row
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN the interaction ends on the row
            underTest.onMagneticInteractionEnd(swipedRow, velocity = null)

            // THEN the state resets
            assertThat(underTest.currentState).isEqualTo(State.IDLE)
        }

    @Test
    fun onMagneticInteractionEnd_whileTargetsSet_goesToIdle() =
        kosmos.testScope.runTest {
            // GIVEN that targets are set
            setTargets()

            // WHEN the interaction ends on the row
            underTest.onMagneticInteractionEnd(swipedRow, velocity = null)

            // THEN the state resets
            assertThat(underTest.currentState).isEqualTo(State.IDLE)
        }

    @Test
    fun onMagneticInteractionEnd_whileDetached_goesToIdle() =
        kosmos.testScope.runTest {
            // GIVEN the swiped row is detached
            setDetachedState()

            // WHEN the interaction ends on the row
            underTest.onMagneticInteractionEnd(swipedRow, velocity = null)

            // THEN the state resets
            assertThat(underTest.currentState).isEqualTo(State.IDLE)
        }

    @Test
    fun onMagneticInteractionEnd_whenDetached_cancelsMagneticAnimations() =
        kosmos.testScope.runTest {
            // GIVEN the swiped row is detached
            setDetachedState()

            // WHEN the interaction ends on the row
            underTest.onMagneticInteractionEnd(swipedRow, velocity = null)

            // THEN magnetic animations are cancelled
            assertThat(magneticAnimationsCancelled[childrenNumber / 2]).isTrue()
        }

    @Test
    fun onMagneticInteractionEnd_forMagneticNeighbor_cancelsMagneticAnimations() =
        kosmos.testScope.runTest {
            val neighborIndex = childrenNumber / 2 - 1
            val neighborRow = children.attachedChildren[neighborIndex]

            // GIVEN that targets are set
            setTargets()

            // WHEN the interactionEnd is called on a target different from the swiped row
            underTest.onMagneticInteractionEnd(neighborRow, null)

            // THEN magnetic animations are cancelled
            assertThat(magneticAnimationsCancelled[neighborIndex]).isTrue()
        }

    @Test
    fun onResetRoundness_swipedRoundableGetsCleared() =
        kosmos.testScope.runTest {
            // GIVEN targets are set
            setTargets()

            // WHEN we reset the roundness
            underTest.resetRoundness()

            // THEN the swiped roundable gets cleared
            assertThat(underTest.isSwipedViewRoundableSet).isFalse()
        }

    @Test
    fun isMagneticRowDismissible_whenDetached_isDismissibleWithCorrectDirection_andFastFling() =
        kosmos.testScope.runTest {
            setDetachedState()

            // With a fast enough velocity in the direction of the detachment
            val velocity = Float.POSITIVE_INFINITY

            // The row is dismissible
            val isDismissible = underTest.isMagneticRowSwipedDismissible(swipedRow, velocity)
            assertThat(isDismissible).isTrue()
        }

    @Test
    fun isMagneticRowDismissible_whenDetached_isDismissibleWithCorrectDirection_andSlowFling() =
        kosmos.testScope.runTest {
            setDetachedState()

            // With a very low velocity in the direction of the detachment
            val velocity = 0.01f

            // The row is dismissible
            val isDismissible = underTest.isMagneticRowSwipedDismissible(swipedRow, velocity)
            assertThat(isDismissible).isTrue()
        }

    @Test
    fun isMagneticRowDismissible_whenDetached_isDismissibleWithOppositeDirection_andSlowFling() =
        kosmos.testScope.runTest {
            setDetachedState()

            // With a very low velocity in the opposite direction relative to the detachment
            val velocity = -0.01f

            // The row is dismissible
            val isDismissible = underTest.isMagneticRowSwipedDismissible(swipedRow, velocity)
            assertThat(isDismissible).isTrue()
        }

    @Test
    fun isMagneticRowDismissible_whenDetached_isNotDismissibleWithOppositeDirection_andFastFling() =
        kosmos.testScope.runTest {
            setDetachedState()

            // With a high enough velocity in the opposite direction relative to the detachment
            val velocity = Float.NEGATIVE_INFINITY

            // The row is not dismissible
            val isDismissible = underTest.isMagneticRowSwipedDismissible(swipedRow, velocity)
            assertThat(isDismissible).isFalse()
        }

    @Test
    fun setMagneticRowTranslation_whenDetached_belowAttachThreshold_reattaches() =
        kosmos.testScope.runTest {
            // GIVEN that the swiped view has been detached
            setDetachedState()

            // WHEN setting a new translation above the attach threshold
            val translation = 50f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the swiped view reattaches magnetically and the state becomes PULLING
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
        }

    @After
    fun tearDown() {
        // We reset the manager so that all MagneticRowListener can cancel all animations
        underTest.reset()
    }

    private fun setDetachedState() {
        val threshold = 100f
        underTest.onDensityChange(
            threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
        )

        // Set the pulling state
        setTargets()
        underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

        // Set a translation that will fall above the threshold
        val translation = 150f
        underTest.setMagneticRowTranslation(swipedRow, translation)

        assertThat(underTest.currentState).isEqualTo(State.DETACHED)
    }

    private fun setTargets() {
        underTest.setMagneticAndRoundableTargets(swipedRow, stackScrollLayout, sectionsManager)
    }

    private fun getReducedTranslation(originalTranslation: Float) =
        underTest.swipedRowMultiplier *
            originalTranslation *
            MagneticNotificationRowManagerImpl.MAGNETIC_REDUCTION

    private fun MagneticRowListener.asTestableListener(rowIndex: Int): MagneticRowListener {
        val delegate = this
        return object : MagneticRowListener {
            override fun setMagneticTranslation(translation: Float, trackEagerly: Boolean) {
                delegate.setMagneticTranslation(translation, trackEagerly)
            }

            override fun triggerMagneticForce(
                endTranslation: Float,
                springForce: SpringForce,
                startVelocity: Float,
            ) {
                delegate.triggerMagneticForce(endTranslation, springForce, startVelocity)
            }

            override fun cancelMagneticAnimations() {
                magneticAnimationsCancelled[rowIndex] = true
                delegate.cancelMagneticAnimations()
            }

            override fun cancelTranslationAnimations() {
                delegate.cancelTranslationAnimations()
            }

            override fun canRowBeDismissed(): Boolean {
                return canRowBeDismissed
            }
        }
    }
}
