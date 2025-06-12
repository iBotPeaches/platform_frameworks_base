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

package com.android.settingslib.widget;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settingslib.widget.SettingsSpinnerPreference.Style;
import com.android.settingslib.widget.spinner.R;

/**
 * An ArrayAdapter which was used by Spinner with settings style.
 * @param <T> the data type to be loaded.
 */
public class SettingsSpinnerAdapter<T> extends ArrayAdapter<T> {

    private static final int DEFAULT_RESOURCE = R.layout.settings_spinner_view;
    private static final int DEFAULT_DROPDOWN_RESOURCE = R.layout.settings_spinner_dropdown_view;
    private static final int DEFAULT_EXPRESSIVE_RESOURCE =
            R.layout.settings_expressvie_spinner_view;
    private static final int DEFAULT_EXPRESSIVE_DROPDOWN_RESOURCE =
            R.layout.settings_expressvie_spinner_dropdown_view;
    private final LayoutInflater mDefaultInflater;
    private int mSelectedPosition = -1;

    /**
     * Constructs a new SettingsSpinnerAdapter with the given context.
     * And it customizes title bar with a settings style.
     *
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     */
    public SettingsSpinnerAdapter(Context context) {
        super(context, getDefaultResource(context, Style.NORMAL));

        setDropDownViewResource(getDropdownResource(context));
        mDefaultInflater = LayoutInflater.from(context);
    }

    @Override
    public View getDropDownView(
            int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view;
        if (convertView == null) {
            view =
                    mDefaultInflater.inflate(
                            getDropdownResource(getContext()), parent, false /* attachToRoot */);
        } else {
            view = convertView;
        }
        TextView textView = view.findViewById(android.R.id.text1);
        ImageView iconView = view.findViewById(android.R.id.icon);
        if (iconView != null) {
            iconView.setVisibility((position == mSelectedPosition) ? View.VISIBLE : View.GONE);
        }
        T item = getItem(position);
        textView.setText(item == null ? "" : item.toString());
        return view;
    }

    public void setSelectedPosition(int pos) {
        mSelectedPosition = pos;
    }

    public SettingsSpinnerAdapter(Context context, SettingsSpinnerPreference.Style style) {
        super(context, getDefaultResource(context, style));

        setDropDownViewResource(getDropdownResource(context));
        mDefaultInflater = LayoutInflater.from(context);
    }

    private static int getDefaultResourceWithStyle(Style style) {
        switch (style) {
            case NORMAL -> {
                return DEFAULT_EXPRESSIVE_RESOURCE;
            }
            case LARGE -> {
                return R.layout.settings_expressive_spinner_view_large;
            }
            case FULL_WIDTH -> {
                return R.layout.settings_expressive_spinner_view_full;
            }
            case OUTLINED -> {
                return R.layout.settings_expressvie_spinner_view_outlined;
            }
            case LARGE_OUTLINED -> {
                return R.layout.settings_expressive_spinner_view_large_outlined;
            }
            case FULL_OUTLINED -> {
                return R.layout.settings_expressive_spinner_view_full_outlined;
            }
            default -> {
                return DEFAULT_RESOURCE;
            }
        }
    }

    /**
     * In overridded {@link #getView(int, View, ViewGroup)}, use this method to get default view.
     */
    public View getDefaultView(int position, View convertView, ViewGroup parent) {
        return mDefaultInflater.inflate(
                getDefaultResource(getContext(), Style.NORMAL), parent, false /* attachToRoot */);
    }

    /**
     * In overridded {@link #getDropDownView(int, View, ViewGroup)}, use this method to get default
     * drop down view.
     */
    public View getDefaultDropDownView(int position, View convertView, ViewGroup parent) {
        return mDefaultInflater.inflate(
                getDropdownResource(getContext()), parent, false /* attachToRoot */);
    }

    private static int getDefaultResource(Context context, Style style) {
        int resId = SettingsThemeHelper.isExpressiveTheme(context)
            ? getDefaultResourceWithStyle(style) : DEFAULT_DROPDOWN_RESOURCE;
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ? resId : android.R.layout.simple_spinner_dropdown_item;
    }

    private static int getDropdownResource(Context context) {
        int resId = SettingsThemeHelper.isExpressiveTheme(context)
            ? DEFAULT_EXPRESSIVE_DROPDOWN_RESOURCE : DEFAULT_DROPDOWN_RESOURCE;
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ? resId : android.R.layout.simple_spinner_dropdown_item;
    }
}
