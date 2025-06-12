/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.overlaytest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

import android.annotation.NonNull;
import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.res.Resources;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@RunWith(JUnit4.class)
@MediumTest
public class FabricatedOverlaysTest {
    private static final String TAG = "FabricatedOverlaysTest";
    private static final String TEST_INT_RESOURCE = "integer/overlaidInt";
    private static final String TEST_FLOAT_RESOURCE = "dimen/overlaidFloat";
    private static final String TEST_OVERLAY_NAME = "Test";

    private Context mContext;
    private Resources mResources;
    private OverlayManager mOverlayManager;
    private int mUserId;
    private UserHandle mUserHandle;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mResources = mContext.getResources();
        mOverlayManager = mContext.getSystemService(OverlayManager.class);
        mUserId = UserHandle.myUserId();
        mUserHandle = UserHandle.of(mUserId);
    }

    @After
    public void tearDown() throws Exception {
        final OverlayManagerTransaction.Builder cleanUp = new OverlayManagerTransaction.Builder();
        mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName(), mUserHandle).forEach(
                info -> {
                    if (info.isFabricated()) {
                        cleanUp.unregisterFabricatedOverlay(info.getOverlayIdentifier());
                    }
                });
        mOverlayManager.commit(cleanUp.build());
    }

    @Test
    public void testFabricatedOverlay() throws Exception {
        final FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                mContext.getPackageName(), TEST_OVERLAY_NAME, mContext.getPackageName())
                .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 1)
                .build();

        waitForIntResourceValue(0);
        mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(overlay)
                .build());

        OverlayInfo info = mOverlayManager.getOverlayInfo(overlay.getIdentifier(), mUserHandle);
        assertNotNull(info);
        assertFalse(info.isEnabled());

        mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                .setEnabled(overlay.getIdentifier(), true, mUserId)
                .build());

        info = mOverlayManager.getOverlayInfo(overlay.getIdentifier(), mUserHandle);
        assertNotNull(info);
        assertTrue(info.isEnabled());

        waitForIntResourceValue(1);
        mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                .unregisterFabricatedOverlay(overlay.getIdentifier())
                .build());

        waitForIntResourceValue(0);
    }

    @Test
    public void testRegisterEnableAtomic() throws Exception {
        final FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                mContext.getPackageName(), TEST_OVERLAY_NAME, mContext.getPackageName())
                .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 1)
                .build();

        waitForIntResourceValue(0);
        mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(overlay)
                .setEnabled(overlay.getIdentifier(), true, mUserId)
                .build());

        waitForIntResourceValue(1);
    }

    @Test
    public void testRegisterTwice() throws Exception {
        FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                mContext.getPackageName(), TEST_OVERLAY_NAME, mContext.getPackageName())
                .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 1)
                .build();

        waitForIntResourceValue(0);
        mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(overlay)
                .setEnabled(overlay.getIdentifier(), true, mUserId)
                .build());

        waitForIntResourceValue(1);
        overlay = new FabricatedOverlay.Builder(
                mContext.getPackageName(), TEST_OVERLAY_NAME, mContext.getPackageName())
                .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 2)
                .build();

        mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(overlay)
                .build());
        waitForIntResourceValue(2);
    }

    @Test
    public void testInvalidOwningPackageName() throws Exception {
        final FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                "android", TEST_OVERLAY_NAME, mContext.getPackageName())
                .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 1)
                .build();

        waitForIntResourceValue(0);
        assertThrows(SecurityException.class, () ->
            mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                    .registerFabricatedOverlay(overlay)
                    .setEnabled(overlay.getIdentifier(), true, mUserId)
                    .build()));

        assertNull(mOverlayManager.getOverlayInfo(overlay.getIdentifier(), mUserHandle));
    }

    @Test
    public void testInvalidOverlayName() throws Exception {
        final FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                mContext.getPackageName(), "invalid@name", mContext.getPackageName())
                .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 1)
                .build();

        waitForIntResourceValue(0);
        assertThrows(SecurityException.class, () ->
                mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(overlay)
                        .setEnabled(overlay.getIdentifier(), true, mUserId)
                        .build()));

        assertNull(mOverlayManager.getOverlayInfo(overlay.getIdentifier(), mUserHandle));
    }

    @Test
    public void testOverlayIdentifierLongest() throws Exception {
        final int maxLength = 255 - 11; // 11 reserved characters
        final String longestName = String.join("",
                Collections.nCopies(maxLength - mContext.getPackageName().length(), "a"));
        {
            FabricatedOverlay overlay = new FabricatedOverlay.Builder(mContext.getPackageName(),
                    longestName, mContext.getPackageName())
                    .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 1)
                    .build();

            mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                    .registerFabricatedOverlay(overlay)
                    .build());
            assertNotNull(mOverlayManager.getOverlayInfo(overlay.getIdentifier(), mUserHandle));
        }
        {
            FabricatedOverlay overlay = new FabricatedOverlay.Builder(mContext.getPackageName(),
                    longestName + "a", mContext.getPackageName())
                    .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 1)
                    .build();

            assertThrows(SecurityException.class, () ->
                    mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                            .registerFabricatedOverlay(overlay)
                            .build()));

            assertNull(mOverlayManager.getOverlayInfo(overlay.getIdentifier(), mUserHandle));
        }
    }

    @Test
    public void setResourceValue_withNullResourceName() throws Exception {
        final FabricatedOverlay.Builder builder = new FabricatedOverlay.Builder(
                "android", TEST_OVERLAY_NAME, mContext.getPackageName());

        assertThrows(NullPointerException.class,
                () -> builder.setResourceValue(null, TypedValue.TYPE_INT_DEC, 1));
    }

    @Test
    public void setResourceValue_withEmptyResourceName() throws Exception {
        final FabricatedOverlay.Builder builder = new FabricatedOverlay.Builder(
                "android", TEST_OVERLAY_NAME, mContext.getPackageName());

        assertThrows(IllegalArgumentException.class,
                () -> builder.setResourceValue("", TypedValue.TYPE_INT_DEC, 1));
    }

    @Test
    public void setResourceValue_withEmptyPackageName() throws Exception {
        final FabricatedOverlay.Builder builder = new FabricatedOverlay.Builder(
                "android", TEST_OVERLAY_NAME, mContext.getPackageName());

        assertThrows(IllegalArgumentException.class,
                () -> builder.setResourceValue(":color/mycolor", TypedValue.TYPE_INT_DEC, 1));
    }

    @Test
    public void setResourceValue_withInvalidTypeName() throws Exception {
        final FabricatedOverlay.Builder builder = new FabricatedOverlay.Builder(
                "android", TEST_OVERLAY_NAME, mContext.getPackageName());

        assertThrows(IllegalArgumentException.class,
                () -> builder.setResourceValue("c/mycolor", TypedValue.TYPE_INT_DEC, 1));
    }

    @Test
    public void setResourceValue_withEmptyTypeName() throws Exception {
        final FabricatedOverlay.Builder builder = new FabricatedOverlay.Builder(
                "android", TEST_OVERLAY_NAME, mContext.getPackageName());

        assertThrows(IllegalArgumentException.class,
                () -> builder.setResourceValue("/mycolor", TypedValue.TYPE_INT_DEC, 1));
    }

    @Test
    public void testInvalidResourceValues() throws Exception {
        final FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                "android", TEST_OVERLAY_NAME, mContext.getPackageName())
                .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 1)
                .setResourceValue("color/something", TypedValue.TYPE_INT_DEC, 1)
                .build();

        waitForIntResourceValue(0);
        assertThrows(SecurityException.class, () ->
                mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(overlay)
                        .setEnabled(overlay.getIdentifier(), true, mUserId)
                        .build()));

        assertNull(mOverlayManager.getOverlayInfo(overlay.getIdentifier(), mUserHandle));
    }

    @Test
    public void testTransactionFailRollback() throws Exception {
        final FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                mContext.getPackageName(), TEST_OVERLAY_NAME, mContext.getPackageName())
                .setResourceValue(TEST_INT_RESOURCE, TypedValue.TYPE_INT_DEC, 1)
                .build();

        waitForIntResourceValue(0);
        assertThrows(SecurityException.class, () ->
                mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(overlay)
                        .setEnabled(overlay.getIdentifier(), true, mUserId)
                        .setEnabled(new OverlayIdentifier("not-valid"), true, mUserId)
                        .build()));

        assertNull(mOverlayManager.getOverlayInfo(overlay.getIdentifier(), mUserHandle));
    }

    @Test
    public void setResourceValue_forFloatType_succeeds() throws Exception {
        final float overlaidValue = 5.7f;
        final FabricatedOverlay overlay = new FabricatedOverlay.Builder(
                mContext.getPackageName(), TEST_OVERLAY_NAME, mContext.getPackageName()).build();
        overlay.setResourceValue(TEST_FLOAT_RESOURCE, overlaidValue, null /*  configuration */);

        waitForFloatResourceValue(0);
        mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(overlay)
                .setEnabled(overlay.getIdentifier(), true, mUserId)
                .build());

        waitForFloatResourceValue(overlaidValue);
    }

    private void waitForIntResourceValue(final int expectedValue) throws TimeoutException {
        waitForResourceValue(expectedValue, TEST_INT_RESOURCE, id -> mResources.getInteger(id));
    }

    private void waitForFloatResourceValue(final float expectedValue) throws TimeoutException {
        waitForResourceValue(expectedValue, TEST_FLOAT_RESOURCE, id -> mResources.getFloat(id));
    }

    private <T> void waitForResourceValue(final T expectedValue, final String resourceName,
            @NonNull Function<Integer, T> resourceValueEmitter) throws TimeoutException {
        final long timeOutDuration = 10000;
        final long endTime = System.currentTimeMillis() + timeOutDuration;
        final int resourceId = mResources.getIdentifier(resourceName, "",
                mContext.getPackageName());
        T resourceValue = null;
        while (System.currentTimeMillis() < endTime) {
            resourceValue = resourceValueEmitter.apply(resourceId);
            if (Objects.equals(expectedValue, resourceValue)) {
                return;
            }
        }
        final String paths = TextUtils.join(",", mResources.getAssets().getApkPaths());
        Log.w(TAG, "current paths: [" + paths + "]", new Throwable());
        throw new TimeoutException("Timed out waiting for '" + resourceName + "' value to equal '"
                + expectedValue + "': current value is '" + resourceValue + "'");
    }
}
