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
import android.content.theming.ThemeSettingsField;
import android.content.theming.ThemeSettingsUpdater;
import android.content.theming.ThemeStyle;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.function.BiConsumer;
import java.util.function.Function;

@RunWith(JUnit4.class)
public class ThemeSettingsFieldTests {
    static final ThemeSettings DEFAULTS = new ThemeSettings(1, 0xFF123456, 0xFF654321,
            "home_wallpaper", ThemeStyle.VIBRANT, true);
    private ThemeSettingsUpdater mUpdater;

    @Before
    public void setup() {
        mUpdater = ThemeSettings.updater();
    }

    @Test
    public void testFromJSON_validValue_setsValue() throws Exception {
        TestThemeSettingsFieldInteger field = getSampleField();

        JSONObject json = new JSONObject();
        json.put("testKey", "5");

        field.fromJSON(json, mUpdater);

        assertThat(mUpdater.getColorIndex()).isEqualTo(5);
    }

    @Test
    public void testFromJSON_nullValue_setsDefault() throws Exception {
        TestThemeSettingsFieldInteger field = getSampleField();

        JSONObject json = new JSONObject();
        json.put("testKey",
                JSONObject.NULL); // Using JSONObject.NULL is how you should indicate null in JSON

        field.fromJSON(json, mUpdater);

        assertThat(mUpdater.getColorIndex()).isEqualTo(DEFAULTS.colorIndex());
    }

    @Test
    public void testFromJSON_invalidValue_setsDefault() throws Exception {
        TestThemeSettingsFieldInteger field = getSampleField();

        JSONObject json = new JSONObject();
        json.put("testKey", "abc"); // Invalid value

        field.fromJSON(json, mUpdater);

        assertThat(mUpdater.getColorIndex()).isEqualTo(DEFAULTS.colorIndex());
    }

    @Test
    public void testToJSON_validValue_writesValue() throws JSONException {
        TestThemeSettingsFieldInteger field = getSampleField();
        ThemeSettings settings = new ThemeSettings(10, 0xFF123456, 0xFF654321, "home_wallpaper",
                0, true);
        JSONObject json = new JSONObject();

        field.toJSON(settings, json);

        assertThat(json.getString("testKey")).isEqualTo("10");
    }

    @Test
    public void testDefaultValue_returnsGetDefault() {
        TestThemeSettingsFieldInteger field = getSampleField();

        assertThat(field.getDefaultValue()).isEqualTo(DEFAULTS.colorIndex());
    }

    @Test
    public void test_String_validValue_returnsParsedValue() throws JSONException {
        TestThemeSettingsFieldInteger field = getSampleField();

        JSONObject json = new JSONObject();
        json.put("testKey", "123");

        field.fromJSON(json, mUpdater);

        assertThat(mUpdater.getColorIndex()).isEqualTo(123);
    }

    @Test
    public void test_String_invalidValue_returnsDefaultValue() throws JSONException {
        TestThemeSettingsFieldInteger field = getSampleField();

        JSONObject json = new JSONObject();
        // values < 0 are invalid
        json.put("testKey", "-123");
        field.fromJSON(json, mUpdater);

        assertThat(mUpdater.getColorIndex()).isEqualTo(DEFAULTS.colorIndex());
    }

    private TestThemeSettingsFieldInteger getSampleField() {
        return new TestThemeSettingsFieldInteger("testKey", ThemeSettingsUpdater::colorIndex,
                ThemeSettings::colorIndex, DEFAULTS);
    }


    // Helper class for testing
    private static class TestThemeSettingsFieldInteger extends ThemeSettingsField<Integer, String> {
        TestThemeSettingsFieldInteger(String key, BiConsumer<ThemeSettingsUpdater, Integer> setter,
                Function<ThemeSettings, Integer> getter, ThemeSettings defaults) {
            super(key, setter, getter, defaults);
        }

        @Override
        public Integer parse(String primitive) {
            try {
                return Integer.parseInt(primitive);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public String serialize(Integer value) throws RuntimeException {
            return value.toString();
        }

        @Override
        public boolean validate(Integer value) {
            return value > 0;
        }

        @Override
        public Class<Integer> getFieldType() {
            return Integer.class;
        }

        @Override
        public Class<String> getJsonType() {
            return String.class;
        }
    }
}
