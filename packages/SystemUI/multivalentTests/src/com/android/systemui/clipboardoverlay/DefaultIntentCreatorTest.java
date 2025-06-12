/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.clipboardoverlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableString;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.res.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DefaultIntentCreatorTest extends SysuiTestCase {
    private static final int EXTERNAL_INTENT_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION;

    private final DefaultIntentCreator mIntentCreator  = new DefaultIntentCreator();

    @Test
    public void test_getTextEditorIntent() {
        Intent intent = mIntentCreator.getTextEditorIntent(getContext());
        assertEquals(new ComponentName(getContext(), EditTextActivity.class),
                intent.getComponent());
        assertFlags(intent, Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    @Test
    public void test_getRemoteCopyIntent() {
        getContext().getOrCreateTestableResources().addOverride(R.string.config_remoteCopyPackage,
                "");

        ClipData clipData = ClipData.newPlainText("Test", "Test Item");
        Intent intent = mIntentCreator.getRemoteCopyIntent(clipData, getContext());

        assertEquals(null, intent.getComponent());
        assertFlags(intent, EXTERNAL_INTENT_FLAGS);
        assertEquals(clipData, intent.getClipData());

        // Try again with a remote copy component
        ComponentName fakeComponent = new ComponentName("com.android.remotecopy",
                "com.android.remotecopy.RemoteCopyActivity");
        getContext().getOrCreateTestableResources().addOverride(R.string.config_remoteCopyPackage,
                fakeComponent.flattenToString());

        intent = mIntentCreator.getRemoteCopyIntent(clipData, getContext());
        assertEquals(fakeComponent, intent.getComponent());
    }

    @Test
    public void test_getImageEditIntentAsync() {
        getContext().getOrCreateTestableResources().addOverride(R.string.config_screenshotEditor,
                "");
        Uri fakeUri = Uri.parse("content://foo");
        final AtomicReference<Intent> intentHolder = new AtomicReference<>(null);
        mIntentCreator.getImageEditIntentAsync(fakeUri, getContext(), output -> {
            intentHolder.set(output);
        });

        Intent intent = intentHolder.get();
        assertEquals(Intent.ACTION_EDIT, intent.getAction());
        assertEquals("image/*", intent.getType());
        assertEquals(null, intent.getComponent());
        assertEquals("clipboard", intent.getStringExtra("edit_source"));
        assertFlags(intent, EXTERNAL_INTENT_FLAGS);

        // try again with an editor component
        ComponentName fakeComponent = new ComponentName("com.android.remotecopy",
                "com.android.remotecopy.RemoteCopyActivity");
        getContext().getOrCreateTestableResources().addOverride(R.string.config_screenshotEditor,
                fakeComponent.flattenToString());
        mIntentCreator.getImageEditIntentAsync(fakeUri, getContext(), output -> {
            intentHolder.set(output);
        });
        assertEquals(fakeComponent, intentHolder.get().getComponent());
    }

    @Test
    public void test_getShareIntent_plaintext() {
        ClipData clipData = ClipData.newPlainText("Test", "Test Item");
        Intent intent = mIntentCreator.getShareIntent(clipData, getContext());

        assertEquals(Intent.ACTION_CHOOSER, intent.getAction());
        assertFlags(intent, EXTERNAL_INTENT_FLAGS);
        Intent target = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        assertEquals("Test Item", target.getStringExtra(Intent.EXTRA_TEXT));
        assertEquals("text/plain", target.getType());
    }

    @Test
    public void test_getShareIntent_html() {
        ClipData clipData = ClipData.newHtmlText("Test", "Some HTML",
                "<b>Some HTML</b>");
        Intent intent = mIntentCreator.getShareIntent(clipData, getContext());

        assertEquals(Intent.ACTION_CHOOSER, intent.getAction());
        assertFlags(intent, EXTERNAL_INTENT_FLAGS);
        Intent target = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        assertEquals("Some HTML", target.getStringExtra(Intent.EXTRA_TEXT));
        assertEquals("text/plain", target.getType());
    }

    @Test
    public void test_getShareIntent_image() {
        Uri uri = Uri.parse("content://something");
        ClipData clipData = new ClipData("Test", new String[]{"image/png"},
                new ClipData.Item(uri));
        Intent intent = mIntentCreator.getShareIntent(clipData, getContext());

        assertEquals(Intent.ACTION_CHOOSER, intent.getAction());
        assertFlags(intent, EXTERNAL_INTENT_FLAGS);
        Intent target = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        assertEquals(uri, target.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class));
        assertEquals(uri, target.getClipData().getItemAt(0).getUri());
        assertEquals("image/png", target.getType());
    }

    @Test
    public void test_getShareIntent_spannableText() {
        ClipData clipData = ClipData.newPlainText("Test", new SpannableString("Test Item"));
        Intent intent = mIntentCreator.getShareIntent(clipData, getContext());

        assertEquals(Intent.ACTION_CHOOSER, intent.getAction());
        assertFlags(intent, EXTERNAL_INTENT_FLAGS);
        Intent target = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        assertEquals("Test Item", target.getStringExtra(Intent.EXTRA_TEXT));
        assertEquals("text/plain", target.getType());
    }

    // Assert that the given flags are set
    private void assertFlags(Intent intent, int flags) {
        assertTrue((intent.getFlags() & flags) == flags);
    }

}
