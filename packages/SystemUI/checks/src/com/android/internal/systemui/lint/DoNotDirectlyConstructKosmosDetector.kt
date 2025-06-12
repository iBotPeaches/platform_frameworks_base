/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.systemui.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass

/**
 * Detects direct construction of `Kosmos()` in subclasses of SysuiTestCase, which can and should
 * use `testKosmos`. See go/thetiger
 */
class DoNotDirectlyConstructKosmosDetector : Detector(), SourceCodeScanner {
    override fun getApplicableConstructorTypes() = listOf("com.android.systemui.kosmos.Kosmos")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val superClassNames =
            node.getContainingUClass()?.superTypes.orEmpty().map { it.resolve()?.qualifiedName }
        if (superClassNames.contains("com.android.systemui.SysuiTestCase")) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node.methodIdentifier),
                message = "Prefer testKosmos to direct Kosmos() in sysui tests.  go/testkosmos",
            )
        }
        super.visitConstructor(context, node, constructor)
    }

    companion object {
        @JvmStatic
        val ISSUE =
            Issue.create(
                id = "DoNotDirectlyConstructKosmos",
                briefDescription =
                    "Prefer testKosmos to direct Kosmos() in sysui tests.  go/testkosmos",
                explanation =
                    """
                    SysuiTestCase.testKosmos allows us to pre-populate a Kosmos instance with
                    team-standard fixture values, and makes it easier to make centralized changes
                    when necessary.  See go/testkosmos
                """,
                category = Category.TESTING,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        DoNotDirectlyConstructKosmosDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
