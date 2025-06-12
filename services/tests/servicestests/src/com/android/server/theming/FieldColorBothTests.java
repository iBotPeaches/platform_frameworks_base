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

import android.content.theming.FieldColorBoth;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsUpdater;
import android.content.theming.ThemeStyle;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FieldColorBothTests {
    static final ThemeSettings DEFAULTS = new ThemeSettings(1, 0xFF123456, 0xFF654321,
            "home_wallpaper", ThemeStyle.VIBRANT, true);
    private FieldColorBoth mFieldColorBoth;

    @Before
    public void setup() {
        mFieldColorBoth = new FieldColorBoth("colorBoth", ThemeSettingsUpdater::colorBoth,
                ThemeSettings::colorBoth, DEFAULTS);
    }

    @Test
    public void parse_validColorBoth_returnsTrue() {
        Boolean parsedValue = mFieldColorBoth.parse("1");
        assertThat(parsedValue).isTrue();
    }

    @Test
    public void parse_validColorBoth_returnsFalse() {
        Boolean parsedValue = mFieldColorBoth.parse("0");
        assertThat(parsedValue).isFalse();
    }

    @Test
    public void parse_invalidColorBoth_returnsNull() {
        Boolean parsedValue = mFieldColorBoth.parse("invalid");
        assertThat(parsedValue).isNull();
    }

    @Test
    public void serialize_true_returnsTrueString() {
        String serializedValue = mFieldColorBoth.serialize(true);
        assertThat(serializedValue).isEqualTo("1");
    }

    @Test
    public void serialize_false_returnsFalseString() {
        String serializedValue = mFieldColorBoth.serialize(false);
        assertThat(serializedValue).isEqualTo("0");
    }

    @Test
    public void validate_true_returnsTrue() {
        assertThat(mFieldColorBoth.validate(true)).isTrue();
    }

    @Test
    public void validate_false_returnsTrue() {
        assertThat(mFieldColorBoth.validate(false)).isTrue();
    }

    @Test
    public void getFieldType_returnsBooleanClass() {
        Truth.assertThat(mFieldColorBoth.getFieldType()).isEqualTo(Boolean.class);
    }

    @Test
    public void getJsonType_returnsStringClass() {
        Truth.assertThat(mFieldColorBoth.getJsonType()).isEqualTo(String.class);
    }

    @Test
    public void get_returnsDefaultValue() {
        Truth.assertThat(mFieldColorBoth.getDefaultValue()).isEqualTo(DEFAULTS.colorBoth());
    }
}
