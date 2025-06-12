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

import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsUpdater;
import android.content.theming.ThemeStyle;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThemeSettingsUpdaterTests {
    public static final ThemeSettings DEFAULTS = new ThemeSettings(
            /* colorIndex= */ 1,
            /* systemPalette= */ 0xFF123456,
            /* accentColor= */ 0xFF654321,
            /* colorSource= */ "home_wallpaper",
            /* themeStyle= */ ThemeStyle.VIBRANT,
            /* colorBoth= */ true);
    private ThemeSettingsUpdater mUpdater;

    @Before
    public void setUp() {
        mUpdater = ThemeSettings.updater();
    }

    @Test
    public void testSetAndGetColorIndex() {
        mUpdater.colorIndex(5);
        assertThat(mUpdater.getColorIndex()).isEqualTo(5);
    }

    @Test
    public void testSetAndGetSystemPalette() {
        mUpdater.systemPalette(0xFFABCDEF);
        assertThat(mUpdater.getSystemPalette()).isEqualTo(0xFFABCDEF);
    }

    @Test
    public void testSetAndGetAccentColor() {
        mUpdater.accentColor(0xFFFEDCBA);
        assertThat(mUpdater.getAccentColor()).isEqualTo(0xFFFEDCBA);
    }

    @Test
    public void testSetAndGetColorSource() {
        mUpdater.colorSource("lock_wallpaper");
        assertThat(mUpdater.getColorSource()).isEqualTo("lock_wallpaper");
    }

    @Test
    public void testSetAndGetThemeStyle() {
        mUpdater.themeStyle(ThemeStyle.EXPRESSIVE);
        assertThat(mUpdater.getThemeStyle()).isEqualTo(ThemeStyle.EXPRESSIVE);
    }

    @Test
    public void testSetAndGetColorBoth() {
        mUpdater.colorBoth(false);
        assertThat(mUpdater.getColorBoth()).isFalse();
    }


    @Test
    public void testToThemeSettings_allFieldsSet() {
        mUpdater.colorIndex(5)
                .systemPalette(0xFFABCDEF)
                .accentColor(0xFFFEDCBA)
                .colorSource("lock_wallpaper")
                .themeStyle(ThemeStyle.EXPRESSIVE)
                .colorBoth(false);
        ThemeSettings settings = mUpdater.toThemeSettings(DEFAULTS);

        assertThat(settings.colorIndex()).isEqualTo(5);
        assertThat(settings.systemPalette()).isEqualTo(0xFFABCDEF);
        assertThat(settings.accentColor()).isEqualTo(0xFFFEDCBA);
        assertThat(settings.colorSource()).isEqualTo("lock_wallpaper");
        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.EXPRESSIVE);
        assertThat(settings.colorBoth()).isFalse();
    }

    @Test
    public void testToThemeSettings_someFieldsNotSet_usesDefaults() {
        mUpdater.colorIndex(5)
                .systemPalette(0xFFABCDEF);

        ThemeSettings settings = mUpdater.toThemeSettings(DEFAULTS);

        assertThat(settings.colorIndex()).isEqualTo(5);
        assertThat(settings.systemPalette()).isEqualTo(0xFFABCDEF);
        assertThat(settings.accentColor()).isEqualTo(DEFAULTS.accentColor());
        assertThat(settings.colorSource()).isEqualTo(DEFAULTS.colorSource());
        assertThat(settings.themeStyle()).isEqualTo(DEFAULTS.themeStyle());
        assertThat(settings.colorBoth()).isEqualTo(DEFAULTS.colorBoth());
    }

    @Test
    public void testParcel_roundTrip_allFieldsSet() {
        mUpdater.colorIndex(5)
                .systemPalette(0xFFABCDEF)
                .accentColor(0xFFFEDCBA)
                .colorSource("lock_wallpaper")
                .themeStyle(ThemeStyle.EXPRESSIVE)
                .colorBoth(false);

        Parcel parcel = Parcel.obtain();
        mUpdater.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ThemeSettingsUpdater fromParcel = ThemeSettingsUpdater.CREATOR.createFromParcel(parcel);

        assertThat(fromParcel.getColorIndex()).isEqualTo(5);
        assertThat(fromParcel.getSystemPalette()).isEqualTo(0xFFABCDEF);
        assertThat(fromParcel.getAccentColor()).isEqualTo(0xFFFEDCBA);
        assertThat(fromParcel.getColorSource()).isEqualTo("lock_wallpaper");
        assertThat(fromParcel.getThemeStyle()).isEqualTo(ThemeStyle.EXPRESSIVE);
        assertThat(fromParcel.getColorBoth()).isFalse();
    }

    @Test
    public void testParcel_roundTrip_noFieldsSet() {
        Parcel parcel = Parcel.obtain();
        mUpdater.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ThemeSettingsUpdater fromParcel = ThemeSettingsUpdater.CREATOR.createFromParcel(parcel);

        assertThat(fromParcel.getColorIndex()).isNull();
        assertThat(fromParcel.getSystemPalette()).isNull();
        assertThat(fromParcel.getAccentColor()).isNull();
        assertThat(fromParcel.getColorSource()).isNull();
        assertThat(fromParcel.getThemeStyle()).isNull();
        assertThat(fromParcel.getColorBoth()).isNull();
    }
}
