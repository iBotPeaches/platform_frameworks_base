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

package com.android.internal.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.Parcel;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DisableStatesTest {

    @Test
    public void testParcelable() {
        Map<Integer, Pair<Integer, Integer>> displaysWithStates = new HashMap<>();
        displaysWithStates.put(1, new Pair<>(10, 20));
        displaysWithStates.put(2, new Pair<>(30, 40));
        boolean animate = true;
        DisableStates original = new DisableStates(displaysWithStates, animate);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DisableStates restored = DisableStates.CREATOR.createFromParcel(parcel);

        assertNotNull(restored);
        assertEquals(original.displaysWithStates.size(), restored.displaysWithStates.size());
        for (Map.Entry<Integer, Pair<Integer, Integer>> entry :
                original.displaysWithStates.entrySet()) {
            int displayId = entry.getKey();
            Pair<Integer, Integer> originalDisplayStates = entry.getValue();
            Pair<Integer, Integer> restoredDisplayStates = restored.displaysWithStates.get(
                    displayId);
            assertEquals(originalDisplayStates.first, restoredDisplayStates.first);
            assertEquals(originalDisplayStates.second, restoredDisplayStates.second);
        }
        assertEquals(original.animate, restored.animate);
    }
}
