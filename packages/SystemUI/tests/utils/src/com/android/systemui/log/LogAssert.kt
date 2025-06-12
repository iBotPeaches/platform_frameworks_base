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
package com.android.systemui.log

import android.util.Log
import android.util.Log.TerribleFailureHandler
import com.google.common.truth.Truth.assertWithMessage

/** Asserts that [notLoggingBlock] does not make a call to [Log.wtf] */
fun <T> assertDoesNotLogWtf(
    message: String = "Expected Log.wtf not to be called",
    notLoggingBlock: () -> T,
): T {
    var caught: TerribleFailureLog? = null
    val newHandler = TerribleFailureHandler { tag, failure, system ->
        caught = TerribleFailureLog(tag, failure, system)
    }
    val oldHandler = Log.setWtfHandler(newHandler)
    val result =
        try {
            notLoggingBlock()
        } finally {
            Log.setWtfHandler(oldHandler)
        }
    caught?.let { throw AssertionError("$message: $it", it.failure) }
    return result
}

/** Assert that [loggingBlock] makes a call to [Log.wtf] */
@JvmOverloads
fun <T> assertLogsWtf(
    message: String = "Expected Log.wtf to be called",
    allowMultiple: Boolean = false,
    loggingBlock: () -> T,
): WtfBlockResult<T> {
    val caught = mutableListOf<TerribleFailureLog>()
    val newHandler = TerribleFailureHandler { tag, failure, system ->
        caught.add(TerribleFailureLog(tag, failure, system))
    }
    val oldHandler = Log.setWtfHandler(newHandler)
    val result =
        try {
            loggingBlock()
        } finally {
            Log.setWtfHandler(oldHandler)
        }
    assertWithMessage(message).that(caught).isNotEmpty()
    if (!allowMultiple) {
        assertWithMessage("Unexpectedly caught Log.Wtf multiple times").that(caught).hasSize(1)
    }
    return WtfBlockResult(caught, result)
}

/** Assert that [loggingBlock] makes at least one call to [Log.wtf] */
@JvmOverloads
fun <T> assertLogsWtfs(
    message: String = "Expected Log.wtf to be called once or more",
    loggingBlock: () -> T,
): WtfBlockResult<T> = assertLogsWtf(message, allowMultiple = true, loggingBlock)

/** The data passed to [TerribleFailureHandler.onTerribleFailure] */
data class TerribleFailureLog(
    val tag: String,
    val failure: Log.TerribleFailure,
    val system: Boolean,
)

/** The [Log.wtf] logs and return value of the block */
data class WtfBlockResult<T>(val logs: List<TerribleFailureLog>, val result: T)

/** Assert that [loggingRunnable] makes a call to [Log.wtf] */
@JvmOverloads
fun assertRunnableLogsWtf(
    message: String = "Expected Log.wtf to be called",
    allowMultiple: Boolean = false,
    loggingRunnable: Runnable,
): WtfBlockResult<Unit> =
    assertLogsWtf(message = message, allowMultiple = allowMultiple) { loggingRunnable.run() }

/** Assert that [loggingRunnable] makes at least one call to [Log.wtf] */
@JvmOverloads
fun assertRunnableLogsWtfs(
    message: String = "Expected Log.wtf to be called once or more",
    loggingRunnable: Runnable,
): WtfBlockResult<Unit> = assertRunnableLogsWtf(message, allowMultiple = true, loggingRunnable)
