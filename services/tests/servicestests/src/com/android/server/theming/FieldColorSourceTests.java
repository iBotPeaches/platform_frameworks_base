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

package com.android.server.theming;

import static com.google.common.truth.Truth.assertThat;

import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsUpdater;
import android.content.theming.ThemeStyle;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class FieldColorSourceTests {
    static final ThemeSettings DEFAULTS = new ThemeSettings(1, 0xFF123456, 0xFF654321,
            "home_wallpaper", ThemeStyle.VIBRANT, true);
    private FieldColorSource mFieldColorSource;

    @Before
    public void setup() {
        mFieldColorSource = new FieldColorSource("colorSource", ThemeSettingsUpdater::colorSource,
                ThemeSettings::colorSource, DEFAULTS);
    }

    @Test
    public void parse_validColorSource_returnsSameString() {
        String validColorSource = "home_wallpaper";
        String parsedValue = mFieldColorSource.parse(validColorSource);
        assertThat(parsedValue).isEqualTo(validColorSource);
    }

    @Test
    public void serialize_validColorSource_returnsSameString() {
        String validColorSource = "lock_wallpaper";
        String serializedValue = mFieldColorSource.serialize(validColorSource);
        assertThat(serializedValue).isEqualTo(validColorSource);
    }

    @Test
    public void validate_preset_returnsTrue() {
        assertThat(mFieldColorSource.validate("preset")).isTrue();
    }

    @Test
    public void validate_homeWallpaper_returnsTrue() {
        assertThat(mFieldColorSource.validate("home_wallpaper")).isTrue();
    }

    @Test
    public void validate_lockWallpaper_returnsTrue() {
        assertThat(mFieldColorSource.validate("lock_wallpaper")).isTrue();
    }

    @Test
    public void validate_invalidColorSource_returnsFalse() {
        assertThat(mFieldColorSource.validate("invalid")).isFalse();
    }

    @Test
    public void getFieldType_returnsStringClass() {
        Truth.assertThat(mFieldColorSource.getFieldType()).isEqualTo(String.class);
    }

    @Test
    public void getJsonType_returnsStringClass() {
        Truth.assertThat(mFieldColorSource.getJsonType()).isEqualTo(String.class);
    }

    @Test
    public void get_returnsDefaultValue() {
        Truth.assertThat(mFieldColorSource.getDefaultValue()).isEqualTo(DEFAULTS.colorSource());
    }
}
