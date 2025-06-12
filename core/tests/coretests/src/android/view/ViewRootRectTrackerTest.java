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

package android.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.testng.AssertJUnit.assertEquals;

import android.graphics.Rect;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ViewRootRectTrackerTest {
    private ViewRootRectTracker mTracker;
    private final List<Rect> mRects = Lists.newArrayList(new Rect(0, 0, 5, 5),
            new Rect(5, 5, 10, 10));

    @Before
    public void setUp() {
        mTracker = new ViewRootRectTracker(v -> Collections.emptyList());
    }

    @Test
    public void setRootRectsAndComputeTest() {
        mTracker.setRootRects(mRects);
        mTracker.computeChanges();
        assertEquals(mRects, mTracker.getLastComputedRects());
    }

    @Test
    public void waitingForComputeChangesTest() {
        mTracker.setRootRects(mRects);
        assertTrue(mTracker.isWaitingForComputeChanges());
        mTracker.computeChangedRects();
        assertFalse(mTracker.isWaitingForComputeChanges());

        View mockView = mock(View.class);
        mTracker.updateRectsForView(mockView);
        assertTrue(mTracker.isWaitingForComputeChanges());
        mTracker.computeChangedRects();
        assertFalse(mTracker.isWaitingForComputeChanges());
    }
}
