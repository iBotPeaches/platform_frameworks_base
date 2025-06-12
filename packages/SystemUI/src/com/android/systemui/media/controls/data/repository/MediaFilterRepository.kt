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

package com.android.systemui.media.controls.data.repository

import com.android.internal.logging.InstanceId
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.media.controls.data.model.MediaSortKeyModel
import com.android.systemui.media.controls.shared.model.MediaCommonModel
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDataLoadingModel
import com.android.systemui.util.time.SystemClock
import java.util.TreeMap
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A repository that holds the state of filtered media data on the device. */
@SysUISingleton
class MediaFilterRepository @Inject constructor(private val systemClock: SystemClock) {

    private val _selectedUserEntries: MutableStateFlow<Map<InstanceId, MediaData>> =
        MutableStateFlow(LinkedHashMap())
    val selectedUserEntries: StateFlow<Map<InstanceId, MediaData>> =
        _selectedUserEntries.asStateFlow()

    private val _allUserEntries: MutableStateFlow<Map<String, MediaData>> =
        MutableStateFlow(LinkedHashMap())
    val allUserEntries: StateFlow<Map<String, MediaData>> = _allUserEntries.asStateFlow()

    private val comparator =
        compareByDescending<MediaSortKeyModel> {
                it.isPlaying == true && it.playbackLocation == MediaData.PLAYBACK_LOCAL
            }
            .thenByDescending {
                it.isPlaying == true && it.playbackLocation == MediaData.PLAYBACK_CAST_LOCAL
            }
            .thenByDescending { it.active }
            .thenByDescending { !it.isResume }
            .thenByDescending { it.playbackLocation != MediaData.PLAYBACK_CAST_REMOTE }
            .thenByDescending { it.lastActive }
            .thenByDescending { it.updateTime }
            .thenByDescending { it.notificationKey }

    private val _currentMedia: MutableStateFlow<List<MediaCommonModel>> =
        MutableStateFlow(mutableListOf())
    val currentMedia = _currentMedia.asStateFlow()

    private var sortedMedia = TreeMap<MediaSortKeyModel, MediaCommonModel>(comparator)

    fun addMediaEntry(key: String, data: MediaData) {
        val entries = LinkedHashMap<String, MediaData>(_allUserEntries.value)
        entries[key] = data
        _allUserEntries.value = entries
    }

    /**
     * Removes the media entry corresponding to the given [key].
     *
     * @return media data if an entry is actually removed, `null` otherwise.
     */
    fun removeMediaEntry(key: String): MediaData? {
        val entries = LinkedHashMap<String, MediaData>(_allUserEntries.value)
        val mediaData = entries.remove(key)
        _allUserEntries.value = entries
        return mediaData
    }

    /** @return whether the added media data already exists. */
    fun addSelectedUserMediaEntry(data: MediaData): Boolean {
        val entries = LinkedHashMap<InstanceId, MediaData>(_selectedUserEntries.value)
        val update = _selectedUserEntries.value.containsKey(data.instanceId)
        entries[data.instanceId] = data
        _selectedUserEntries.value = entries
        return update
    }

    /**
     * Removes selected user media entry given the corresponding key.
     *
     * @return media data if an entry is actually removed, `null` otherwise.
     */
    fun removeSelectedUserMediaEntry(key: InstanceId): MediaData? {
        val entries = LinkedHashMap<InstanceId, MediaData>(_selectedUserEntries.value)
        val mediaData = entries.remove(key)
        _selectedUserEntries.value = entries
        return mediaData
    }

    /**
     * Removes selected user media entry given a key and media data.
     *
     * @return true if media data is removed, false otherwise.
     */
    fun removeSelectedUserMediaEntry(key: InstanceId, data: MediaData): Boolean {
        val entries = LinkedHashMap<InstanceId, MediaData>(_selectedUserEntries.value)
        val succeed = entries.remove(key, data)
        if (!succeed) {
            return false
        }
        _selectedUserEntries.value = entries
        return true
    }

    fun clearSelectedUserMedia() {
        _selectedUserEntries.value = LinkedHashMap()
    }

    fun addMediaDataLoadingState(
        mediaDataLoadingModel: MediaDataLoadingModel,
        isUpdate: Boolean = true,
    ) {
        val sortedMap = TreeMap<MediaSortKeyModel, MediaCommonModel>(comparator)
        sortedMap.putAll(
            sortedMedia.filter { (_, commonModel) ->
                commonModel.mediaLoadedModel.instanceId != mediaDataLoadingModel.instanceId
            }
        )

        _selectedUserEntries.value[mediaDataLoadingModel.instanceId]?.let {
            val sortKey =
                MediaSortKeyModel(
                    it.isPlaying,
                    it.playbackLocation,
                    it.active,
                    it.resumption,
                    it.lastActive,
                    it.notificationKey,
                    systemClock.currentTimeMillis(),
                    it.instanceId,
                )

            if (mediaDataLoadingModel is MediaDataLoadingModel.Loaded) {
                val newCommonModel =
                    MediaCommonModel(
                        mediaDataLoadingModel,
                        canBeRemoved(it),
                        if (isUpdate) systemClock.currentTimeMillis() else 0,
                    )
                sortedMap[sortKey] = newCommonModel

                var isNewToCurrentMedia = true
                val currentList =
                    mutableListOf<MediaCommonModel>().apply { addAll(_currentMedia.value) }
                currentList.forEachIndexed { index, mediaCommonModel ->
                    if (
                        mediaCommonModel.mediaLoadedModel.instanceId ==
                            mediaDataLoadingModel.instanceId
                    ) {
                        // When loading an update for an existing media control.
                        isNewToCurrentMedia = false
                        if (mediaCommonModel != newCommonModel) {
                            // Update media model if changed.
                            currentList[index] = newCommonModel
                        }
                    }
                }
                if (isNewToCurrentMedia && it.active) {
                    _currentMedia.value = sortedMap.values.toList()
                } else {
                    _currentMedia.value = currentList
                }

                sortedMedia = sortedMap
            }
        }

        // On removal we want to keep the order being shown to user.
        if (mediaDataLoadingModel is MediaDataLoadingModel.Removed) {
            _currentMedia.value =
                _currentMedia.value.filter { commonModel ->
                    mediaDataLoadingModel.instanceId != commonModel.mediaLoadedModel.instanceId
                }
            sortedMedia = sortedMap
        }
    }

    fun setOrderedMedia() {
        _currentMedia.value = sortedMedia.values.toList()
    }

    fun hasActiveMedia(): Boolean {
        return _selectedUserEntries.value.any { it.value.active }
    }

    fun hasAnyMedia(): Boolean {
        return _selectedUserEntries.value.entries.isNotEmpty()
    }

    private fun canBeRemoved(data: MediaData): Boolean {
        return data.isPlaying?.let { !it } ?: data.isClearable && !data.active
    }
}
