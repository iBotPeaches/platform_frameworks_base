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

package com.android.internal.logging;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.protolog.common.IProtoLogGroup;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ProtoLogTest {
    @Test
    public void canTrace() {
        ProtoLog.init(TEST_GROUP_1, TEST_GROUP_2);

        ProtoLog.v(TEST_GROUP_1, "Verbose message");
        ProtoLog.d(TEST_GROUP_1, "Debug message");
        ProtoLog.i(TEST_GROUP_1, "Info message");
        ProtoLog.w(TEST_GROUP_1, "Warning message");
        ProtoLog.e(TEST_GROUP_1, "Error message");
        ProtoLog.wtf(TEST_GROUP_1, "Wtf message");

        ProtoLog.v(TEST_GROUP_2, "Verbose message");
        ProtoLog.d(TEST_GROUP_2, "Debug message");
        ProtoLog.i(TEST_GROUP_2, "Info message");
        ProtoLog.w(TEST_GROUP_2, "Warning message");
        ProtoLog.e(TEST_GROUP_2, "Error message");
        ProtoLog.wtf(TEST_GROUP_2, "Wtf message");
    }

    private static final IProtoLogGroup TEST_GROUP_1 = new ProtoLogTestGroup("TEST_TAG_1", 1);
    private static final IProtoLogGroup TEST_GROUP_2 = new ProtoLogTestGroup("TEST_TAG_2", 2);

}
