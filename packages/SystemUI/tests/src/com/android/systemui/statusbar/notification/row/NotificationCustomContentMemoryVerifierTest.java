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

package com.android.systemui.statusbar.notification.row;

import static com.android.systemui.statusbar.notification.row.NotificationCustomContentNotificationBuilder.buildAcceptableNotification;
import static com.android.systemui.statusbar.notification.row.NotificationCustomContentNotificationBuilder.buildAcceptableNotificationEntry;
import static com.android.systemui.statusbar.notification.row.NotificationCustomContentNotificationBuilder.buildOversizedNotification;
import static com.android.systemui.statusbar.notification.row.NotificationCustomContentNotificationBuilder.buildWarningSizedNotification;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.platform.test.annotations.EnableFlags;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.notification.Flags;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.log.LogAssertKt;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

@SmallTest
@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_NOTIFICATION_CUSTOM_VIEW_URI_RESTRICTION)
public class NotificationCustomContentMemoryVerifierTest extends SysuiTestCase {

    private static final String AUTHORITY = "notification.memory.test.authority";
    private static final Uri TEST_URI = new Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .path("path")
            .build();

    @Rule
    public PlatformCompatChangeRule mCompatChangeRule = new PlatformCompatChangeRule();

    @Before
    public void setUp() {
        TestImageContentProvider provider = new TestImageContentProvider(mContext);
        mContext.getContentResolver().addProvider(AUTHORITY, provider);
        provider.onCreate();
    }

    @Test
    @EnableCompatChanges({
            NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS})
    public void requiresImageViewMemorySizeCheck_customViewNotification_returnsTrue() {
        NotificationEntry entry =
                buildAcceptableNotificationEntry(
                        mContext);
        assertThat(NotificationCustomContentMemoryVerifier.requiresImageViewMemorySizeCheck(entry))
                .isTrue();
    }

    @Test
    @EnableCompatChanges({
            NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS})
    public void requiresImageViewMemorySizeCheck_plainNotification_returnsFalse() {
        Notification notification =
                new Notification.Builder(mContext, "ChannelId")
                        .setContentTitle("Just a notification")
                        .setContentText("Yep")
                        .build();
        NotificationEntry entry = new NotificationEntryBuilder().setNotification(
                notification).build();
        assertThat(NotificationCustomContentMemoryVerifier.requiresImageViewMemorySizeCheck(entry))
                .isFalse();
    }


    @Test
    @EnableCompatChanges({
            NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS})
    public void satisfiesMemoryLimits_smallNotification_returnsTrue() {
        Notification.Builder notification =
                buildAcceptableNotification(mContext,
                        TEST_URI);
        NotificationEntry entry = toEntry(notification);
        View inflatedView = inflateNotification(notification);
        assertThat(
                NotificationCustomContentMemoryVerifier.satisfiesMemoryLimits(inflatedView, entry)
        )
                .isTrue();
    }

    @Test
    @EnableCompatChanges({
            NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS})
    public void satisfiesMemoryLimits_oversizedNotification_returnsFalse() {
        Notification.Builder notification =
                buildOversizedNotification(mContext,
                        TEST_URI);
        NotificationEntry entry = toEntry(notification);
        View inflatedView = inflateNotification(notification);
        assertThat(
                NotificationCustomContentMemoryVerifier.satisfiesMemoryLimits(inflatedView, entry)
        ).isFalse();
    }

    @Test
    @DisableCompatChanges(
            {NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS}
    )
    public void satisfiesMemoryLimits_oversizedNotification_compatDisabled_returnsTrue() {
        Notification.Builder notification =
                buildOversizedNotification(mContext,
                        TEST_URI);
        NotificationEntry entry = toEntry(notification);
        View inflatedView = inflateNotification(notification);
        assertThat(
                NotificationCustomContentMemoryVerifier.satisfiesMemoryLimits(inflatedView, entry)
        ).isTrue();
    }

    @Test
    @EnableCompatChanges({
            NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS})
    public void satisfiesMemoryLimits_warningSizedNotification_returnsTrue() {
        Notification.Builder notification =
                buildWarningSizedNotification(mContext,
                        TEST_URI);
        NotificationEntry entry = toEntry(notification);
        View inflatedView = inflateNotification(notification);
        assertThat(
                NotificationCustomContentMemoryVerifier.satisfiesMemoryLimits(inflatedView, entry)
        )
                .isTrue();
    }

    @Test
    @EnableCompatChanges({
            NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS})
    public void satisfiesMemoryLimits_viewWithoutCustomNotificationRoot_returnsTrue() {
        NotificationEntry entry = new NotificationEntryBuilder().build();
        View view = new FrameLayout(mContext);
        LogAssertKt.assertRunnableLogsWtf(() -> {
            assertThat(NotificationCustomContentMemoryVerifier.satisfiesMemoryLimits(view, entry))
                    .isTrue();
        });
    }

    @Test
    @EnableCompatChanges({
            NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS})
    public void computeViewHierarchyImageViewSize_smallNotification_returnsSensibleValue() {
        Notification.Builder notification =
                buildAcceptableNotification(mContext,
                        TEST_URI);
        // This should have a size of a single image
        View inflatedView = inflateNotification(notification);
        assertThat(
                NotificationCustomContentMemoryVerifier.computeViewHierarchyImageViewSize(
                        inflatedView))
                .isGreaterThan(170000);
    }

    private View inflateNotification(Notification.Builder builder) {
        RemoteViews remoteViews = builder.createBigContentView();
        return remoteViews.apply(mContext, new FrameLayout(mContext));
    }

    private NotificationEntry toEntry(Notification.Builder builder) {
        return new NotificationEntryBuilder().setNotification(builder.build())
                .setUid(Process.myUid()).build();
    }


    /** This provider serves the images for inflation. */
    class TestImageContentProvider extends ContentProvider {

        TestImageContentProvider(Context context) {
            ProviderInfo info = new ProviderInfo();
            info.authority = AUTHORITY;
            info.exported = true;
            attachInfoForTesting(context, info);
            setAuthorities(AUTHORITY);
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) {
            return getContext().getResources().openRawResourceFd(
                    NotificationCustomContentNotificationBuilder.getDRAWABLE_IMAGE_RESOURCE())
                        .getParcelFileDescriptor();
        }

        @Override
        public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts) {
            return getContext().getResources().openRawResourceFd(
                    NotificationCustomContentNotificationBuilder.getDRAWABLE_IMAGE_RESOURCE());
        }

        @Override
        public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts,
                CancellationSignal signal) throws FileNotFoundException {
            return openTypedAssetFile(uri, mimeTypeFilter, opts);
        }

        @Override
        public int delete(Uri uri, Bundle extras) {
            return 0;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public String getType(Uri uri) {
            return "image/png";
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values, Bundle extras) {
            return super.insert(uri, values, extras);
        }

        @Override
        public Cursor query(Uri uri, String[] projection, Bundle queryArgs,
                CancellationSignal cancellationSignal) {
            return super.query(uri, projection, queryArgs, cancellationSignal);
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return null;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }
    }


}
