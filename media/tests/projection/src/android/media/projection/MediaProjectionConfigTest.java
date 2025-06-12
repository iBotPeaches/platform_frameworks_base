/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.media.projection;

import static android.media.projection.MediaProjectionConfig.CAPTURE_REGION_FIXED_DISPLAY;
import static android.media.projection.MediaProjectionConfig.CAPTURE_REGION_USER_CHOICE;
import static android.media.projection.MediaProjectionConfig.PROJECTION_SOURCE_DISPLAY;
import static android.media.projection.MediaProjectionConfig.DEFAULT_PROJECTION_SOURCES;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.media.projection.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link MediaProjectionConfig} class.
 * <p>
 * Build/Install/Run:
 * atest MediaProjectionTests:MediaProjectionConfigTest
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class MediaProjectionConfigTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final MediaProjectionConfig DISPLAY_CONFIG =
            MediaProjectionConfig.createConfigForDefaultDisplay();
    private static final MediaProjectionConfig USERS_CHOICE_CONFIG =
            MediaProjectionConfig.createConfigForUserChoice();

    @Test
    public void testParcelable() {
        Parcel parcel = Parcel.obtain();
        DISPLAY_CONFIG.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        MediaProjectionConfig config = MediaProjectionConfig.CREATOR.createFromParcel(parcel);
        assertThat(DISPLAY_CONFIG).isEqualTo(config);
        parcel.recycle();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_CONTENT_SHARING)
    public void testCreateDisplayConfig() {
        assertThat(DISPLAY_CONFIG.getRegionToCapture()).isEqualTo(CAPTURE_REGION_FIXED_DISPLAY);
        assertThat(DISPLAY_CONFIG.getDisplayToCapture()).isEqualTo(DEFAULT_DISPLAY);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_CONTENT_SHARING)
    public void testCreateUsersChoiceConfig() {
        assertThat(USERS_CHOICE_CONFIG.getRegionToCapture()).isEqualTo(CAPTURE_REGION_USER_CHOICE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_CONTENT_SHARING)
    public void testDefaultProjectionSources() {
        assertThat(USERS_CHOICE_CONFIG.getProjectionSources())
                .isEqualTo(DEFAULT_PROJECTION_SOURCES);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_CONTENT_SHARING)
    public void testCreateDisplayConfigProjectionSource() {
        assertThat(DISPLAY_CONFIG.getProjectionSources()).isEqualTo(PROJECTION_SOURCE_DISPLAY);
        assertThat(DISPLAY_CONFIG.getDisplayToCapture()).isEqualTo(DEFAULT_DISPLAY);
    }

    @Test
    public void testEquals() {
        assertThat(MediaProjectionConfig.createConfigForUserChoice()).isEqualTo(
                USERS_CHOICE_CONFIG);
        assertThat(DISPLAY_CONFIG).isNotEqualTo(USERS_CHOICE_CONFIG);
        assertThat(MediaProjectionConfig.createConfigForDefaultDisplay()).isEqualTo(
                DISPLAY_CONFIG);
    }
}
