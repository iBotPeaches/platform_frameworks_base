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

package com.android.server.display.mode

import android.os.Looper
import android.view.Display
import android.view.DisplayAddress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private const val PHYSICAL_DISPLAY_ID_1 = 1L
private const val PHYSICAL_DISPLAY_ID_2 = 2L
private const val MODE_ID_1 = 3
private const val MODE_ID_2 = 4
private const val LOGICAL_DISPLAY_ID = 5
private val physicalAddress1 = DisplayAddress.fromPhysicalDisplayId(PHYSICAL_DISPLAY_ID_1)
private val physicalAddress2 = DisplayAddress.fromPhysicalDisplayId(PHYSICAL_DISPLAY_ID_2)

/**
 * Tests for ModeChangeObserver, comply with changes in b/31925610
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
public class ModeChangeObserverTest {
    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    // System Under Test
    private lateinit var modeChangeObserver: ModeChangeObserver

    // Non Mocks
    private val looper = Looper.getMainLooper()
    private val votesStorage = VotesStorage({}, null)

    // Mocks
    private val mockInjector = mock<DisplayModeDirector.Injector>()
    private val mockDisplay1 = mock<Display>()
    private val mockDisplay2 = mock<Display>()

    @Before
    fun setUp() {
        whenever(mockInjector.getDisplay(LOGICAL_DISPLAY_ID)).thenReturn(mockDisplay1)
        whenever(mockDisplay1.getAddress()).thenReturn(physicalAddress1)
        whenever(mockInjector.getDisplays()).thenReturn(arrayOf<Display>())
        modeChangeObserver = ModeChangeObserver(votesStorage, mockInjector, looper)
        modeChangeObserver.observe()
    }

    @Test
    fun testOnModeRejectedBeforeDisplayAdded() {
        val rejectedModes = HashSet<Int>()
        rejectedModes.add(MODE_ID_1)
        rejectedModes.add(MODE_ID_2)

        // ModeRejected is called before display is mapped, hence votes are null
        modeChangeObserver.mModeChangeListener.onModeRejected(PHYSICAL_DISPLAY_ID_1, MODE_ID_1)
        modeChangeObserver.mModeChangeListener.onModeRejected(PHYSICAL_DISPLAY_ID_1, MODE_ID_2)
        val votes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(votes.size()).isEqualTo(0)

        // Display is mapped to a Logical Display Id, now the Rejected Mode Votes get updated
        modeChangeObserver.mDisplayListener.onDisplayAdded(LOGICAL_DISPLAY_ID)
        val newVotes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(newVotes.size()).isEqualTo(1)
        val vote = newVotes.get(Vote.PRIORITY_REJECTED_MODES)
        assertThat(vote).isInstanceOf(RejectedModesVote::class.java)
        val rejectedModesVote = vote as RejectedModesVote
        assertThat(rejectedModesVote.mModeIds.size).isEqualTo(rejectedModes.size)
        assertThat(rejectedModesVote.mModeIds).contains(MODE_ID_1)
        assertThat(rejectedModesVote.mModeIds).contains(MODE_ID_2)
    }

    @Test
    fun testOnDisplayAddedBeforeOnModeRejected() {
        // Display is mapped to the corresponding Logical Id, but Mode Rejected no received yet
        // Verify that the Vote is still Null
        modeChangeObserver.mDisplayListener.onDisplayAdded(LOGICAL_DISPLAY_ID)
        val votes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(votes.size()).isEqualTo(0)

        // ModeRejected Event received for the mapped display
        modeChangeObserver.mModeChangeListener.onModeRejected(PHYSICAL_DISPLAY_ID_1, MODE_ID_1)
        val newVotes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(newVotes.size()).isEqualTo(1)
        val vote = newVotes.get(Vote.PRIORITY_REJECTED_MODES)
        assertThat(vote).isInstanceOf(RejectedModesVote::class.java)
        val rejectedModesVote = vote as RejectedModesVote
        assertThat(rejectedModesVote.mModeIds.size).isEqualTo(1)
        assertThat(rejectedModesVote.mModeIds).contains(MODE_ID_1)
    }

    @Test
    fun testOnDisplayAddedThenRejectedThenRemoved() {
        // Display is mapped to its Logical Display Id
        modeChangeObserver.mDisplayListener.onDisplayAdded(LOGICAL_DISPLAY_ID)
        val votes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(votes.size()).isEqualTo(0)

        // ModeRejected Event is received for mapped display
        modeChangeObserver.mModeChangeListener.onModeRejected(PHYSICAL_DISPLAY_ID_1, MODE_ID_1)
        val newVotes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(newVotes.size()).isEqualTo(1)
        val vote = newVotes.get(Vote.PRIORITY_REJECTED_MODES)
        assertThat(vote).isInstanceOf(RejectedModesVote::class.java)
        val rejectedModesVote = vote as RejectedModesVote
        assertThat(rejectedModesVote.mModeIds.size).isEqualTo(1)
        assertThat(rejectedModesVote.mModeIds).contains(MODE_ID_1)

        // Display mapping is removed, hence remove the votes
        modeChangeObserver.mDisplayListener.onDisplayRemoved(LOGICAL_DISPLAY_ID)
        val finalVotes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(finalVotes.size()).isEqualTo(0)
    }

    @Test
    fun testForModesRejectedAfterDisplayChanged() {
        // Mock Display 1 is mapped to logicalId
        modeChangeObserver.mDisplayListener.onDisplayAdded(LOGICAL_DISPLAY_ID)
        val votes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(votes.size()).isEqualTo(0)

        // Mode Rejected received for PhysicalId2 not mapped yet, so votes are null
        whenever(mockInjector.getDisplay(LOGICAL_DISPLAY_ID)).thenReturn(mockDisplay2)
        whenever(mockDisplay2.getAddress()).thenReturn(physicalAddress2)
        modeChangeObserver.mModeChangeListener.onModeRejected(PHYSICAL_DISPLAY_ID_2, MODE_ID_2)
        val changedVotes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(changedVotes.size()).isEqualTo(0)

        // Display mapping changed, now PhysicalId2 is mapped to the LogicalId, votes get updated
        modeChangeObserver.mDisplayListener.onDisplayChanged(LOGICAL_DISPLAY_ID)
        val finalVotes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(finalVotes.size()).isEqualTo(1)
        val finalVote = finalVotes.get(Vote.PRIORITY_REJECTED_MODES)
        assertThat(finalVote).isInstanceOf(RejectedModesVote::class.java)
        val newRejectedModesVote = finalVote as RejectedModesVote
        assertThat(newRejectedModesVote.mModeIds.size).isEqualTo(1)
        assertThat(newRejectedModesVote.mModeIds).contains(MODE_ID_2)
    }

    @Test
    fun testForModesNotRejectedAfterDisplayChanged() {
        // Mock Display 1 is added
        modeChangeObserver.mDisplayListener.onDisplayAdded(LOGICAL_DISPLAY_ID)
        val votes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(votes.size()).isEqualTo(0)

        // Mode Rejected received for Display 1, votes added for rejected mode
        modeChangeObserver.mModeChangeListener.onModeRejected(PHYSICAL_DISPLAY_ID_1, MODE_ID_1)
        val newVotes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(newVotes.size()).isEqualTo(1)
        val vote = newVotes.get(Vote.PRIORITY_REJECTED_MODES)
        assertThat(vote).isInstanceOf(RejectedModesVote::class.java)
        val rejectedModesVote = vote as RejectedModesVote
        assertThat(rejectedModesVote.mModeIds.size).isEqualTo(1)
        assertThat(rejectedModesVote.mModeIds).contains(MODE_ID_1)

        // Display Changed such that logical Id corresponds to PhysicalDisplayId2
        // Rejected Modes Vote is removed
        whenever(mockInjector.getDisplay(LOGICAL_DISPLAY_ID)).thenReturn(mockDisplay2)
        whenever(mockDisplay2.getAddress()).thenReturn(physicalAddress2)
        modeChangeObserver.mDisplayListener.onDisplayChanged(LOGICAL_DISPLAY_ID)
        val finalVotes = votesStorage.getVotes(LOGICAL_DISPLAY_ID)
        assertThat(finalVotes.size()).isEqualTo(0)
    }
}