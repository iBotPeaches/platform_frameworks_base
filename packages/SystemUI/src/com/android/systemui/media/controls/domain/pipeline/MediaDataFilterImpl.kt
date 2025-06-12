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

package com.android.systemui.media.controls.domain.pipeline

import android.content.Context
import android.content.pm.UserInfo
import android.util.Log
import com.android.internal.annotations.KeepForWeakReference
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.logging.InstanceId
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.media.controls.data.repository.MediaFilterRepository
import com.android.systemui.media.controls.shared.MediaLogger
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDataLoadingModel
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.util.time.SystemClock
import java.util.SortedMap
import java.util.concurrent.Executor
import javax.inject.Inject

private const val TAG = "MediaDataFilter"
private const val DEBUG = true

/**
 * Filters data updates from [MediaDataCombineLatest] based on the current user ID, and handles user
 * switches (removing entries for the previous user, adding back entries for the current user).
 *
 * This is added at the end of the pipeline since we may still need to handle callbacks from
 * background users (e.g. timeouts).
 */
@SysUISingleton
class MediaDataFilterImpl
@Inject
constructor(
    userTracker: UserTracker,
    private val lockscreenUserManager: NotificationLockscreenUserManager,
    @Main private val executor: Executor,
    private val systemClock: SystemClock,
    private val mediaFilterRepository: MediaFilterRepository,
    private val mediaLogger: MediaLogger,
) : MediaDataManager.Listener {
    /** Non-UI listeners to media changes. */
    private val _listeners: MutableSet<MediaDataProcessor.Listener> = mutableSetOf()
    val listeners: Set<MediaDataProcessor.Listener>
        get() = _listeners.toSet()

    lateinit var mediaDataProcessor: MediaDataProcessor

    // Ensure the field (and associated reference) isn't removed during optimization.
    @KeepForWeakReference
    private val userTrackerCallback =
        object : UserTracker.Callback {
            override fun onUserChanged(newUser: Int, userContext: Context) {
                handleUserSwitched()
            }

            override fun onProfilesChanged(profiles: List<UserInfo>) {
                handleProfileChanged()
            }
        }

    init {
        userTracker.addCallback(userTrackerCallback, executor)
    }

    override fun onMediaDataLoaded(
        key: String,
        oldKey: String?,
        data: MediaData,
        immediately: Boolean,
        receivedSmartspaceCardLatency: Int,
        isSsReactivated: Boolean,
    ) {
        if (oldKey != null && oldKey != key) {
            mediaFilterRepository.removeMediaEntry(oldKey)
        }
        mediaFilterRepository.addMediaEntry(key, data)

        if (
            !lockscreenUserManager.isCurrentProfile(data.userId) ||
                !lockscreenUserManager.isProfileAvailable(data.userId)
        ) {
            return
        }

        val isUpdate = mediaFilterRepository.addSelectedUserMediaEntry(data)

        mediaLogger.logMediaLoaded(data.instanceId, data.active, "loading media")
        mediaFilterRepository.addMediaDataLoadingState(
            MediaDataLoadingModel.Loaded(data.instanceId),
            isUpdate,
        )

        // Notify listeners
        listeners.forEach { it.onMediaDataLoaded(key, oldKey, data) }
    }

    override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
        mediaFilterRepository.removeMediaEntry(key)?.let { mediaData ->
            val instanceId = mediaData.instanceId
            mediaFilterRepository.removeSelectedUserMediaEntry(instanceId)?.let {
                mediaFilterRepository.addMediaDataLoadingState(
                    MediaDataLoadingModel.Removed(instanceId)
                )
                mediaLogger.logMediaRemoved(instanceId, "removing media card")
                // Only notify listeners if something actually changed
                listeners.forEach { it.onMediaDataRemoved(key, userInitiated) }
            }
        }
    }

    @VisibleForTesting
    internal fun handleProfileChanged() {
        // TODO(b/317221348) re-add media removed when profile is available.
        mediaFilterRepository.allUserEntries.value.forEach { (key, data) ->
            if (!lockscreenUserManager.isProfileAvailable(data.userId)) {
                // Only remove media when the profile is unavailable.
                mediaFilterRepository.removeSelectedUserMediaEntry(data.instanceId, data)
                mediaFilterRepository.addMediaDataLoadingState(
                    MediaDataLoadingModel.Removed(data.instanceId)
                )
                mediaLogger.logMediaRemoved(data.instanceId, "Removing $key after profile change")
                listeners.forEach { listener -> listener.onMediaDataRemoved(key, false) }
            }
        }
    }

    @VisibleForTesting
    internal fun handleUserSwitched() {
        // If the user changes, remove all current MediaData objects.
        val listenersCopy = listeners
        val keyCopy = mediaFilterRepository.selectedUserEntries.value.keys.toMutableList()
        // Clear the list first and update loading state to remove media from UI.
        mediaFilterRepository.clearSelectedUserMedia()
        keyCopy.forEach { instanceId ->
            mediaFilterRepository.addMediaDataLoadingState(
                MediaDataLoadingModel.Removed(instanceId)
            )
            mediaLogger.logMediaRemoved(instanceId, "Removing media after user change")
            getKey(instanceId)?.let {
                listenersCopy.forEach { listener -> listener.onMediaDataRemoved(it, false) }
            }
        }

        mediaFilterRepository.allUserEntries.value.forEach { (key, data) ->
            if (lockscreenUserManager.isCurrentProfile(data.userId)) {
                val isUpdate = mediaFilterRepository.addSelectedUserMediaEntry(data)
                mediaFilterRepository.addMediaDataLoadingState(
                    MediaDataLoadingModel.Loaded(data.instanceId),
                    isUpdate,
                )
                mediaLogger.logMediaLoaded(
                    data.instanceId,
                    data.active,
                    "Re-adding $key after user change",
                )
                listenersCopy.forEach { listener -> listener.onMediaDataLoaded(key, null, data) }
            }
        }
    }

    /** Invoked when the user has dismissed the media carousel */
    fun onSwipeToDismiss() {
        if (DEBUG) Log.d(TAG, "Media carousel swiped away")
        val mediaEntries = mediaFilterRepository.allUserEntries.value.entries
        mediaEntries.forEach { (key, data) ->
            if (mediaFilterRepository.selectedUserEntries.value.containsKey(data.instanceId)) {
                // Force updates to listeners, needed for re-activated card
                mediaDataProcessor.setInactive(key, timedOut = true, forceUpdate = true)
            }
        }
    }

    /** Add a listener for filtered [MediaData] changes */
    fun addListener(listener: MediaDataProcessor.Listener) = _listeners.add(listener)

    /** Remove a listener that was registered with addListener */
    fun removeListener(listener: MediaDataProcessor.Listener) = _listeners.remove(listener)

    /**
     * Return the time since last active for the most-recent media.
     *
     * @param sortedEntries selectedUserEntries sorted from the earliest to the most-recent.
     * @return The duration in milliseconds from the most-recent media's last active timestamp to
     *   the present. MAX_VALUE will be returned if there is no media.
     */
    private fun timeSinceActiveForMostRecentMedia(
        sortedEntries: SortedMap<InstanceId, MediaData>
    ): Long {
        if (sortedEntries.isEmpty()) {
            return Long.MAX_VALUE
        }

        val now = systemClock.elapsedRealtime()
        val lastActiveInstanceId = sortedEntries.lastKey() // most recently active
        return sortedEntries[lastActiveInstanceId]?.let { now - it.lastActive } ?: Long.MAX_VALUE
    }

    private fun getKey(instanceId: InstanceId): String? {
        val allEntries = mediaFilterRepository.allUserEntries.value
        val filteredEntries = allEntries.filter { (_, data) -> data.instanceId == instanceId }
        return if (filteredEntries.isNotEmpty()) {
            filteredEntries.keys.first()
        } else {
            null
        }
    }
}
