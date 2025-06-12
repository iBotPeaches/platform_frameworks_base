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
 * limitations under the License
 */

@file:JvmName("NotificationCustomContentNotificationBuilder")

package com.android.systemui.statusbar.notification.row

import android.app.Notification
import android.app.Notification.DecoratedCustomViewStyle
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Process
import android.widget.RemoteViews
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.tests.R
import org.hamcrest.Matchers.lessThan
import org.junit.Assume.assumeThat

public val DRAWABLE_IMAGE_RESOURCE = R.drawable.romainguy_rockaway

fun buildAcceptableNotificationEntry(context: Context): NotificationEntry {
    return NotificationEntryBuilder()
        .setNotification(buildAcceptableNotification(context, null).build())
        .setUid(Process.myUid())
        .build()
}

fun buildAcceptableNotification(context: Context, uri: Uri?): Notification.Builder =
    buildNotification(context, uri, 1)

fun buildOversizedNotification(context: Context, uri: Uri): Notification.Builder {
    val numImagesForOversize =
        (NotificationCustomContentMemoryVerifier.getStripViewSizeLimit(context) /
            drawableSizeOnDevice(context)) + 2
    return buildNotification(context, uri, numImagesForOversize)
}

fun buildWarningSizedNotification(context: Context, uri: Uri): Notification.Builder {
    val numImagesForOversize =
        (NotificationCustomContentMemoryVerifier.getWarnViewSizeLimit(context) /
            drawableSizeOnDevice(context)) + 1
    // The size needs to be smaller than outright stripping size.
    assumeThat(
        numImagesForOversize * drawableSizeOnDevice(context),
        lessThan(NotificationCustomContentMemoryVerifier.getStripViewSizeLimit(context)),
    )
    return buildNotification(context, uri, numImagesForOversize)
}

fun buildNotification(context: Context, uri: Uri?, numImages: Int): Notification.Builder {
    val remoteViews = RemoteViews(context.packageName, R.layout.custom_view_flipper)
    repeat(numImages) { i ->
        val remoteViewFlipperImageView =
            RemoteViews(context.packageName, R.layout.custom_view_flipper_image)

        if (uri == null) {
            remoteViewFlipperImageView.setImageViewResource(
                R.id.imageview,
                R.drawable.romainguy_rockaway,
            )
        } else {
            val imageUri = uri.buildUpon().appendPath(i.toString()).build()
            remoteViewFlipperImageView.setImageViewUri(R.id.imageview, imageUri)
        }
        remoteViews.addView(R.id.flipper, remoteViewFlipperImageView)
    }

    return Notification.Builder(context, "ChannelId")
        .setSmallIcon(android.R.drawable.ic_info)
        .setStyle(DecoratedCustomViewStyle())
        .setCustomContentView(remoteViews)
        .setCustomBigContentView(remoteViews)
        .setContentTitle("This is a remote view!")
}

fun drawableSizeOnDevice(context: Context): Int {
    val drawable = context.resources.getDrawable(DRAWABLE_IMAGE_RESOURCE)
    return (drawable as BitmapDrawable).bitmap.allocationByteCount
}
