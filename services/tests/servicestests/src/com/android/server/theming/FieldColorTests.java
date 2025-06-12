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

import android.content.theming.FieldColor;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsUpdater;
import android.content.theming.ThemeStyle;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FieldColorTests {
    static final ThemeSettings DEFAULTS = new ThemeSettings(1, 0xFF123456, 0xFF654321,
            "home_wallpaper", ThemeStyle.VIBRANT, true);

    private FieldColor mFieldColor;

    @Before
    public void setup() {
        // Default to blue
        mFieldColor = new FieldColor("accentColor", ThemeSettingsUpdater::accentColor,
                ThemeSettings::accentColor, DEFAULTS);
    }

    @Test
    public void parse_validColor_returnsCorrectColor() {
        Integer parsedValue = mFieldColor.parse("FF0000FF");
        assertThat(parsedValue).isEqualTo(0xFF0000FF);
    }    @Test
    public void parse_validColorLowercase_returnsCorrectColor() {
        Integer parsedValue = mFieldColor.parse("ff0000ff");
        assertThat(parsedValue).isEqualTo(0xFF0000FF);
    }

    @Test
    public void parse_validColorNoAlpha_returnsCorrectColor() {
        Integer parsedValue = mFieldColor.parse("0000ff");
        assertThat(parsedValue).isEqualTo(0xFF0000FF);
    }


    @Test
    public void parse_invalidColor_returnsNull() {
        Integer parsedValue = mFieldColor.parse("invalid");
        assertThat(parsedValue).isNull();
    }

    @Test
    public void parse_nullColor_returnsNull() {
        Integer parsedValue = mFieldColor.parse(null);
        assertThat(parsedValue).isNull();
    }

    @Test
    public void serialize_validColor_returnsCorrectString() {
        String serializedValue = mFieldColor.serialize(0xFFFF0000); // Red
        assertThat(serializedValue).isEqualTo("ffff0000");
    }

    @Test
    public void serialize_zeroColor_returnsZeroString() {
        String serializedValue = mFieldColor.serialize(0);
        assertThat(serializedValue).isEqualTo("0");
    }

    @Test
    public void validate_validColor_returnsTrue() {
        assertThat(mFieldColor.validate(0xFF00FF00)).isTrue(); // Green
    }

    @Test
    public void getFieldType_returnsIntegerClass() {
        Truth.assertThat(mFieldColor.getFieldType()).isEqualTo(Integer.class);
    }

    @Test
    public void getJsonType_returnsStringClass() {
        Truth.assertThat(mFieldColor.getJsonType()).isEqualTo(String.class);
    }

    @Test
    public void get_returnsDefaultValue() {
        Truth.assertThat(mFieldColor.getDefaultValue()).isEqualTo(DEFAULTS.accentColor());
    }
}
