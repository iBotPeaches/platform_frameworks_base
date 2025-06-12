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

package com.android.systemui.statusbar.notification.row

import android.app.compat.CompatChanges
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.VisibleForTesting
import com.android.app.tracing.traceSection
import com.android.systemui.statusbar.notification.collection.NotificationEntry

/** Checks whether Notifications with Custom content views conform to configured memory limits. */
object NotificationCustomContentMemoryVerifier {

    private const val NOTIFICATION_SERVICE_TAG = "NotificationService"

    /** Notifications with custom views need to conform to maximum memory consumption. */
    @JvmStatic
    fun requiresImageViewMemorySizeCheck(entry: NotificationEntry): Boolean {
        if (!com.android.server.notification.Flags.notificationCustomViewUriRestriction()) {
            return false
        }

        return entry.containsCustomViews()
    }

    /**
     * This walks the custom view hierarchy contained in the passed Notification view and determines
     * if the total memory consumption of all image views satisfies the limit set by
     * [getStripViewSizeLimit]. It will also log to logcat if the limit exceeds
     * [getWarnViewSizeLimit].
     *
     * @return true if the Notification conforms to the view size limits.
     */
    @JvmStatic
    fun satisfiesMemoryLimits(view: View, entry: NotificationEntry): Boolean {
        val mainColumnView =
            view.findViewById<View>(com.android.internal.R.id.notification_main_column)
        if (mainColumnView == null) {
            Log.wtf(
                NOTIFICATION_SERVICE_TAG,
                "R.id.notification_main_column view should not be null!",
            )
            return true
        }

        val memorySize =
            traceSection("computeViewHiearchyImageViewSize") {
                computeViewHierarchyImageViewSize(view)
            }

        if (memorySize > getStripViewSizeLimit(view.context)) {
            val stripOversizedView = isCompatChangeEnabledForUid(entry.sbn.uid)
            if (stripOversizedView) {
                Log.w(
                    NOTIFICATION_SERVICE_TAG,
                    "Dropped notification due to too large RemoteViews ($memorySize bytes) on " +
                        "pkg: ${entry.sbn.packageName} tag: ${entry.sbn.tag} id: ${entry.sbn.id}",
                )
            } else {
                Log.w(
                    NOTIFICATION_SERVICE_TAG,
                    "RemoteViews too large on pkg: ${entry.sbn.packageName} " +
                        "tag: ${entry.sbn.tag} id: ${entry.sbn.id} " +
                        "this WILL notification WILL be dropped when targetSdk " +
                        "is set to ${Build.VERSION_CODES.BAKLAVA}!",
                )
            }

            // We still warn for size, but return "satisfies = ok" if the target SDK
            // is too low.
            return !stripOversizedView
        }

        if (memorySize > getWarnViewSizeLimit(view.context)) {
            // We emit the same warning as NotificationManagerService does to keep some consistency
            // for developers.
            Log.w(
                NOTIFICATION_SERVICE_TAG,
                "RemoteViews too large on pkg: ${entry.sbn.packageName} " +
                    "tag: ${entry.sbn.tag} id: ${entry.sbn.id} " +
                    "this notifications might be dropped in a future release",
            )
        }
        return true
    }

    private fun isCompatChangeEnabledForUid(uid: Int): Boolean =
        try {
            CompatChanges.isChangeEnabled(
                NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS,
                uid,
            )
        } catch (e: RuntimeException) {
            Log.wtf(NOTIFICATION_SERVICE_TAG, "Failed to contact system_server for compat change.")
            false
        }

    @VisibleForTesting
    @JvmStatic
    fun computeViewHierarchyImageViewSize(view: View): Int =
        when (view) {
            is ViewGroup -> {
                var use = 0
                for (i in 0 until view.childCount) {
                    use += computeViewHierarchyImageViewSize(view.getChildAt(i))
                }
                use
            }
            is ImageView -> computeImageViewSize(view)
            else -> 0
        }

    /**
     * Returns the memory size of a Bitmap contained in a passed [ImageView] in bytes. If the view
     * contains any other kind of drawable, the memory size is estimated from its intrinsic
     * dimensions.
     *
     * @return Bitmap size in bytes or 0 if no drawable is set.
     */
    private fun computeImageViewSize(view: ImageView): Int {
        val drawable = view.drawable
        return computeDrawableSize(drawable)
    }

    private fun computeDrawableSize(drawable: Drawable?): Int {
        return when (drawable) {
            null -> 0
            is AdaptiveIconDrawable ->
                computeDrawableSize(drawable.foreground) +
                    computeDrawableSize(drawable.background) +
                    computeDrawableSize(drawable.monochrome)
            is BitmapDrawable -> drawable.bitmap.allocationByteCount
            // People can sneak large drawables into those custom memory views via resources -
            // we use the intrisic size as a proxy for how much memory rendering those will
            // take.
            else -> drawable.intrinsicWidth * drawable.intrinsicHeight * 4
        }
    }

    /** @return Size of remote views after which a size warning is logged. */
    @VisibleForTesting
    fun getWarnViewSizeLimit(context: Context): Int =
        context.resources.getInteger(
            com.android.internal.R.integer.config_notificationWarnRemoteViewSizeBytes
        )

    /** @return Size of remote views after which the notification is dropped. */
    @VisibleForTesting
    fun getStripViewSizeLimit(context: Context): Int =
        context.resources.getInteger(
            com.android.internal.R.integer.config_notificationStripRemoteViewSizeBytes
        )
}
