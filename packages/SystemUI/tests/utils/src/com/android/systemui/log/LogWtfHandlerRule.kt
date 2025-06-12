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
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class LogWtfHandlerRule : TestRule {

    private var failureLogExemptions = mutableListOf<FailureLogExemption>()

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val handler = TerribleFailureTestHandler()
                val originalWtfHandler = Log.setWtfHandler(handler)
                var failure: Throwable? = null
                try {
                    base.evaluate()
                } catch (ex: Throwable) {
                    failure = ex
                } finally {
                    failure =
                        runAndAddSuppressed(failure) {
                            handler.onTestFinished(failureLogExemptions)
                        }
                    Log.setWtfHandler(originalWtfHandler)
                }
                if (failure != null) {
                    throw failure
                }
            }
        }
    }

    /** Adds a log failure exemption. Exemptions are evaluated at the end of the test. */
    fun addFailureLogExemption(exemption: FailureLogExemption) {
        failureLogExemptions.add(exemption)
    }

    /** Clears and sets exemptions. Exemptions are evaluated at the end of the test. */
    fun resetFailureLogExemptions(vararg exemptions: FailureLogExemption) {
        failureLogExemptions = exemptions.toMutableList()
    }

    private fun runAndAddSuppressed(currentError: Throwable?, block: () -> Unit): Throwable? {
        try {
            block()
        } catch (t: Throwable) {
            if (currentError == null) {
                return t
            }
            currentError.addSuppressed(t)
        }
        return currentError
    }

    private class TerribleFailureTestHandler : TerribleFailureHandler {
        private val failureLogs = mutableListOf<FailureLog>()

        override fun onTerribleFailure(tag: String, what: Log.TerribleFailure, system: Boolean) {
            failureLogs.add(FailureLog(tag = tag, failure = what, system = system))
        }

        fun onTestFinished(exemptions: List<FailureLogExemption>) {
            val failures =
                failureLogs.filter { failureLog ->
                    !exemptions.any { it.isFailureLogExempt(failureLog) }
                }
            if (failures.isNotEmpty()) {
                throw AssertionError("Unexpected Log.wtf calls: $failures", failures[0].failure)
            }
        }
    }

    /** All the information from a call to [Log.wtf] that was handed to [TerribleFailureHandler] */
    data class FailureLog(val tag: String, val failure: Log.TerribleFailure, val system: Boolean)

    /** An interface for exempting a [FailureLog] from causing a test failure. */
    fun interface FailureLogExemption {
        /** Determines whether a log should be except from failing the test. */
        fun isFailureLogExempt(log: FailureLog): Boolean
    }
}
