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

import android.annotation.IntDef

@Retention(AnnotationRetention.SOURCE)
@IntDef(
    flag = true,
    prefix = ["FEATURE_"],
    value = [FEATURE_AUTO_OPEN, FEATURE_MANUAL_OPEN, FEATURE_ENABLED, FEATURE_ALL],
)
annotation class CommunalFeature

/** If we should automatically open the hub */
const val FEATURE_AUTO_OPEN: Int = 1

/** If the user is allowed to manually open the hub */
const val FEATURE_MANUAL_OPEN: Int = 1 shl 1

/**
 * If the hub should be considered enabled. If not, it may be cleaned up entirely to reduce memory
 * footprint.
 */
const val FEATURE_ENABLED: Int = 1 shl 2

const val FEATURE_ALL: Int = FEATURE_ENABLED or FEATURE_MANUAL_OPEN or FEATURE_AUTO_OPEN
