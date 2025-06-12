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

package com.android.systemui.media.dialog;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.media.flags.Flags;
import com.android.settingslib.media.MediaDevice;
import com.android.systemui.res.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** A proxy of holding the list of Output Switcher's output media items. */
public class OutputMediaItemListProxy {
    private final Context mContext;
    private final List<MediaItem> mOutputMediaItemList;

    // Use separated lists to hold different media items and create the list of output media items
    // by using those separated lists and group dividers.
    private final List<MediaItem> mSelectedMediaItems;
    private final List<MediaItem> mSuggestedMediaItems;
    private final List<MediaItem> mSpeakersAndDisplaysMediaItems;

    public OutputMediaItemListProxy(Context context) {
        mContext = context;
        mOutputMediaItemList = new CopyOnWriteArrayList<>();
        mSelectedMediaItems = new CopyOnWriteArrayList<>();
        mSuggestedMediaItems = new CopyOnWriteArrayList<>();
        mSpeakersAndDisplaysMediaItems = new CopyOnWriteArrayList<>();
    }

    /** Returns the list of output media items. */
    public List<MediaItem> getOutputMediaItemList() {
        if (Flags.fixOutputMediaItemListIndexOutOfBoundsException()) {
            if (isEmpty() && !mOutputMediaItemList.isEmpty()) {
                // Ensures mOutputMediaItemList is empty when all individual media item lists are
                // empty, preventing unexpected state issues.
                mOutputMediaItemList.clear();
            } else if (!isEmpty() && mOutputMediaItemList.isEmpty()) {
                // When any individual media item list is modified, the cached mOutputMediaItemList
                // is emptied. On the next request for the output media item list, a fresh list is
                // created and stored in the cache.
                mOutputMediaItemList.addAll(createOutputMediaItemList());
            }
        }
        return mOutputMediaItemList;
    }

    private List<MediaItem> createOutputMediaItemList() {
        List<MediaItem> finalMediaItems = new CopyOnWriteArrayList<>();
        finalMediaItems.addAll(mSelectedMediaItems);
        if (!mSuggestedMediaItems.isEmpty()) {
            finalMediaItems.add(
                    MediaItem.createGroupDividerMediaItem(
                            mContext.getString(
                                    R.string.media_output_group_title_suggested_device)));
            finalMediaItems.addAll(mSuggestedMediaItems);
        }
        if (!mSpeakersAndDisplaysMediaItems.isEmpty()) {
            finalMediaItems.add(
                    MediaItem.createGroupDividerMediaItem(
                            mContext.getString(
                                    R.string.media_output_group_title_speakers_and_displays)));
            finalMediaItems.addAll(mSpeakersAndDisplaysMediaItems);
        }
        return finalMediaItems;
    }

