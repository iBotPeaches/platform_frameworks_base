/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.hoststubgen.filters

/**
 * How each entry should be handled on the dashboard.
 */
enum class StatsLabel(val statValue: Int, val label: String) {
    /** Entry shouldn't show up in the dashboard. */
    Ignored(-1, ""),

    /** Entry should be shown as "not supported" */
    NotSupported(0, "NotSupported"),

    /**
     * Entry should be shown as "supported", but are too "boring" to show on the dashboard,
     * e.g. annotation classes.
     */
    SupportedButBoring(1, "Boring"),

    /** Entry should be shown as "supported" */
    Supported(2, "Supported");

    val isSupported: Boolean
        get() {
        return when (this) {
            SupportedButBoring, Supported -> true
            else -> false
        }
    }
}

/**
 * Captures a [FilterPolicy] with a human-readable reason.
 */
data class FilterPolicyWithReason (
    val policy: FilterPolicy,
    val reason: String = "",
    val statsLabelOverride: StatsLabel? = null
) {
    /**
     * Return a new [FilterPolicy] with an updated reason, while keeping the original reason
     * as an "inner-reason".
     */
    fun wrapReason(reason: String, statsLabelOverride: StatsLabel? = null): FilterPolicyWithReason {
        return FilterPolicyWithReason(
            policy,
            "$reason [inner-reason: ${this.reason}]",
            statsLabelOverride = statsLabelOverride ?: this.statsLabelOverride,
        )
    }

    override fun toString(): String {
        return "[$policy/$statsLabel - reason: $reason]"
    }

    val statsLabel: StatsLabel get() {
        statsLabelOverride?.let { return it }
        if (policy.isSupported) {
            return StatsLabel.Supported
        }
        return StatsLabel.NotSupported
    }
}
