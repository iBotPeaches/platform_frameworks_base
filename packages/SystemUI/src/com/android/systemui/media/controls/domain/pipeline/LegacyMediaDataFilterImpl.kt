/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.SmartspaceMediaData
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.util.time.SystemClock
import java.util.SortedMap
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.collections.LinkedHashMap

private const val TAG = "MediaDataFilter"
private const val DEBUG = true

/**
 * Filters data updates from [MediaDataCombineLatest] based on the current user ID, and handles user
 * switches (removing entries for the previous user, adding back entries for the current user). Also
 * filters out smartspace updates in favor of local recent media, when avaialble.
 *
 * This is added at the end of the pipeline since we may still need to handle callbacks from
 * background users (e.g. timeouts).
 */
class LegacyMediaDataFilterImpl
@Inject
constructor(
    private val userTracker: UserTracker,
    private val lockscreenUserManager: NotificationLockscreenUserManager,
    @Main private val executor: Executor,
    private val systemClock: SystemClock,
) : MediaDataManager.Listener {
    private val _listeners: MutableSet<MediaDataManager.Listener> = mutableSetOf()
    val listeners: Set<MediaDataManager.Listener>
        get() = _listeners.toSet()

    lateinit var mediaDataManager: MediaDataManager

    private val allEntries: LinkedHashMap<String, MediaData> = LinkedHashMap()
    // The filtered userEntries, which will be a subset of all userEntries in MediaDataManager
    private val userEntries: LinkedHashMap<String, MediaData> = LinkedHashMap()

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
            allEntries.remove(oldKey)
        }
        allEntries.put(key, data)

        if (
            !lockscreenUserManager.isCurrentProfile(data.userId) ||
                !lockscreenUserManager.isProfileAvailable(data.userId)
        ) {
            return
        }

        if (oldKey != null && oldKey != key) {
            userEntries.remove(oldKey)
        }
        userEntries.put(key, data)

        // Notify listeners
        listeners.forEach { it.onMediaDataLoaded(key, oldKey, data) }
    }

    override fun onSmartspaceMediaDataLoaded(
        key: String,
        data: SmartspaceMediaData,
        shouldPrioritize: Boolean,
    ) {
        // TODO(b/382680767): remove
    }

    override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
        allEntries.remove(key)
        userEntries.remove(key)?.let {
            // Only notify listeners if something actually changed
            listeners.forEach { it.onMediaDataRemoved(key, userInitiated) }
        }
    }

    override fun onSmartspaceMediaDataRemoved(key: String, immediately: Boolean) {
        // TODO(b/382680767): remove
    }

    @VisibleForTesting
    internal fun handleProfileChanged() {
        // TODO(b/317221348) re-add media removed when profile is available.
        allEntries.forEach { (key, data) ->
            if (!lockscreenUserManager.isProfileAvailable(data.userId)) {
                // Only remove media when the profile is unavailable.
                if (DEBUG) Log.d(TAG, "Removing $key after profile change")
                userEntries.remove(key, data)
                listeners.forEach { listener -> listener.onMediaDataRemoved(key, false) }
            }
        }
    }

    @VisibleForTesting
    internal fun handleUserSwitched() {
        // If the user changes, remove all current MediaData objects and inform listeners
        val listenersCopy = listeners
        val keyCopy = userEntries.keys.toMutableList()
        // Clear the list first, to make sure callbacks from listeners if we have any entries
        // are up to date
        userEntries.clear()
        keyCopy.forEach {
            if (DEBUG) Log.d(TAG, "Removing $it after user change")
            listenersCopy.forEach { listener -> listener.onMediaDataRemoved(it, false) }
        }

        allEntries.forEach { (key, data) ->
            if (lockscreenUserManager.isCurrentProfile(data.userId)) {
                if (DEBUG) Log.d(TAG, "Re-adding $key after user change")
                userEntries.put(key, data)
                listenersCopy.forEach { listener -> listener.onMediaDataLoaded(key, null, data) }
            }
        }
    }

    /** Invoked when the user has dismissed the media carousel */
    fun onSwipeToDismiss() {
        if (DEBUG) Log.d(TAG, "Media carousel swiped away")
        val mediaKeys = userEntries.keys.toSet()
        mediaKeys.forEach {
            // Force updates to listeners, needed for re-activated card
            mediaDataManager.setInactive(it, timedOut = true, forceUpdate = true)
        }
    }

    /** Are there any media notifications active? */
    fun hasActiveMedia() = userEntries.any { it.value.active }

    /** Are there any media entries we should display? */
    fun hasAnyMedia() = userEntries.isNotEmpty()

    /** Add a listener for filtered [MediaData] changes */
    fun addListener(listener: MediaDataManager.Listener) = _listeners.add(listener)

    /** Remove a listener that was registered with addListener */
    fun removeListener(listener: MediaDataManager.Listener) = _listeners.remove(listener)

    /**
     * Return the time since last active for the most-recent media.
     *
     * @param sortedEntries userEntries sorted from the earliest to the most-recent.
     * @return The duration in milliseconds from the most-recent media's last active timestamp to
     *   the present. MAX_VALUE will be returned if there is no media.
     */
    private fun timeSinceActiveForMostRecentMedia(
        sortedEntries: SortedMap<String, MediaData>
    ): Long {
        if (sortedEntries.isEmpty()) {
            return Long.MAX_VALUE
        }

        val now = systemClock.elapsedRealtime()
        val lastActiveKey = sortedEntries.lastKey() // most recently active
        return sortedEntries.get(lastActiveKey)?.let { now - it.lastActive } ?: Long.MAX_VALUE
    }
}
