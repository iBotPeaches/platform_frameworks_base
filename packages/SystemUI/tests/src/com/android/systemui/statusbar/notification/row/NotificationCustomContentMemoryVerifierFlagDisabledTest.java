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

import static com.android.systemui.statusbar.notification.row.NotificationCustomContentNotificationBuilder.buildAcceptableNotificationEntry;

import static com.google.common.truth.Truth.assertThat;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.DisableFlags;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.notification.Flags;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NotificationCustomContentMemoryVerifierFlagDisabledTest extends SysuiTestCase {

    @Rule
    public PlatformCompatChangeRule mCompatChangeRule = new PlatformCompatChangeRule();

    @Test
    @DisableFlags(Flags.FLAG_NOTIFICATION_CUSTOM_VIEW_URI_RESTRICTION)
    @EnableCompatChanges({
            NotificationCustomContentCompat.CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS
    })
    public void requiresImageViewMemorySizeCheck_flagDisabled_returnsFalse() {
        NotificationEntry entry = buildAcceptableNotificationEntry(mContext);
        assertThat(NotificationCustomContentMemoryVerifier.requiresImageViewMemorySizeCheck(entry))
                .isFalse();
    }

}
