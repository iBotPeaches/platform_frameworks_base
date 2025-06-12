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

package com.android.server.wm.utils;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.content.res.TypedArray;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import com.android.internal.policy.AttributeCache;

import org.junit.Test;

/**
 * Build/Install/Run:
 *  atest WmTests:WindowStyleCacheTest
 */
@SmallTest
@Presubmit
public class WindowStyleCacheTest {

    @Test
    public void testCache() {
        final Context context = getInstrumentation().getContext();
        AttributeCache.init(context);
        final WindowStyleCache<TestStyle> cache = new WindowStyleCache<>(TestStyle::new);
        final String packageName = context.getPackageName();
        final int theme = com.android.frameworks.wmtests.R.style.ActivityWindowStyleTest;
        final int userId = context.getUserId();
        final TestStyle style = cache.get(packageName, theme, userId);
        assertNotNull(style);
        assertSame(style, cache.get(packageName, theme, userId));

        cache.invalidatePackage(packageName);
        assertNotSame(style, cache.get(packageName, theme, userId));
    }

    private static class TestStyle {
        TestStyle(TypedArray array) {
        }
    }
}