    /** Updates the list of output media items with a given list of media devices. */
    public void updateMediaDevices(
            List<MediaDevice> devices,
            List<MediaDevice> selectedDevices,
            @Nullable MediaDevice connectedMediaDevice,
            boolean needToHandleMutingExpectedDevice) {
        Set<String> selectedOrConnectedMediaDeviceIds =
                selectedDevices.stream().map(MediaDevice::getId).collect(Collectors.toSet());
        if (connectedMediaDevice != null) {
            selectedOrConnectedMediaDeviceIds.add(connectedMediaDevice.getId());
        }

        List<MediaItem> selectedMediaItems = new ArrayList<>();
        List<MediaItem> suggestedMediaItems = new ArrayList<>();
        List<MediaItem> speakersAndDisplaysMediaItems = new ArrayList<>();
        Map<String, MediaItem> deviceIdToMediaItemMap = new HashMap<>();
        buildMediaItems(
                devices,
                selectedOrConnectedMediaDeviceIds,
                needToHandleMutingExpectedDevice,
                selectedMediaItems,
                suggestedMediaItems,
                speakersAndDisplaysMediaItems,
                deviceIdToMediaItemMap);

        List<MediaItem> updatedSelectedMediaItems = new CopyOnWriteArrayList<>();
        List<MediaItem> updatedSuggestedMediaItems = new CopyOnWriteArrayList<>();
        List<MediaItem> updatedSpeakersAndDisplaysMediaItems = new CopyOnWriteArrayList<>();
        if (isEmpty()) {
            updatedSelectedMediaItems.addAll(selectedMediaItems);
            updatedSuggestedMediaItems.addAll(suggestedMediaItems);
            updatedSpeakersAndDisplaysMediaItems.addAll(speakersAndDisplaysMediaItems);
        } else {
            Set<String> updatedDeviceIds = new HashSet<>();
            // Preserve the existing media item order while updating with the latest device
            // information. Some items may retain their original group (suggested, speakers and
            // displays) to maintain this order.
            updateMediaItems(
                    mSelectedMediaItems,
                    updatedSelectedMediaItems,
                    deviceIdToMediaItemMap,
                    updatedDeviceIds);
            updateMediaItems(
                    mSuggestedMediaItems,
                    updatedSuggestedMediaItems,
                    deviceIdToMediaItemMap,
                    updatedDeviceIds);
            updateMediaItems(
                    mSpeakersAndDisplaysMediaItems,
                    updatedSpeakersAndDisplaysMediaItems,
                    deviceIdToMediaItemMap,
                    updatedDeviceIds);

            // Append new media items that are not already in the existing lists to the output list.
            List<MediaItem> remainingMediaItems = new ArrayList<>();
            remainingMediaItems.addAll(
                    getRemainingMediaItems(selectedMediaItems, updatedDeviceIds));
            remainingMediaItems.addAll(
                    getRemainingMediaItems(suggestedMediaItems, updatedDeviceIds));
            remainingMediaItems.addAll(
                    getRemainingMediaItems(speakersAndDisplaysMediaItems, updatedDeviceIds));
            updatedSpeakersAndDisplaysMediaItems.addAll(remainingMediaItems);
        }

        if (Flags.enableOutputSwitcherDeviceGrouping() && !updatedSelectedMediaItems.isEmpty()) {
            MediaItem selectedMediaItem = updatedSelectedMediaItems.get(0);
            Optional<MediaDevice> mediaDeviceOptional = selectedMediaItem.getMediaDevice();
            if (mediaDeviceOptional.isPresent()) {
                MediaItem updatedMediaItem =
                        MediaItem.createDeviceMediaItem(
                                mediaDeviceOptional.get(), /* isFirstDeviceInGroup= */ true);
                updatedSelectedMediaItems.remove(0);
                updatedSelectedMediaItems.add(0, updatedMediaItem);
            }
        }

        mSelectedMediaItems.clear();
        mSelectedMediaItems.addAll(updatedSelectedMediaItems);
        mSuggestedMediaItems.clear();
        mSuggestedMediaItems.addAll(updatedSuggestedMediaItems);
        mSpeakersAndDisplaysMediaItems.clear();
        mSpeakersAndDisplaysMediaItems.addAll(updatedSpeakersAndDisplaysMediaItems);

        // The cached mOutputMediaItemList is cleared upon any update to individual media item
        // lists. This ensures getOutputMediaItemList() computes and caches a fresh list on the next
        // invocation.
        mOutputMediaItemList.clear();
    }

    /** Updates the list of output media items with the given list. */
    public void clearAndAddAll(List<MediaItem> updatedMediaItems) {
        mOutputMediaItemList.clear();
        mOutputMediaItemList.addAll(updatedMediaItems);
    }

    /** Removes the media items with muting expected devices. */
    public void removeMutingExpectedDevices() {
        if (Flags.fixOutputMediaItemListIndexOutOfBoundsException()) {
            mSelectedMediaItems.removeIf((MediaItem::isMutingExpectedDevice));
            mSuggestedMediaItems.removeIf((MediaItem::isMutingExpectedDevice));
            mSpeakersAndDisplaysMediaItems.removeIf((MediaItem::isMutingExpectedDevice));
        }
        mOutputMediaItemList.removeIf((MediaItem::isMutingExpectedDevice));
    }

