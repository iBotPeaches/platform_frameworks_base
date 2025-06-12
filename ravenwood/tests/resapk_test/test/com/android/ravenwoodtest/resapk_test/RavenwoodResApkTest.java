/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.ravenwoodtest.resapk_test;


import static com.google.common.truth.Truth.assertThat;

import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ravenwood.common.RavenwoodCommonUtils;
import com.android.ravenwood.restest_apk.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class RavenwoodResApkTest {
    private static final String TAG = "RavenwoodResApkTest";

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    /**
     * Ensure the file "ravenwood-res.apk" exists.
     */
    @Test
    public void testResApkExists() {
        var file = "ravenwood-res-apks/ravenwood-res.apk";

        assertTrue(new File(file).exists());
    }

    @Test
    public void testFrameworkResExists() {
        var file = "ravenwood-data/framework-res.apk";

        assertTrue(new File(
                RavenwoodCommonUtils.getRavenwoodRuntimePath() + "/" + file).exists());
    }

    @Test
    public void testReadStringNoFlag() {
        assertThat(sContext.getString(R.string.test_string_1)).isEqualTo("Test String 1");
    }

    @Test
    public void testReadStringRoFlagEnabled() {
        assertThat(sContext.getString(R.string.test_string_enabled)).isEqualTo("Enabled");
    }

    @Test
    public void testReadStringRoFlagDisabled() {
        assertThrows(android.content.res.Resources.NotFoundException.class, () -> {
            sContext.getString(R.string.test_string_disabled);
        });
    }

    /**
     * Look into the layout and collect the "text" attribute.
     *
     * It _should_ respect android:featureFlag, but until b/396458006 gets fixed, this returns
     * even disabled elements.
     */
    private List<String> getTextsFromEnabledChildren() throws Exception {
        try (XmlResourceParser parser = sContext.getResources().getLayout(R.layout.testlayout)) {
            assertNotNull(parser);

            var ret = new ArrayList<String>();

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                var text = parser.getAttributeValue(null, "text");
                if (text == null) {
                    continue;
                }

                Log.d(TAG, "Found tag: " + parser.getName() + " text='" + text + "'");
                ret.add(text);
            }
            return ret;
        }
    }

    @Test
    public void testElementNoFlag() throws Exception {
        assertThat(getTextsFromEnabledChildren()).contains("no-flags");
    }

    @Test
    public void testElementWithRoFlagEnabled() throws Exception {
        assertThat(getTextsFromEnabledChildren()).contains("ro-enabled");
    }

    @Test
    public void testElementWithRoFlagDisabled() throws Exception {
        assertThat(getTextsFromEnabledChildren()).doesNotContain("ro-disabled");
    }

    @Test
    public void testElementWithRwFlagEnabled() throws Exception {
        assertThat(getTextsFromEnabledChildren()).contains("rw-enabled");
    }

    @Test
    @DisabledOnRavenwood(bug = 396458006,
            reason = "RW flags in XML are all handled as enabled for now")
    public void testElementWithRwFlagDisabled() throws Exception {
        assertThat(getTextsFromEnabledChildren()).doesNotContain("rw-disabled");
    }
}
