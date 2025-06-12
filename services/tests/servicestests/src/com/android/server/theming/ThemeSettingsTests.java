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

import static org.junit.Assert.assertNull;

import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsUpdater;
import android.content.theming.ThemeStyle;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThemeSettingsTests {
    public static final ThemeSettings DEFAULTS = new ThemeSettings(
            /* colorIndex= */ 1,
            /* systemPalette= */ 0xFF123456,
            /* accentColor= */ 0xFF654321,
            /* colorSource= */ "home_wallpaper",
            /* themeStyle= */ ThemeStyle.VIBRANT,
            /* colorBoth= */ true);

    /**
     * Test that the updater correctly sets all fields when they are provided.
     */
    @Test
    public void testUpdater_allFieldsSet() {
        ThemeSettingsUpdater updater = ThemeSettings.updater()
                .colorIndex(2)
                .systemPalette(0xFFFF0000)
                .accentColor(0xFF00FF00)
                .colorSource("preset")
                .themeStyle(ThemeStyle.MONOCHROMATIC)
                .colorBoth(false);

        ThemeSettings settings = updater.toThemeSettings(DEFAULTS);

        assertThat(settings.colorIndex()).isEqualTo(2);
        assertThat(settings.systemPalette()).isEqualTo(0xFFFF0000);
        assertThat(settings.accentColor()).isEqualTo(0xFF00FF00);
        assertThat(settings.colorSource()).isEqualTo("preset");
        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.MONOCHROMATIC);
        assertThat(settings.colorBoth()).isEqualTo(false);
    }

    /**
     * Test that the updater uses null values when no fields are explicitly set.
     */
    @Test
    public void testUpdater_noFieldsSet() {
        ThemeSettingsUpdater updater = ThemeSettings.updater();

        assertNull(updater.getColorIndex());
        assertNull(updater.getSystemPalette());
        assertNull(updater.getAccentColor());
        assertNull(updater.getColorSource());
        assertNull(updater.getThemeStyle());
        assertNull(updater.getColorBoth());
    }

    /**
     * Test that the ThemeSettings object can be correctly parceled and restored.
     */
    @Test
    public void testParcel_roundTrip() {
        ThemeSettingsUpdater updater = ThemeSettings.updater()
                .colorIndex(2)
                .systemPalette(0xFFFF0000)
                .accentColor(0xFF00FF00)
                .colorSource("preset")
                .themeStyle(ThemeStyle.MONOCHROMATIC)
                .colorBoth(false);

        ThemeSettings settings = updater.toThemeSettings(DEFAULTS);

        Parcel parcel = Parcel.obtain();
        settings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ThemeSettings fromParcel = ThemeSettings.CREATOR.createFromParcel(parcel);

        assertThat(settings.colorIndex()).isEqualTo(fromParcel.colorIndex());
        assertThat(settings.systemPalette()).isEqualTo(fromParcel.systemPalette());
        assertThat(settings.accentColor()).isEqualTo(fromParcel.accentColor());
        assertThat(settings.colorSource()).isEqualTo(fromParcel.colorSource());
        assertThat(settings.themeStyle()).isEqualTo(fromParcel.themeStyle());
        assertThat(settings.colorBoth()).isEqualTo(fromParcel.colorBoth());
    }
}
