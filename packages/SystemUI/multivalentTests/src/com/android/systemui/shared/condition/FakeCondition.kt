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
package com.android.systemui.shared.condition

import kotlinx.coroutines.CoroutineScope

/**
 * Fake implementation of [Condition], and provides a way for tests to update condition fulfillment.
 */
class FakeCondition : Condition {
    constructor(scope: CoroutineScope) : super(scope)

    constructor(
        scope: CoroutineScope,
        initialValue: Boolean?,
        overriding: Boolean,
    ) : super(scope, initialValue, overriding)

    public override suspend fun start() {}

    public override fun stop() {}

    override val startStrategy: Int
        get() = START_EAGERLY

    fun fakeUpdateCondition(isConditionMet: Boolean) {
        updateCondition(isConditionMet)
    }

    fun fakeClearCondition() {
        clearCondition()
    }
}
