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

package com.android.systemui.communal.data.model

sealed interface SuppressionReason {
    @CommunalFeature val suppressedFeatures: Int

    /** Whether this reason suppresses a particular feature. */
    fun isSuppressed(@CommunalFeature feature: Int): Boolean {
        return (suppressedFeatures and feature) != 0
    }

    /** Suppress hub automatically opening due to Android Auto projection */
    data object ReasonCarProjection : SuppressionReason {
        override val suppressedFeatures: Int = FEATURE_AUTO_OPEN
    }

    /** Suppress hub due to the "When to dream" conditions not being met */
    data class ReasonWhenToAutoShow(override val suppressedFeatures: Int) : SuppressionReason

    /** Suppress hub due to device policy */
    data object ReasonDevicePolicy : SuppressionReason {
        override val suppressedFeatures: Int = FEATURE_ALL
    }

    /** Suppress hub due to the user disabling the setting */
    data object ReasonSettingDisabled : SuppressionReason {
        override val suppressedFeatures: Int = FEATURE_ALL
    }

    /** Suppress hub due to the user being locked */
    data object ReasonUserLocked : SuppressionReason {
        override val suppressedFeatures: Int = FEATURE_ALL
    }

    /** Suppress hub due the a secondary user being active */
    data object ReasonSecondaryUser : SuppressionReason {
        override val suppressedFeatures: Int = FEATURE_ALL
    }

    /** Suppress hub due to the flag being disabled */
    data object ReasonFlagDisabled : SuppressionReason {
        override val suppressedFeatures: Int = FEATURE_ALL
    }

    /** Suppress hub due to an unknown reason, used as initial state and in tests */
    data class ReasonUnknown(override val suppressedFeatures: Int = FEATURE_ALL) :
        SuppressionReason
}
