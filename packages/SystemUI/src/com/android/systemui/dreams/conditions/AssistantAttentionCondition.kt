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
package com.android.systemui.dreams.conditions

import com.android.systemui.assist.AssistManager
import com.android.systemui.assist.AssistManager.VisualQueryAttentionListener
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shared.condition.Condition
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** [AssistantAttentionCondition] provides a signal when assistant has the user's attention. */
class AssistantAttentionCondition
@Inject
constructor(@Application scope: CoroutineScope, private val assistManager: AssistManager) :
    Condition(scope) {
    private val visualQueryAttentionListener: VisualQueryAttentionListener =
        object : VisualQueryAttentionListener {
            override fun onAttentionGained() {
                updateCondition(true)
            }

            override fun onAttentionLost() {
                updateCondition(false)
            }
        }

    override suspend fun start() {
        assistManager.addVisualQueryAttentionListener(visualQueryAttentionListener)
    }

    public override fun stop() {
        assistManager.removeVisualQueryAttentionListener(visualQueryAttentionListener)
    }

    override val startStrategy: Int
        get() = START_EAGERLY
}
