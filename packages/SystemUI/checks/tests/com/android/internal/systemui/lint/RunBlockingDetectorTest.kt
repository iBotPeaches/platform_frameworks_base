/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *            http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.android.internal.systemui.lint

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class RunBlockingDetectorTest : SystemUILintDetectorTest() {

    override fun getDetector(): Detector = RunBlockingDetector()

    override fun getIssues(): List<Issue> = listOf(RunBlockingDetector.ISSUE)

    @Test
    fun testViolation() {
        lint()
            .files(
                kotlin(
                    """
                    package com.example

                    import kotlinx.coroutines.runBlocking

                    class MyClass {
                        fun myMethod() {
                            runBlocking {
                                // Some code here
                            }
                        }
                    }
                    """
                ),
                RUN_BLOCKING_DEFINITION,
            )
            .issues(RunBlockingDetector.ISSUE)
            .run()
            .expect(
                """
src/com/example/MyClass.kt:4: Warning: Importing kotlinx.coroutines.runBlocking is not allowed. [RunBlockingUsage]
                    import kotlinx.coroutines.runBlocking
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 1 warnings
"""
                    .trimIndent()
            )
    }

    // Verifies that the lint check does *not* flag calls to other methods.
    @Test
    fun testNotViolation() {
        lint()
            .detector(RunBlockingDetector())
            .issues(RunBlockingDetector.ISSUE)
            .files(
                kotlin(
                    """
                    package com.example

                    class MyClass {
                        fun myMethod() {
                            myOtherMethod {
                            }
                        }

                        fun myOtherMethod(block: () -> Unit) {
                            block()
                        }
                    }
                    """
                )
            )
            .run()
            .expectClean()
    }

    private companion object {
        val RUN_BLOCKING_DEFINITION =
            kotlin(
                """
                    package kotlinx.coroutines

                    fun runBlocking(block: suspend () -> Unit) {
                        // Implementation details don't matter for this test.
                    }
                    """
            )
    }
}
