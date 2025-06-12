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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag

/** Asserts that the tile grid with [testTag] contains exactly [specs] */
fun ComposeContentTestRule.assertGridContainsExactly(testTag: String, specs: List<String>) {
    onNodeWithTag(testTag)
        .onChildren()
        .filter(SemanticsMatcher.contentDescriptionStartsWith("tile"))
        .apply {
            fetchSemanticsNodes().forEachIndexed { index, _ ->
                get(index).assert(hasContentDescription(specs[index]))
            }
        }
}

/**
 * A [SemanticsMatcher] that matches anything with a content description starting with the given
 * [prefix]
 */
fun SemanticsMatcher.Companion.contentDescriptionStartsWith(prefix: String): SemanticsMatcher {
    return SemanticsMatcher("${SemanticsProperties.ContentDescription.name} starts with $prefix") {
        semanticsNode ->
        semanticsNode.config.getOrNull(SemanticsProperties.ContentDescription)?.any {
            it.startsWith(prefix)
        } ?: false
    }
}