    /** Clears the output media item list. */
    public void clear() {
        if (Flags.fixOutputMediaItemListIndexOutOfBoundsException()) {
            mSelectedMediaItems.clear();
            mSuggestedMediaItems.clear();
            mSpeakersAndDisplaysMediaItems.clear();
        }
        mOutputMediaItemList.clear();
    }

    /** Returns whether the output media item list is empty. */
    public boolean isEmpty() {
        if (Flags.fixOutputMediaItemListIndexOutOfBoundsException()) {
            return mSelectedMediaItems.isEmpty()
                    && mSuggestedMediaItems.isEmpty()
                    && mSpeakersAndDisplaysMediaItems.isEmpty();
        } else {
            return mOutputMediaItemList.isEmpty();
        }
    }

    private void buildMediaItems(
            List<MediaDevice> devices,
            Set<String> selectedOrConnectedMediaDeviceIds,
            boolean needToHandleMutingExpectedDevice,
            List<MediaItem> selectedMediaItems,
            List<MediaItem> suggestedMediaItems,
            List<MediaItem> speakersAndDisplaysMediaItems,
            Map<String, MediaItem> deviceIdToMediaItemMap) {
        for (MediaDevice device : devices) {
            String deviceId = device.getId();
            MediaItem mediaItem = MediaItem.createDeviceMediaItem(device);
            if (needToHandleMutingExpectedDevice && device.isMutingExpectedDevice()) {
                selectedMediaItems.add(0, mediaItem);
            } else if (!needToHandleMutingExpectedDevice
                    && selectedOrConnectedMediaDeviceIds.contains(device.getId())) {
                if (Flags.enableOutputSwitcherDeviceGrouping()) {
                    selectedMediaItems.add(mediaItem);
                } else {
                    selectedMediaItems.add(0, mediaItem);
                }
            } else if (device.isSuggestedDevice()) {
                suggestedMediaItems.add(mediaItem);
            } else {
                speakersAndDisplaysMediaItems.add(mediaItem);
            }
            deviceIdToMediaItemMap.put(deviceId, mediaItem);
        }
    }

    /** Returns a list of media items that remains the same order as the existing media items. */
    private void updateMediaItems(
            List<MediaItem> existingMediaItems,
            List<MediaItem> updatedMediaItems,
            Map<String, MediaItem> deviceIdToMediaItemMap,
            Set<String> updatedDeviceIds) {
        List<String> existingDeviceIds = getDeviceIds(existingMediaItems);
        for (String deviceId : existingDeviceIds) {
            MediaItem mediaItem = deviceIdToMediaItemMap.get(deviceId);
            if (mediaItem != null) {
                updatedMediaItems.add(mediaItem);
                updatedDeviceIds.add(deviceId);
            }
        }
    }

    /**
     * Returns media items from the input list that are not associated with the given device IDs.
     */
    private List<MediaItem> getRemainingMediaItems(
            List<MediaItem> mediaItems, Set<String> deviceIds) {
        List<MediaItem> remainingMediaItems = new ArrayList<>();
        for (MediaItem item : mediaItems) {
            Optional<MediaDevice> mediaDeviceOptional = item.getMediaDevice();
            if (mediaDeviceOptional.isPresent()) {
                String deviceId = mediaDeviceOptional.get().getId();
                if (!deviceIds.contains(deviceId)) {
                    remainingMediaItems.add(item);
                }
            }
        }
        return remainingMediaItems;
    }

    /** Returns a list of media device IDs for the given list of media items. */
    private List<String> getDeviceIds(List<MediaItem> mediaItems) {
        List<String> deviceIds = new ArrayList<>();
        for (MediaItem item : mediaItems) {
            if (item != null && item.getMediaDevice().isPresent()) {
                deviceIds.add(item.getMediaDevice().get().getId());
            }
        }
        return deviceIds;
    }
}
