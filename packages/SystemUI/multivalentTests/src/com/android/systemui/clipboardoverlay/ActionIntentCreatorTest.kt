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

package com.android.systemui.clipboardoverlay

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.text.SpannableString
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.mockito.mock
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ActionIntentCreatorTest : SysuiTestCase() {
    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)
    private val testScope = TestScope(mainDispatcher)
    val packageManager = mock<PackageManager>()

    val creator =
        ActionIntentCreator(context, packageManager, testScope.backgroundScope, mainDispatcher)

    @Test
    fun test_getTextEditorIntent() {
        val intent = creator.getTextEditorIntent(context)
        assertEquals(ComponentName(context, EditTextActivity::class.java), intent.component)
        assertFlags(intent, Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    @Test
    fun test_getRemoteCopyIntent() {
        context.getOrCreateTestableResources().addOverride(R.string.config_remoteCopyPackage, "")

        val clipData = ClipData.newPlainText("Test", "Test Item")
        var intent = creator.getRemoteCopyIntent(clipData, context)

        assertEquals(null, intent.component)
        assertFlags(intent, EXTERNAL_INTENT_FLAGS)
        assertEquals(clipData, intent.clipData)

        // Try again with a remote copy component
        val fakeComponent =
            ComponentName("com.android.remotecopy", "com.android.remotecopy.RemoteCopyActivity")
        context
            .getOrCreateTestableResources()
            .addOverride(R.string.config_remoteCopyPackage, fakeComponent.flattenToString())

        intent = creator.getRemoteCopyIntent(clipData, context)
        assertEquals(fakeComponent, intent.component)
    }

    @Test
    fun test_getImageEditIntent_noDefault() = runTest {
        context.getOrCreateTestableResources().addOverride(R.string.config_screenshotEditor, "")
        val fakeUri = Uri.parse("content://foo")
        var intent = creator.getImageEditIntent(fakeUri, context)

        assertEquals(Intent.ACTION_EDIT, intent.action)
        assertEquals("image/*", intent.type)
        assertEquals(null, intent.component)
        assertEquals("clipboard", intent.getStringExtra("edit_source"))
        assertFlags(intent, EXTERNAL_INTENT_FLAGS)
    }

    @Test
    fun test_getImageEditIntent_defaultProvided() = runTest {
        val fakeUri = Uri.parse("content://foo")

        val fakeComponent =
            ComponentName("com.android.remotecopy", "com.android.remotecopy.RemoteCopyActivity")
        context
            .getOrCreateTestableResources()
            .addOverride(R.string.config_screenshotEditor, fakeComponent.flattenToString())
        val intent = creator.getImageEditIntent(fakeUri, context)
        assertEquals(fakeComponent, intent.component)
    }

    @Test
    fun test_getImageEditIntent_preferredProvidedButDisabled() = runTest {
        val fakeUri = Uri.parse("content://foo")

        val defaultComponent = ComponentName("com.android.foo", "com.android.foo.Something")
        val preferredComponent = ComponentName("com.android.bar", "com.android.bar.Something")

        val packageInfo =
            PackageInfo().apply {
                activities = arrayOf() // no activities
            }
        whenever(packageManager.getPackageInfo(eq(preferredComponent.packageName), anyInt()))
            .thenReturn(packageInfo)

        context
            .getOrCreateTestableResources()
            .addOverride(R.string.config_screenshotEditor, defaultComponent.flattenToString())
        context
            .getOrCreateTestableResources()
            .addOverride(
                R.string.config_preferredScreenshotEditor,
                preferredComponent.flattenToString(),
            )
        val intent = creator.getImageEditIntent(fakeUri, context)
        assertEquals(defaultComponent, intent.component)
    }

    @Test
    fun test_getImageEditIntent_preferredProvided() = runTest {
        val fakeUri = Uri.parse("content://foo")

        val defaultComponent = ComponentName("com.android.foo", "com.android.foo.Something")
        val preferredComponent = ComponentName("com.android.bar", "com.android.bar.Something")

        val packageInfo =
            PackageInfo().apply {
                activities =
                    arrayOf(
                        ActivityInfo().apply {
                            packageName = preferredComponent.packageName
                            name = preferredComponent.className
                        }
                    )
            }
        whenever(packageManager.getPackageInfo(eq(preferredComponent.packageName), anyInt()))
            .thenReturn(packageInfo)

        context
            .getOrCreateTestableResources()
            .addOverride(R.string.config_screenshotEditor, defaultComponent.flattenToString())
        context
            .getOrCreateTestableResources()
            .addOverride(
                R.string.config_preferredScreenshotEditor,
                preferredComponent.flattenToString(),
            )
        val intent = creator.getImageEditIntent(fakeUri, context)
        assertEquals(preferredComponent, intent.component)
    }

    @Test
    fun test_getShareIntent_plaintext() {
        val clipData = ClipData.newPlainText("Test", "Test Item")
        val intent = creator.getShareIntent(clipData, context)

        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        assertFlags(intent, EXTERNAL_INTENT_FLAGS)
        val target = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals("Test Item", target?.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("text/plain", target?.type)
    }

    @Test
    fun test_getShareIntent_html() {
        val clipData = ClipData.newHtmlText("Test", "Some HTML", "<b>Some HTML</b>")
        val intent = creator.getShareIntent(clipData, getContext())

        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        assertFlags(intent, EXTERNAL_INTENT_FLAGS)
        val target = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals("Some HTML", target?.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("text/plain", target?.type)
    }

    @Test
    fun test_getShareIntent_image() {
        val uri = Uri.parse("content://something")
        val clipData = ClipData("Test", arrayOf("image/png"), ClipData.Item(uri))
        val intent = creator.getShareIntent(clipData, context)

        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        assertFlags(intent, EXTERNAL_INTENT_FLAGS)
        val target = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(uri, target?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(uri, target?.clipData?.getItemAt(0)?.uri)
        assertEquals("image/png", target?.type)
    }

    @Test
    fun test_getShareIntent_spannableText() {
        val clipData = ClipData.newPlainText("Test", SpannableString("Test Item"))
        val intent = creator.getShareIntent(clipData, context)

        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        assertFlags(intent, EXTERNAL_INTENT_FLAGS)
        val target = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals("Test Item", target?.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("text/plain", target?.type)
    }

    // Assert that the given flags are set
    private fun assertFlags(intent: Intent, flags: Int) {
        assertTrue((intent.flags and flags) == flags)
    }

    companion object {
        private const val EXTERNAL_INTENT_FLAGS: Int =
            (Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
