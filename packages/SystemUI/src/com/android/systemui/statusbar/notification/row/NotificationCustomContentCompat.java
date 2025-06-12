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

import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledAfter;
import android.os.Build;

/**
 * Holds compat {@link ChangeId} for {@link NotificationCustomContentMemoryVerifier}.
 */
final class NotificationCustomContentCompat {
    /**
     * Enables memory size checking of custom views included in notifications to ensure that
     * they conform to the size limit set in `config_notificationStripRemoteViewSizeBytes`
     * config.xml parameter.
     * Notifications exceeding the size will be rejected.
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion = Build.VERSION_CODES.BAKLAVA)
    public static final long CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS = 270553691L;
}
