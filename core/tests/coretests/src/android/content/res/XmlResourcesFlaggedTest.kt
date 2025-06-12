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
package android.content.res

import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.TypedValue

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry

import com.android.frameworks.coretests.R
import com.android.internal.pm.pkg.parsing.ParsingPackageUtils

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue


import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

import java.io.IOException

/**
* Tests for flag handling within Resources.loadXmlResourceParser() and methods that call it.
*/
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4::class)
@android.platform.test.annotations.DisabledOnRavenwood(bug = 396458006,
    reason = "Resource flags don't fully work on Ravenwood yet")
class XmlResourcesFlaggedTest {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private var mResources: Resources = Resources(null)

    @Before
    fun setup() {
        mResources = InstrumentationRegistry.getInstrumentation().getContext().getResources()
        mResources.getImpl().flushLayoutCache()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LAYOUT_READWRITE_FLAGS)
    fun flaggedXmlTypedValueMarkedAsSuch() {
        val tv = TypedValue()
        mResources.getImpl().getValue(R.xml.flags, tv, false)
        assertTrue(tv.usesFeatureFlags)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LAYOUT_READWRITE_FLAGS)
    @Throws(IOException::class, XmlPullParserException::class)
    fun parsedFlaggedXmlWithTrueOneElement() {
        ParsingPackageUtils.getAconfigFlags()
            .addFlagValuesForTesting(mapOf("android.content.res.always_false" to false))
        val tv = TypedValue()
        mResources.getImpl().getValue(R.xml.flags, tv, false)
        val parser = mResources.loadXmlResourceParser(
            tv.string.toString(),
            R.xml.flags,
            tv.assetCookie,
            "xml",
            true
        )
        assertEquals(XmlPullParser.START_DOCUMENT, parser.next())
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("first", parser.getName())
        assertEquals(XmlPullParser.END_TAG, parser.next())
        assertEquals(XmlPullParser.END_DOCUMENT, parser.next())
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LAYOUT_READWRITE_FLAGS)
    @Throws(IOException::class, XmlPullParserException::class)
    fun parsedFlaggedXmlWithFalseTwoElements() {
        val tv = TypedValue()
        mResources.getImpl().getValue(R.xml.flags, tv, false)
        val parser = mResources.loadXmlResourceParser(
            tv.string.toString(),
            R.xml.flags,
            tv.assetCookie,
            "xml",
            false
        )
        assertEquals(XmlPullParser.START_DOCUMENT, parser.next())
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("first", parser.getName())
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("second", parser.getName())
        assertEquals(XmlPullParser.END_TAG, parser.next())
        assertEquals(XmlPullParser.END_TAG, parser.next())
        assertEquals(XmlPullParser.END_DOCUMENT, parser.next())
    }
}
