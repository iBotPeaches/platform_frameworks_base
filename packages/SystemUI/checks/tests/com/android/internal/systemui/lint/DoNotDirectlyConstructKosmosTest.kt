/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class DoNotDirectlyConstructKosmosTest : SystemUILintDetectorTest() {
    override fun getDetector(): Detector = DoNotDirectlyConstructKosmosDetector()

    override fun getIssues(): List<Issue> = listOf(DoNotDirectlyConstructKosmosDetector.ISSUE)

    @Test
    fun wronglyTriesToDirectlyConstructKosmos() {
        val runOnSource =
            runOnSource(
                """
                      package test.pkg.name

                      import com.android.systemui.kosmos.Kosmos
                      import com.android.systemui.SysuiTestCase

                      class MyTest: SysuiTestCase {
                          val kosmos = Kosmos()
                      }
                """
            )

        runOnSource
            .expectWarningCount(1)
            .expect(
                """
                src/test/pkg/name/MyTest.kt:7: Warning: Prefer testKosmos to direct Kosmos() in sysui tests.  go/testkosmos [DoNotDirectlyConstructKosmos]
                    val kosmos = Kosmos()
                                 ~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    @Test
    fun okToConstructKosmosIfNotInSysuiTestCase() {
        val runOnSource =
            runOnSource(
                """
                      package test.pkg.name

                      import com.android.systemui.kosmos.Kosmos

                      class MyTest {
                          val kosmos = Kosmos()
                      }
                """
            )

        runOnSource.expectWarningCount(0)
    }

    private fun runOnSource(source: String): TestLintResult {
        return lint()
            .files(TestFiles.kotlin(source).indented(), kosmosStub, sysuiTestCaseStub)
            .issues(DoNotDirectlyConstructKosmosDetector.ISSUE)
            .run()
    }

    companion object {
        private val kosmosStub: TestFile =
            kotlin(
                """
                package com.android.systemui.kosmos

                class Kosmos
            """
            )

        private val sysuiTestCaseStub: TestFile =
            kotlin(
                """
                package com.android.systemui

                class SysuiTestCase
                """
            )
    }
}
