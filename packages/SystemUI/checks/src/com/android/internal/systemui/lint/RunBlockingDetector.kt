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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UImportStatement

/** Detects whether [runBlocking] is being imported. */
class RunBlockingDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UFile::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitFile(node: UFile) {
                for (importStatement in node.imports) {
                    visitImportStatement(context, importStatement)
                }
            }
        }
    }

    private fun visitImportStatement(context: JavaContext, importStatement: UImportStatement) {
        val importName = importStatement.importReference?.asSourceString()
        if (FORBIDDEN_IMPORTS.contains(importName)) {
            context.report(
                ISSUE,
                importStatement as UElement,
                context.getLocation(importStatement),
                "Importing $importName is not allowed.",
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE =
            Issue.create(
                id = "RunBlockingUsage",
                briefDescription = "Discouraged runBlocking call",
                explanation =
                    """
                    Using `runBlocking` is generally discouraged in Android
                    development as it can lead to UI freezes and ANRs.
                    Consider using `launch` or `async` with coroutine scope
                    instead. If needed from java, consider introducing a method
                    with a callback instead from kotlin.
                    """,
                category = Category.PERFORMANCE,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                    Implementation(RunBlockingDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )

        val FORBIDDEN_IMPORTS = listOf("kotlinx.coroutines.runBlocking")
    }
}
