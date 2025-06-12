/*
 * Copyright (c) 2022 The Android Open Source Project
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
 *
 */

package com.android.systemui.statusbar.notification.row

import android.view.ViewGroup
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.dagger.NotificationLog
import com.android.systemui.log.dagger.NotificationRenderLog
import com.android.systemui.statusbar.notification.stack.MagneticNotificationRowManagerImpl
import javax.inject.Inject

class NotificationRowLogger
@Inject
constructor(
    @NotificationLog private val buffer: LogBuffer,
    @NotificationRenderLog private val notificationRenderBuffer: LogBuffer,
) {
    fun logKeepInParentChildDetached(child: String, oldParent: String?) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = child
                str2 = oldParent
            },
            { "Detach child $str1 kept in parent $str2" },
        )
    }

    fun logSkipAttachingKeepInParentChild(child: String, newParent: String?) {
        buffer.log(
            TAG,
            LogLevel.WARNING,
            {
                str1 = child
                str2 = newParent
            },
            { "Skipping to attach $str1 to $str2, because it still flagged to keep in parent" },
        )
    }

    fun logRemoveTransientFromContainer(
        childEntry: String,
        containerEntry: String,
    ) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.INFO,
            {
                str1 = childEntry
                str2 = containerEntry
            },
            { "RemoveTransientRow from ChildrenContainer: childKey: $str1 -- containerKey: $str2" },
        )
    }

    fun logRemoveTransientFromNssl(childEntry: String) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.INFO,
            { str1 = childEntry },
            { "RemoveTransientRow from Nssl: childKey: $str1" },
        )
    }

    fun logRemoveTransientFromViewGroup(childEntry: String, containerView: ViewGroup) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.WARNING,
            {
                str1 = childEntry
                str2 = containerView.toString()
            },
            { "RemoveTransientRow from other ViewGroup: childKey: $str1 -- ViewGroup: $str2" },
        )
    }

    fun logAddTransientRow(
        childEntry: String,
        containerEntry: String,
        index: Int,
    ) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.ERROR,
            {
                str1 = childEntry
                str2 = containerEntry
                int1 = index
            },
            { "addTransientRow to row: childKey: $str1 -- containerKey: $str2 -- index: $int1" },
        )
    }

    fun logRemoveTransientRow(childEntry: String, containerEntry: String) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.ERROR,
            {
                str1 = childEntry
                str2 = containerEntry
            },
            { "removeTransientRow from row: childKey: $str1 -- containerKey: $str2" },
        )
    }

    fun logResetAllContentAlphas(entry: String) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.INFO,
            { str1 = entry },
            { "resetAllContentAlphas: $str1" },
        )
    }

    fun logSkipResetAllContentAlphas(entry: String) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.INFO,
            { str1 = entry },
            { "Skip resetAllContentAlphas: $str1" },
        )
    }

    fun logStartAppearAnimation(entry: String, isAppear: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = entry
                bool1 = isAppear
            },
            { "startAppearAnimation childKey: $str1 isAppear:$bool1" },
        )
    }

    fun logCancelAppearDrawing(entry: String, wasDrawing: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.WARNING,
            {
                str1 = entry
                bool1 = wasDrawing
            },
            { "cancelAppearDrawing childKey: $str1 wasDrawing:$bool1" },
        )
    }

    fun logAppearAnimationStarted(entry: String, isAppear: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = entry
                bool1 = isAppear
            },
            { "onAppearAnimationStarted childKey: $str1 isAppear:$bool1" },
        )
    }

    fun logAppearAnimationSkipped(entry: String, isAppear: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.WARNING,
            {
                str1 = entry
                bool1 = isAppear
            },
            { "Skipped an appear animation childKey: $str1 isAppear:$bool1" },
        )
    }

    fun logAppearAnimationFinished(
        entry: String,
        isAppear: Boolean,
        cancelled: Boolean,
    ) {
        notificationRenderBuffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = entry
                bool1 = isAppear
                bool2 = cancelled
            },
            { "onAppearAnimationFinished childKey: $str1 isAppear:$bool1 cancelled:$bool2" },
        )
    }

    fun logMagneticAndRoundableTargetsNotSet(
        state: MagneticNotificationRowManagerImpl.State,
        entry: String,
    ) {
        buffer.log(
            TAG,
            LogLevel.ERROR,
            {
                str1 = entry
                str2 = state.name
            },
            { "Failed to set magnetic and roundable targets for $str1 on state $str2." },
        )
    }

    fun logMagneticRowTranslationNotSet(
        state: MagneticNotificationRowManagerImpl.State,
        entry: String,
    ) {
        buffer.log(
            TAG,
            LogLevel.ERROR,
            {
                str1 = entry
                str2 = state.name
            },
            { "Failed to set magnetic row translation for $str1 on state $str2." },
        )
    }
}

private const val TAG = "NotifRow"
