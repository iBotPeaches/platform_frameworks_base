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
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.intro.R

class IntroPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {

    private var isCollapsable: Boolean = true
    private var minLines: Int = DEFAULT_MIN_LINES
    private var hyperlinkListener: View.OnClickListener? = null
    private var learnMoreListener: View.OnClickListener? = null
    private var learnMoreText: CharSequence? = null

    init {
        layoutResource = R.layout.settingslib_expressive_preference_intro
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedBelow = false
        holder.isDividerAllowedAbove = false

        (holder.findViewById(R.id.collapsable_summary) as? CollapsableTextView)?.apply {
            setCollapsable(isCollapsable)
            setMinLines(minLines)
            visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
            setText(summary.toString())
            if (hyperlinkListener != null) {
                setHyperlinkListener(hyperlinkListener)
            }
            if (learnMoreListener != null) {
                setLearnMoreText(learnMoreText)
                setLearnMoreAction(learnMoreListener)
            }
        }
    }

    /**
     * Sets whether the summary is collapsable.
     *
     * @param collapsable True if the summary should be collapsable, false otherwise.
     */
    fun setCollapsable(collapsable: Boolean) {
        isCollapsable = collapsable
        minLines = if (isCollapsable) DEFAULT_MIN_LINES else DEFAULT_MAX_LINES
        notifyChanged()
    }

    /**
     * Sets the minimum number of lines to display when collapsed.
     *
     * @param lines The minimum number of lines.
     */
    fun setMinLines(lines: Int) {
        minLines = lines.coerceIn(1, DEFAULT_MAX_LINES)
        notifyChanged()
    }

    /**
     * Sets the action when clicking on the hyperlink in the text.
     *
     * @param listener The click listener for hyperlink.
     */
    fun setHyperlinkListener(listener: View.OnClickListener) {
        if (hyperlinkListener != listener) {
            hyperlinkListener = listener
            notifyChanged()
        }
    }

    /**
     * Sets the action when clicking on the learn more view.
     *
     * @param listener The click listener for learn more.
     */
    fun setLearnMoreAction(listener: View.OnClickListener) {
        if (learnMoreListener != listener) {
            learnMoreListener = listener
            notifyChanged()
        }
    }

    /**
     * Sets the text of learn more view.
     *
     * @param text The text of learn more.
     */
    fun setLearnMoreText(text: CharSequence) {
        if (!TextUtils.equals(learnMoreText, text)) {
            learnMoreText = text
            notifyChanged()
        }
    }

    companion object {
        private const val DEFAULT_MAX_LINES = 10
        private const val DEFAULT_MIN_LINES = 1
    }
}
