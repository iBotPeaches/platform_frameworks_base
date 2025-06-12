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
package com.android.internal.systemui.lint

import com.android.internal.systemui.lint.ShadeDisplayAwareDetector.Companion.isInRelevantShadePackage
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.tryResolveUDeclaration
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUFile

/**
 * Lint check to ensure that when creating dialogs shade-relevant packages, the correct Context is
 * provided.
 *
 * This is to ensure that the dialog is created with the correct context when the shade is moved to
 * a different display. When the shade is moved, the configuration might change, and only
 * `@ShadeDisplayAware`-annotated components will update accordingly to reflect the new display.
 *
 * Example:
 * ```kotlin
 * class ExampleClass
 *      @Inject
 *      constructor(private val contextInteractor: ShadeDialogContextInteractor) {
 *
 *      fun showDialog() {
 *          val dialog = systemUIDialogFactory.create(delegate, contextInteractor.context)
 *          dialog.show()
 *      }
 *  }
 * ```
 */
// TODO: b/396066687 - update linter after refactoring to use ShadeDialogFactory
class ShadeDisplayAwareDialogDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> = listOf(CREATE_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!isInRelevantShadePackage(node.getContainingUFile()?.packageName)) return
        if (!context.evaluator.isMemberInClass(method, SYSUI_DIALOG_FACTORY)) return
        val contextArg =
            node.valueArguments.find {
                it.getExpressionType()?.canonicalText == "android.content.Context"
            }
        if (contextArg == null) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getNameLocation(node),
                message =
                    "SystemUIDialog.Factory#create requires a Context that accounts for the " +
                        "shade's display. Use create(shadeDialogContextInteractor.getContext()) " +
                        "or create(shadeDialogContextInteractor.context) to provide the correct Context.",
            )
        } else {
            val isProvidedByContextInteractor =
                contextArg.tryResolveUDeclaration()?.getContainingUClass()?.qualifiedName ==
                    SHADE_DIALOG_CONTEXT_INTERACTOR

            if (!isProvidedByContextInteractor) {
                context.report(
                    issue = ISSUE,
                    scope = contextArg,
                    location = context.getNameLocation(contextArg),
                    message =
                        "In shade-relevant packages, SystemUIDialog.Factory#create must be called " +
                            "with the Context directly from ShadeDialogContextInteractor " +
                            "(ShadeDialogContextInteractor.context or getContext()). " +
                            "Avoid intermediate variables or function calls. This direct usage " +
                            "is required to ensure proper shade display handling.",
                )
            }
        }
    }

    companion object {
        private const val CREATE_METHOD = "create"
        private const val SYSUI_DIALOG_FACTORY =
            "com.android.systemui.statusbar.phone.SystemUIDialog.Factory"
        private const val SHADE_DIALOG_CONTEXT_INTERACTOR =
            "com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor"

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "ShadeDisplayAwareDialogChecker",
                briefDescription = "Checking for shade display aware context when creating dialogs",
                explanation =
                    """
                Dialogs created by the notification shade must use a special Context to appear
                on the correct display, especially when the shade is not on the default display.
            """
                        .trimIndent(),
                category = Category.CORRECTNESS,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        ShadeDisplayAwareDialogDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
