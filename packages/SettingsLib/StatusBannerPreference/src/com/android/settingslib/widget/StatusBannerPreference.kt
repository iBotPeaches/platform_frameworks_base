/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settingslib.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.statusbanner.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

class StatusBannerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {

    enum class BannerStatus {
        GENERIC,
        LOW,
        MEDIUM,
        HIGH,
        OFF,
        LOADING_DETERMINATE, // The loading progress is set by the caller.
        LOADING_INDETERMINATE // No loading progress. Just loading animation
    }
    var iconLevel: BannerStatus = BannerStatus.GENERIC
        set(value) {
            field = value
            updateIconTint(value)
            notifyChanged()
        }
    var buttonLevel: BannerStatus = BannerStatus.GENERIC
        set(value) {
            field = value
            notifyChanged()
        }
    private var buttonText: String = ""
        set(value) {
            field = value
            notifyChanged()
        }
    private var listener: View.OnClickListener? = null

    private var circularProgressIndicator: CircularProgressIndicator? = null

    init {
        layoutResource = R.layout.settingslib_expressive_preference_statusbanner
        isSelectable = false

        initAttributes(context, attrs, defStyleAttr)
    }

    private fun initAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.StatusBanner, defStyleAttr, 0
        ).apply {
            iconLevel = getInteger(R.styleable.StatusBanner_iconLevel, 0).toBannerStatus()
            if (icon == null) {
                icon = getIconDrawable(iconLevel)
            } else {
                updateIconTint(iconLevel)
            }
            buttonLevel = getInteger(R.styleable.StatusBanner_buttonLevel, 0).toBannerStatus()
            buttonText = getString(R.styleable.StatusBanner_buttonText) ?: ""
            recycle()
        }
    }

    private fun Int.toBannerStatus(): BannerStatus = when (this) {
        1 -> BannerStatus.LOW
        2 -> BannerStatus.MEDIUM
        3 -> BannerStatus.HIGH
        4 -> BannerStatus.OFF
        5 -> BannerStatus.LOADING_DETERMINATE
        6 -> BannerStatus.LOADING_INDETERMINATE
        else -> BannerStatus.GENERIC
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedBelow = false
        holder.isDividerAllowedAbove = false

        (holder.findViewById(R.id.icon_background) as? ImageView)?.apply {
            setImageDrawable(getBackgroundDrawable(iconLevel))
        }

        holder.findViewById(android.R.id.icon_frame)?.apply {
            visibility =
                if (
                    icon != null || iconLevel == BannerStatus.LOADING_DETERMINATE ||
                    iconLevel == BannerStatus.LOADING_INDETERMINATE
                )
                    View.VISIBLE
                else View.GONE
        }

        holder.findViewById(android.R.id.icon)?.apply {
            visibility =
                if (iconLevel == BannerStatus.LOADING_DETERMINATE ||
                    iconLevel == BannerStatus.LOADING_INDETERMINATE)
                    View.GONE
                else View.VISIBLE
        }

        circularProgressIndicator = holder.findViewById(R.id.progress_indicator)
                as? CircularProgressIndicator

        (circularProgressIndicator)?.apply {
            visibility =
                if (iconLevel == BannerStatus.LOADING_DETERMINATE)
                    View.VISIBLE
                else View.GONE
        }

        holder.findViewById(R.id.loading_indicator)?.apply {
            visibility =
                if (iconLevel == BannerStatus.LOADING_INDETERMINATE)
                    View.VISIBLE
                else View.GONE
        }

        (holder.findViewById(R.id.status_banner_button) as? MaterialButton)?.apply {
            setBackgroundColor(
                if (buttonLevel == BannerStatus.OFF) getBackgroundColor(BannerStatus.GENERIC)
                else getBackgroundColor(buttonLevel)
            )
            text = buttonText
            setOnClickListener(listener)
            visibility = if (listener != null) View.VISIBLE else View.GONE
        }
    }

    fun getProgressIndicator(): CircularProgressIndicator? {
        return circularProgressIndicator
    }

    /**
     * Sets the text to be displayed in button.
     */
    fun setButtonText(@StringRes textResId: Int) {
        buttonText = context.getString(textResId)
    }

    /**
     * Register a callback to be invoked when positive button is clicked.
     */
    fun setButtonOnClickListener(listener: View.OnClickListener) {
        this.listener = listener
        notifyChanged()
    }

    private fun getBackgroundColor(level: BannerStatus): Int {
        return when (level) {
            BannerStatus.LOW -> ContextCompat.getColor(
                context,
                R.color.settingslib_expressive_color_status_level_low
            )

            BannerStatus.MEDIUM -> ContextCompat.getColor(
                context,
                R.color.settingslib_expressive_color_status_level_medium
            )

            BannerStatus.HIGH -> ContextCompat.getColor(
                context,
                R.color.settingslib_expressive_color_status_level_high
            )

            BannerStatus.OFF -> ContextCompat.getColor(
                context,
                R.color.settingslib_expressive_color_status_level_off
            )

            else -> ContextCompat.getColor(
                context,
                com.android.settingslib.widget.theme.R.color.settingslib_materialColorPrimary
            )
        }
    }

    private fun getIconDrawable(level: BannerStatus): Drawable? {
        return when (level) {
            BannerStatus.LOW -> ContextCompat.getDrawable(
                context,
                R.drawable.settingslib_expressive_icon_status_level_low
            )

            BannerStatus.MEDIUM -> ContextCompat.getDrawable(
                context,
                R.drawable.settingslib_expressive_icon_status_level_medium
            )

            BannerStatus.HIGH -> ContextCompat.getDrawable(
                context,
                R.drawable.settingslib_expressive_icon_status_level_high
            )

            BannerStatus.OFF -> ContextCompat.getDrawable(
                context,
                R.drawable.settingslib_expressive_icon_status_level_off
            )

            else -> null
        }
    }

    private fun getBackgroundDrawable(level: BannerStatus): Drawable? {
        return when (level) {
            BannerStatus.LOW -> ContextCompat.getDrawable(
                context,
                R.drawable.settingslib_expressive_background_level_low
            )

            BannerStatus.MEDIUM -> ContextCompat.getDrawable(
                context,
                R.drawable.settingslib_expressive_background_level_medium
            )

            BannerStatus.HIGH -> ContextCompat.getDrawable(
                context,
                R.drawable.settingslib_expressive_background_level_high
            )

            // Using the same background drawable for other levels.
            else -> ContextCompat.getDrawable(
                context,
                R.drawable.settingslib_expressive_background_generic
            )
        }
    }

    /**
     * Sets the icon's tint color based on the icon level. If an icon is not defined, this is a
     * no-op.
     */
    private fun updateIconTint(iconLevel: BannerStatus) {
        icon?.setTintList(ColorStateList.valueOf(getBackgroundColor(iconLevel)))
    }
}
