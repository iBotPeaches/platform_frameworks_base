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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.provider.Settings;
import android.testing.TestableContext;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ThemeSettingsManagerTests {
    private final int mUserId = 0;
    public static final ThemeSettings DEFAULTS = new ThemeSettings(
            /* colorIndex= */ 1,
            /* systemPalette= */ 0xFF123456,
            /* accentColor= */ 0xFF654321,
            /* colorSource= */ "home_wallpaper",
            /* themeStyle= */ ThemeStyle.VIBRANT,
            /* colorBoth= */ true);

    @Rule
    public final TestableContext mContext = new TestableContext(
            getInstrumentation().getTargetContext(), null);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private ContentResolver mContentResolver;


    @Before
    public void setup() {
        mContentResolver = mContext.getContentResolver();
    }

    @Test
    public void loadSettings_emptyJSON_returnsDefault() {
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{}", mUserId);

        ThemeSettingsManager manager = new ThemeSettingsManager(DEFAULTS);
        ThemeSettings settings = manager.loadSettings(mUserId, mContentResolver);

        assertThat(settings.colorIndex()).isEqualTo(DEFAULTS.colorIndex());
        assertThat(settings.systemPalette()).isEqualTo(DEFAULTS.systemPalette());
        assertThat(settings.accentColor()).isEqualTo(DEFAULTS.accentColor());
        assertThat(settings.colorSource()).isEqualTo(DEFAULTS.colorSource());
        assertThat(settings.themeStyle()).isEqualTo(DEFAULTS.themeStyle());
        assertThat(settings.colorBoth()).isEqualTo(DEFAULTS.colorBoth());
    }

    @Test
    public void replaceSettings_writesSettingsToProvider() throws Exception {

        ThemeSettingsManager manager = new ThemeSettingsManager(DEFAULTS);

        ThemeSettings newSettings = new ThemeSettings(3, 0xFF112233, 0xFF332211, "preset",
                ThemeStyle.MONOCHROMATIC, false);
        manager.replaceSettings(mUserId, mContentResolver, newSettings);

        String settingsString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);
        JSONObject settingsJson = new JSONObject(settingsString);
        assertThat(settingsJson.getString("android.theme.customization.color_index")).isEqualTo(
                "3");
        assertThat(settingsJson.getString("android.theme.customization.system_palette"))
                .isEqualTo("ff112233");
        assertThat(settingsJson.getString("android.theme.customization.accent_color"))
                .isEqualTo("ff332211");
        assertThat(settingsJson.getString("android.theme.customization.color_source"))
                .isEqualTo("preset");
        assertThat(settingsJson.getString("android.theme.customization.theme_style"))
                .isEqualTo("MONOCHROMATIC");
        assertThat(settingsJson.getString("android.theme.customization.color_both")).isEqualTo("0");
    }

    @Test
    public void updatesSettings_writesSettingsToProvider() throws Exception {
        ThemeSettingsManager manager = new ThemeSettingsManager(DEFAULTS);

        ThemeSettings newSettings = new ThemeSettings(3, 0xFF112233, 0xFF332211, "preset",
                ThemeStyle.MONOCHROMATIC, false);
        manager.updateSettings(mUserId, mContentResolver, newSettings);

        ThemeSettings loadedSettings = manager.loadSettings(mUserId, mContentResolver);
        assertThat(loadedSettings.equals(newSettings)).isTrue();
    }
}
