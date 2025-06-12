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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class ShadeDisplayAwareDialogDetectorTest : SystemUILintDetectorTest() {
    override fun getDetector(): Detector = ShadeDisplayAwareDialogDetector()

    override fun getIssues(): List<Issue> = listOf(ShadeDisplayAwareDialogDetector.ISSUE)

    private val injectStub: TestFile =
        kotlin(
                """
                package javax.inject
                @Retention(AnnotationRetention.RUNTIME) annotation class Inject
                """
            )
            .indented()
    private val shadeDisplayAwareStub: TestFile =
        kotlin(
                """
                package com.android.systemui.shade
                @Retention(AnnotationRetention.RUNTIME) annotation class ShadeDisplayAware
                """
            )
            .indented()
    private val mainStub: TestFile =
        kotlin(
                """
                package com.android.systemui.dagger.qualifiers

                @Retention(AnnotationRetention.RUNTIME) annotation class Main
                """
            )
            .indented()
    private val dialogContextStub: TestFile =
        kotlin(
                """
                package com.android.systemui.shade.domain.interactor

                import android.content.Context

                interface ShadeDialogContextInteractor {
                    val context: Context
                }
                """
            )
            .indented()
    private val delegateStub: TestFile =
        java(
            """
                package com.android.systemui.statusbar.phone;

                public interface Delegate { }
            """
                .trimIndent()
        )
    private val sysuiDialogStub: TestFile =
        java(
                """
            package com.android.systemui.statusbar.phone;

            import android.content.Context;
            public class SystemUIDialog {
                public SystemUIDialog(int id) { }

                public static class Factory {
                    public SystemUIDialog create() {
                        return new SystemUIDialog();
                    }

                    public SystemUIDialog create(Context context) {
                        return new SystemUIDialog();
                    }

                    public SystemUIDialog create(Delegate delegate, Context context) {
                        return new SystemUIDialog();
                    }

                    public SystemUIDialog create(Delegate delegate, Context context,
                            boolean shouldAcsdDismissDialog) {
                        return new SystemUIDialog();
                    }

                    public SystemUIDialog create(Delegate delegate, Context context, int theme) {
                        return new SystemUIDialog();
                    }

                    public SystemUIDialog create(Delegate delegate) {
                        return new SystemUIDialog();
                    }
                }
            }
            """
            )
            .indented()

    private val otherStubs =
        arrayOf(
            injectStub,
            shadeDisplayAwareStub,
            mainStub,
            delegateStub,
            sysuiDialogStub,
            dialogContextStub,
        )

    @Test
    fun create_noArguments() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import android.content.Context
                        import com.android.systemui.statusbar.phone.SystemUIDialog

                        class ExampleClass
                            @Inject
                            constructor(private val systemUIDialogFactory: SystemUIDialog.Factory) {

                            fun showDialog() {
                                val dialog = systemUIDialogFactory.create()
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectWarningCount(1)
            .expectContains(
                "SystemUIDialog.Factory#create requires a Context that accounts for the " +
                    "shade's display. Use create(shadeDialogContextInteractor.getContext()) " +
                    "or create(shadeDialogContextInteractor.context) to provide the correct Context."
            )
            .expectContains("[ShadeDisplayAwareDialogChecker]")
            .expectContains("0 errors, 1 warning")
    }

    @Test
    fun create_UnannotatedContext() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import android.content.Context
                        import com.android.systemui.statusbar.phone.SystemUIDialog
                        class ExampleClass
                            @Inject
                            constructor(
                                private val context: Context,
                                private val systemUIDialogFactory: SystemUIDialog.Factory
                            ) {

                            fun showDialog() {
                                val dialog = systemUIDialogFactory.create(context)
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectWarningCount(1)
            .expectContains(
                "In shade-relevant packages, SystemUIDialog.Factory#create must be called " +
                    "with the Context directly from ShadeDialogContextInteractor " +
                    "(ShadeDialogContextInteractor.context or getContext()). " +
                    "Avoid intermediate variables or function calls. This direct usage " +
                    "is required to ensure proper shade display handling."
            )
            .expectContains("[ShadeDisplayAwareDialogChecker]")
            .expectContains("0 errors, 1 warning")
    }

    @Test
    fun create_annotatedContext() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import android.content.Context
                        import com.android.systemui.statusbar.phone.SystemUIDialog
                        import com.android.systemui.shade.ShadeDisplayAware
                        class ExampleClass
                            @Inject
                            constructor(
                                @ShadeDisplayAware private val context: Context,
                                private val systemUIDialogFactory: SystemUIDialog.Factory
                            ) {

                            fun showDialog() {
                                val dialog = systemUIDialogFactory.create(context)
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectWarningCount(1)
            .expectContains(
                "In shade-relevant packages, SystemUIDialog.Factory#create must be called " +
                    "with the Context directly from ShadeDialogContextInteractor " +
                    "(ShadeDialogContextInteractor.context or getContext()). " +
                    "Avoid intermediate variables or function calls. This direct usage " +
                    "is required to ensure proper shade display handling."
            )
            .expectContains("[ShadeDisplayAwareDialogChecker]")
            .expectContains("0 errors, 1 warning")
    }

    @Test
    fun create_LocalVariable() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import com.android.systemui.statusbar.phone.SystemUIDialog
                        import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor

                        class ExampleClass
                            @Inject
                            constructor(
                                private val contextInteractor: ShadeDialogContextInteractor,
                                private val systemUIDialogFactory: SystemUIDialog.Factory
                            ) {

                            fun showDialog() {
                                val context2 = contextInteractor.context
                                val dialog = systemUIDialogFactory.create(context2)
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectWarningCount(1)
            .expectContains(
                "In shade-relevant packages, SystemUIDialog.Factory#create must be called " +
                    "with the Context directly from ShadeDialogContextInteractor " +
                    "(ShadeDialogContextInteractor.context or getContext()). " +
                    "Avoid intermediate variables or function calls. This direct usage " +
                    "is required to ensure proper shade display handling."
            )
            .expectContains("[ShadeDisplayAwareDialogChecker]")
            .expectContains("0 errors, 1 warning")
    }

    @Test
    fun create_delegate_UnannotatedContext() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import android.content.Context
                        import com.android.systemui.statusbar.phone.SystemUIDialog
                        import com.android.systemui.statusbar.phone.Delegate
                        class ExampleClass
                            @Inject
                            constructor(
                                private val context: Context,
                                private val delegate: Delegate,
                                private val systemUIDialogFactory: SystemUIDialog.Factory
                            ) {

                            fun showDialog() {
                                val dialog = systemUIDialogFactory.create(delegate, context)
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectWarningCount(1)
            .expectContains(
                "In shade-relevant packages, SystemUIDialog.Factory#create must be called " +
                    "with the Context directly from ShadeDialogContextInteractor " +
                    "(ShadeDialogContextInteractor.context or getContext()). " +
                    "Avoid intermediate variables or function calls. This direct usage " +
                    "is required to ensure proper shade display handling."
            )
            .expectContains("[ShadeDisplayAwareDialogChecker]")
            .expectContains("0 errors, 1 warning")
    }

    @Test
    fun create_delegate_Context() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import android.content.Context
                        import com.android.systemui.statusbar.phone.SystemUIDialog
                        import com.android.systemui.statusbar.phone.Delegate
                        import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor

                        class ExampleClass
                            @Inject
                            constructor(
                                private val delegate: Delegate,
                                private val contextInteractor: ShadeDialogContextInteractor,
                                private val systemUIDialogFactory: SystemUIDialog.Factory
                            ) {

                            fun showDialog() {
                                val dialog = systemUIDialogFactory.create(delegate, contextInteractor.context)
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun create_Delegate_Context_Boolean() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor
                        import com.android.systemui.statusbar.phone.SystemUIDialog
                        import com.android.systemui.statusbar.phone.Delegate

                        class ExampleClass
                            @Inject
                            constructor(
                                private val delegate: Delegate,
                                private val contextInteractor: ShadeDialogContextInteractor,
                                private val systemUIDialogFactory: SystemUIDialog.Factory
                            ) {

                            fun showDialog() {
                                val dialog = systemUIDialogFactory.create(delegate, contextInteractor.context, true)
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun create_Delegate_UnannotatedContext_Int() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import android.content.Context
                        import com.android.systemui.statusbar.phone.SystemUIDialog
                        import com.android.systemui.statusbar.phone.Delegate
                        class ExampleClass
                            @Inject
                            constructor(
                                private val context: Context,
                                private val delegate: Delegate,
                                private val systemUIDialogFactory: SystemUIDialog.Factory
                            ) {

                            fun showDialog() {
                                val dialog = systemUIDialogFactory.create(delegate, context, 0)
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectWarningCount(1)
            .expectContains(
                "In shade-relevant packages, SystemUIDialog.Factory#create must be called " +
                    "with the Context directly from ShadeDialogContextInteractor " +
                    "(ShadeDialogContextInteractor.context or getContext()). " +
                    "Avoid intermediate variables or function calls. This direct usage " +
                    "is required to ensure proper shade display handling."
            )
            .expectContains("[ShadeDisplayAwareDialogChecker]")
            .expectContains("0 errors, 1 warning")
    }

    @Test
    fun create_Delegate_Context_Int() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor
                        import com.android.systemui.statusbar.phone.SystemUIDialog
                        import com.android.systemui.statusbar.phone.Delegate

                        class ExampleClass
                            @Inject
                            constructor(
                                private val delegate: Delegate,
                                private val contextInteractor: ShadeDialogContextInteractor,
                                private val systemUIDialogFactory: SystemUIDialog.Factory
                            ) {

                            fun showDialog() {
                                val dialog = systemUIDialogFactory.create(delegate, contextInteractor.context, 0)
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    @Test
    fun create_Delegate() {
        lint()
            .files(
                TestFiles.kotlin(
                    """
                        package com.android.systemui.shade.example
                        import javax.inject.Inject
                        import com.android.systemui.statusbar.phone.Delegate
                        import com.android.systemui.statusbar.phone.SystemUIDialog

                        class ExampleClass
                            @Inject
                            constructor(
                                private val delegate: Delegate,
                                private val systemUIDialogFactory: SystemUIDialog.Factory
                            ) {

                            fun showDialog() {
                                val dialog = systemUIDialogFactory.create(delegate)
                                dialog.show()
                            }
                        }
                    """
                        .trimIndent()
                ),
                *androidStubs,
                *otherStubs,
            )
            .issues(ShadeDisplayAwareDialogDetector.ISSUE)
            .testModes(TestMode.DEFAULT)
            .run()
            .expectWarningCount(1)
            .expectContains(
                "SystemUIDialog.Factory#create requires a Context that accounts for the " +
                    "shade's display. Use create(shadeDialogContextInteractor.getContext()) " +
                    "or create(shadeDialogContextInteractor.context) to provide the correct Context."
            )
            .expectContains("[ShadeDisplayAwareDialogChecker]")
            .expectContains("0 errors, 1 warning")
    }
}
