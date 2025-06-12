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

package com.android.systemui.statusbar.notification.promoted

import android.app.Notification
import android.app.Notification.BigPictureStyle
import android.app.Notification.BigTextStyle
import android.app.Notification.CallStyle
import android.app.Notification.EXTRA_BIG_TEXT
import android.app.Notification.EXTRA_CALL_PERSON
import android.app.Notification.EXTRA_CHRONOMETER_COUNT_DOWN
import android.app.Notification.EXTRA_PROGRESS
import android.app.Notification.EXTRA_PROGRESS_INDETERMINATE
import android.app.Notification.EXTRA_PROGRESS_MAX
import android.app.Notification.EXTRA_SUB_TEXT
import android.app.Notification.EXTRA_TEXT
import android.app.Notification.EXTRA_TITLE
import android.app.Notification.EXTRA_TITLE_BIG
import android.app.Notification.EXTRA_VERIFICATION_ICON
import android.app.Notification.EXTRA_VERIFICATION_TEXT
import android.app.Notification.InboxStyle
import android.app.Notification.ProgressStyle
import android.app.Person
import android.content.Context
import android.graphics.drawable.Icon
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.NotificationLockscreenUserManager.REDACTION_TYPE_NONE
import com.android.systemui.statusbar.NotificationLockscreenUserManager.RedactionType
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.promoted.AutomaticPromotionCoordinator.Companion.EXTRA_AUTOMATICALLY_EXTRACTED_SHORT_CRITICAL_TEXT
import com.android.systemui.statusbar.notification.promoted.AutomaticPromotionCoordinator.Companion.EXTRA_WAS_AUTOMATICALLY_PROMOTED
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Companion.isPromotedForStatusBarChip
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.OldProgress
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.When
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModels
import com.android.systemui.statusbar.notification.row.shared.ImageModel
import com.android.systemui.statusbar.notification.row.shared.ImageModelProvider
import com.android.systemui.statusbar.notification.row.shared.ImageModelProvider.ImageSizeClass.MediumSquare
import com.android.systemui.statusbar.notification.row.shared.ImageModelProvider.ImageSizeClass.SmallSquare
import com.android.systemui.statusbar.notification.row.shared.SkeletonImageTransform
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject

interface PromotedNotificationContentExtractor {
    fun extractContent(
        entry: NotificationEntry,
        recoveredBuilder: Notification.Builder,
        @RedactionType redactionType: Int,
        imageModelProvider: ImageModelProvider,
    ): PromotedNotificationContentModels?
}

