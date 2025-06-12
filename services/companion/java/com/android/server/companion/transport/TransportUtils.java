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

package com.android.server.companion.transport;

import static android.companion.AssociationRequest.DEVICE_PROFILE_WEARABLE_SENSING;
import static android.companion.CompanionDeviceManager.TRANSPORT_FLAG_EXTEND_PATCH_DIFF;

import static java.util.Collections.unmodifiableMap;

import android.companion.AssociationInfo;
import android.util.ArrayMap;

import java.util.Map;

/**
 * Utility class for transport manager.
 * @hide
 */
public final class TransportUtils {

    /**
     * Device profile -> Union of allowlisted transport flags
     */
    private static final Map<String, Integer> DEVICE_PROFILE_TRANSPORT_FLAGS_ALLOWLIST;
    static {
        final Map<String, Integer> map = new ArrayMap<>();
        map.put(DEVICE_PROFILE_WEARABLE_SENSING,
                TRANSPORT_FLAG_EXTEND_PATCH_DIFF);
        DEVICE_PROFILE_TRANSPORT_FLAGS_ALLOWLIST = unmodifiableMap(map);
    }

    /**
     * Enforce that the association that is trying to attach a transport with provided flags has
     * one of the allowlisted device profiles that may apply the flagged features.
     *
     * @param association Association for which transport is being attached
     * @param flags Flags for features being applied to the transport
     */
    public static void enforceAssociationCanUseTransportFlags(
            AssociationInfo association, int flags) {
        if (flags == 0) {
            return;
        }

        final String deviceProfile = association.getDeviceProfile();
        if (!DEVICE_PROFILE_TRANSPORT_FLAGS_ALLOWLIST.containsKey(deviceProfile)) {
            throw new IllegalArgumentException("Association (id=" + association.getId()
                    + ") with device profile " + deviceProfile + " does not support the "
                    + "usage of transport flags.");
        }

        int allowedFlags = DEVICE_PROFILE_TRANSPORT_FLAGS_ALLOWLIST.get(deviceProfile);

        // Ensure that every non-zero bits in flags are also present in allowed flags
        if ((allowedFlags & flags) != flags) {
            throw new IllegalArgumentException("Association (id=" + association.getId()
                    + ") does not have the device profile required to use at least "
                    + "one of the flags in this transport.");
        }
    }

    private TransportUtils() {}
}
