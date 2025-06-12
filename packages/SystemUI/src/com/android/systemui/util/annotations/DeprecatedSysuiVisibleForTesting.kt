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

package com.android.systemui.util.annotations

/**
 * Given the effort in go/internal-harmful to eliminate the attempt to use Kotlin `internal` as a
 * test-visibility marker, we are centrally moving these APIs to public, marked both with
 * [VisibleForTesting] and this annotation. Ideally, over time, these APIs should be replaced with
 * explicit named testing APIs (see go/internal-harmful)
 */
@Deprecated(
    "Indicates an API that has been marked @VisibleForTesting, but requires further thought"
)
annotation class DeprecatedSysuiVisibleForTesting()