@SysUISingleton
class PromotedNotificationContentExtractorImpl
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val skeletonImageTransform: SkeletonImageTransform,
    private val systemClock: SystemClock,
    private val logger: PromotedNotificationLogger,
) : PromotedNotificationContentExtractor {
    override fun extractContent(
        entry: NotificationEntry,
        recoveredBuilder: Notification.Builder,
        @RedactionType redactionType: Int,
        imageModelProvider: ImageModelProvider,
    ): PromotedNotificationContentModels? {
        if (!PromotedNotificationContentModel.featureFlagEnabled()) {
            logger.logExtractionSkipped(entry, "feature flags disabled")
            return null
        }

        val notification = entry.sbn.notification
        if (notification == null) {
            logger.logExtractionFailed(entry, "entry.sbn.notification is null")
            return null
        }

        // The status bar chips rely on this extractor, so take them into account for promotion.
        if (!isPromotedForStatusBarChip(notification)) {
            logger.logExtractionSkipped(entry, "isPromotedOngoing returned false")
            return null
        }

        val privateVersion =
            extractPrivateContent(
                key = entry.key,
                notification = notification,
                recoveredBuilder = recoveredBuilder,
                lastAudiblyAlertedMs = entry.lastAudiblyAlertedMs,
                imageModelProvider = imageModelProvider,
            )
        val publicVersion =
            if (redactionType == REDACTION_TYPE_NONE) {
                privateVersion
            } else {
                notification.publicVersion?.let { publicNotification ->
                    createAppDefinedPublicVersion(
                        privateModel = privateVersion,
                        publicNotification = publicNotification,
                        imageModelProvider = imageModelProvider,
                    )
                } ?: createDefaultPublicVersion(privateModel = privateVersion)
            }
        return PromotedNotificationContentModels(
                privateVersion = privateVersion,
                publicVersion = publicVersion,
            )
            .also { logger.logExtractionSucceeded(entry, it) }
    }

    private fun copyNonSensitiveFields(
        privateModel: PromotedNotificationContentModel,
        publicBuilder: PromotedNotificationContentModel.Builder,
    ) {
        publicBuilder.smallIcon = privateModel.smallIcon
        publicBuilder.iconLevel = privateModel.iconLevel
        publicBuilder.appName = privateModel.appName
        publicBuilder.time = privateModel.time
        publicBuilder.lastAudiblyAlertedMs = privateModel.lastAudiblyAlertedMs
        publicBuilder.profileBadgeResId = privateModel.profileBadgeResId
        publicBuilder.colors = privateModel.colors
    }

    private fun createDefaultPublicVersion(
        privateModel: PromotedNotificationContentModel
    ): PromotedNotificationContentModel =
        PromotedNotificationContentModel.Builder(key = privateModel.identity.key)
            .also {
                it.style =
                    if (privateModel.style == Style.Ineligible) Style.Ineligible else Style.Base
                copyNonSensitiveFields(privateModel, it)
            }
            .build()

    private fun createAppDefinedPublicVersion(
        privateModel: PromotedNotificationContentModel,
        publicNotification: Notification,
        imageModelProvider: ImageModelProvider,
    ): PromotedNotificationContentModel =
        PromotedNotificationContentModel.Builder(key = privateModel.identity.key)
            .also { publicBuilder ->
                val notificationStyle = publicNotification.notificationStyle
                publicBuilder.style =
                    when {
                        privateModel.style == Style.Ineligible -> Style.Ineligible
                        notificationStyle == CallStyle::class.java -> Style.CollapsedCall
                        else -> Style.CollapsedBase
                    }
                copyNonSensitiveFields(privateModel = privateModel, publicBuilder = publicBuilder)
                publicBuilder.shortCriticalText = publicNotification.shortCriticalText()
                publicBuilder.subText = publicNotification.subText()
                // The standard public version is extracted as a collapsed notification,
                //  so avoid using bigTitle or bigText, and instead get the collapsed versions.
                publicBuilder.title = publicNotification.title(notificationStyle, expanded = false)
                publicBuilder.text = publicNotification.text()
                publicBuilder.skeletonLargeIcon =
                    publicNotification.skeletonLargeIcon(imageModelProvider)
                // Only CallStyle has styled content that shows in the collapsed version.
                if (publicBuilder.style == Style.Call) {
                    extractCallStyleContent(publicNotification, publicBuilder, imageModelProvider)
                }
            }
            .build()

    private fun extractPrivateContent(
        key: String,
        notification: Notification,
        recoveredBuilder: Notification.Builder,
        lastAudiblyAlertedMs: Long,
        imageModelProvider: ImageModelProvider,
    ): PromotedNotificationContentModel {

        val contentBuilder = PromotedNotificationContentModel.Builder(key)

        // TODO: Pitch a fit if style is unsupported or mandatory fields are missing once
        // FLAG_PROMOTED_ONGOING is set reliably and we're not testing status bar chips.

        contentBuilder.wasPromotedAutomatically =
            notification.extras.getBoolean(EXTRA_WAS_AUTOMATICALLY_PROMOTED, false)
        contentBuilder.smallIcon = notification.smallIconModel(imageModelProvider)
        contentBuilder.iconLevel = notification.iconLevel
        contentBuilder.appName = notification.loadHeaderAppName(context)
        contentBuilder.subText = notification.subText()
        contentBuilder.time = notification.extractWhen()
        contentBuilder.shortCriticalText = notification.shortCriticalText()
        contentBuilder.lastAudiblyAlertedMs = lastAudiblyAlertedMs
        contentBuilder.profileBadgeResId = null // TODO
        contentBuilder.title = notification.title(recoveredBuilder.style?.javaClass)
        contentBuilder.text = notification.text(recoveredBuilder.style?.javaClass)
        contentBuilder.skeletonLargeIcon = notification.skeletonLargeIcon(imageModelProvider)
        contentBuilder.oldProgress = notification.oldProgress()

        val colorsFromNotif = recoveredBuilder.getColors(/* isHeader= */ false)
        contentBuilder.colors =
            PromotedNotificationContentModel.Colors(
                backgroundColor = colorsFromNotif.backgroundColor,
                primaryTextColor = colorsFromNotif.primaryTextColor,
            )

        recoveredBuilder.extractStyleContent(notification, contentBuilder, imageModelProvider)

        return contentBuilder.build()
    }

    private fun Notification.smallIconModel(imageModelProvider: ImageModelProvider): ImageModel? =
        imageModelProvider.getImageModel(smallIcon, SmallSquare)

    private fun Notification.title(): CharSequence? = getCharSequenceExtraUnlessEmpty(EXTRA_TITLE)

    private fun Notification.bigTitle(): CharSequence? =
        getCharSequenceExtraUnlessEmpty(EXTRA_TITLE_BIG)

    private fun Notification.callPerson(): Person? =
        extras?.getParcelable(EXTRA_CALL_PERSON, Person::class.java)

    private fun Notification.title(
        styleClass: Class<out Notification.Style>?,
        expanded: Boolean = true,
    ): CharSequence? {
        // bigTitle is only used in the expanded form of 3 styles.
        return when (styleClass) {
            BigTextStyle::class.java,
            BigPictureStyle::class.java,
            InboxStyle::class.java -> if (expanded) bigTitle() else null
            CallStyle::class.java -> callPerson()?.name?.takeUnlessEmpty()
            else -> null
        } ?: title()
    }

    private fun Notification.text(): CharSequence? = getCharSequenceExtraUnlessEmpty(EXTRA_TEXT)

    private fun Notification.bigText(): CharSequence? =
        getCharSequenceExtraUnlessEmpty(EXTRA_BIG_TEXT)

    private fun Notification.text(styleClass: Class<out Notification.Style>?): CharSequence? {
        return when (styleClass) {
            BigTextStyle::class.java -> bigText()
            else -> null
        } ?: text()
    }

    private fun Notification.subText(): String? = getStringExtraUnlessEmpty(EXTRA_SUB_TEXT)

    private fun Notification.shortCriticalText(): String? {
        if (!android.app.Flags.apiRichOngoing()) {
            return null
        }
        if (shortCriticalText != null) {
            return shortCriticalText
        }
        if (Flags.promoteNotificationsAutomatically()) {
            return getStringExtraUnlessEmpty(EXTRA_AUTOMATICALLY_EXTRACTED_SHORT_CRITICAL_TEXT)
        }
        return null
    }

    private fun Notification.chronometerCountDown(): Boolean =
        extras?.getBoolean(EXTRA_CHRONOMETER_COUNT_DOWN, /* defaultValue= */ false) ?: false

    private fun Notification.skeletonLargeIcon(
        imageModelProvider: ImageModelProvider
    ): ImageModel? =
        getLargeIcon()?.let {
            imageModelProvider.getImageModel(it, MediumSquare, skeletonImageTransform)
        }

    private fun Notification.oldProgress(): OldProgress? {
        val progress = progress() ?: return null
        val max = progressMax() ?: return null
        val isIndeterminate = progressIndeterminate() ?: return null

        return OldProgress(progress = progress, max = max, isIndeterminate = isIndeterminate)
    }

    private fun Notification.progress(): Int? = extras?.getInt(EXTRA_PROGRESS)

    private fun Notification.progressMax(): Int? = extras?.getInt(EXTRA_PROGRESS_MAX)

    private fun Notification.progressIndeterminate(): Boolean? =
        extras?.getBoolean(EXTRA_PROGRESS_INDETERMINATE)

    private fun Notification.extractWhen(): When? {
        val whenTime = getWhen()

        return when {
            showsChronometer() -> {
                When.Chronometer(
                    elapsedRealtimeMillis =
                        whenTime + systemClock.elapsedRealtime() - systemClock.currentTimeMillis(),
                    isCountDown = chronometerCountDown(),
                )
            }

            showsTime() -> When.Time(currentTimeMillis = whenTime)

            else -> null
        }
    }

    private fun Notification.skeletonVerificationIcon(
        imageModelProvider: ImageModelProvider
    ): ImageModel? =
        extras.getParcelable(EXTRA_VERIFICATION_ICON, Icon::class.java)?.let {
            imageModelProvider.getImageModel(it, SmallSquare, skeletonImageTransform)
        }

    private fun Notification.verificationText(): CharSequence? =
        getCharSequenceExtraUnlessEmpty(EXTRA_VERIFICATION_TEXT)

    private fun Notification.Builder.extractStyleContent(
        notification: Notification,
        contentBuilder: PromotedNotificationContentModel.Builder,
        imageModelProvider: ImageModelProvider,
    ) {
        val style = this.style

        contentBuilder.style =
            when (style) {
                null -> Style.Base

                is BigPictureStyle -> {
                    Style.BigPicture
                }

                is BigTextStyle -> {
                    Style.BigText
                }

                is CallStyle -> {
                    extractCallStyleContent(notification, contentBuilder, imageModelProvider)
                    Style.Call
                }

                is ProgressStyle -> {
                    style.extractContent(contentBuilder)
                    Style.Progress
                }

                else -> Style.Ineligible
            }
    }

    private fun extractCallStyleContent(
        notification: Notification,
        contentBuilder: PromotedNotificationContentModel.Builder,
        imageModelProvider: ImageModelProvider,
    ) {
        contentBuilder.verificationIcon = notification.skeletonVerificationIcon(imageModelProvider)
        contentBuilder.verificationText = notification.verificationText()
    }

    private fun ProgressStyle.extractContent(
        contentBuilder: PromotedNotificationContentModel.Builder
    ) {
        // TODO: Create NotificationProgressModel.toSkeleton, or something similar.
        contentBuilder.newProgress = createProgressModel(0xffffffff.toInt(), 0xff000000.toInt())
    }
}

private fun Notification.getCharSequenceExtraUnlessEmpty(key: String): CharSequence? =
    extras?.getCharSequence(key)?.takeUnlessEmpty()

private fun Notification.getStringExtraUnlessEmpty(key: String): String? =
    extras?.getString(key)?.takeUnlessEmpty()

private fun <T : CharSequence> T.takeUnlessEmpty(): T? = takeUnless { it.isEmpty() }
