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
package com.android.hoststubgen

import com.android.hoststubgen.filters.FilterPolicy
import com.android.hoststubgen.utils.ArgIterator
import com.android.hoststubgen.utils.BaseOptions
import com.android.hoststubgen.utils.FileOrResource
import com.android.hoststubgen.utils.SetOnce

private fun parsePackageRedirect(fromColonTo: String): Pair<String, String> {
    val colon = fromColonTo.indexOf(':')
    if ((colon < 1) || (colon + 1 >= fromColonTo.length)) {
        throw ArgumentsException("--package-redirect must be a colon-separated string")
    }
    // TODO check for duplicates
    return Pair(fromColonTo.substring(0, colon), fromColonTo.substring(colon + 1))
}

/**
 * Options to configure [HostStubGenClassProcessor].
 */
open class HostStubGenClassProcessorOptions(
    var keepAnnotations: MutableSet<String> = mutableSetOf(),
    var throwAnnotations: MutableSet<String> = mutableSetOf(),
    var removeAnnotations: MutableSet<String> = mutableSetOf(),
    var ignoreAnnotations: MutableSet<String> = mutableSetOf(),
    var keepClassAnnotations: MutableSet<String> = mutableSetOf(),
    var partiallyAllowedAnnotations: MutableSet<String> = mutableSetOf(),
    var redirectAnnotations: MutableSet<String> = mutableSetOf(),

    var substituteAnnotations: MutableSet<String> = mutableSetOf(),
    var redirectionClassAnnotations: MutableSet<String> = mutableSetOf(),
    var classLoadHookAnnotations: MutableSet<String> = mutableSetOf(),
    var keepStaticInitializerAnnotations: MutableSet<String> = mutableSetOf(),

    var packageRedirects: MutableList<Pair<String, String>> = mutableListOf(),

    var annotationAllowedClassesFile: SetOnce<String?> = SetOnce(null),

    var defaultClassLoadHook: SetOnce<String?> = SetOnce(null),
    var defaultMethodCallHook: SetOnce<String?> = SetOnce(null),

    var policyOverrideFiles: MutableList<FileOrResource> = mutableListOf(),

    var defaultPolicy: SetOnce<FilterPolicy> = SetOnce(FilterPolicy.Remove),

    var deleteFinals: SetOnce<Boolean> = SetOnce(false),

    var enableClassChecker: SetOnce<Boolean> = SetOnce(false),
    var enablePreTrace: SetOnce<Boolean> = SetOnce(false),
    var enablePostTrace: SetOnce<Boolean> = SetOnce(false),
) : BaseOptions() {

    private val allAnnotations = mutableSetOf<String>()

    private fun ensureUniqueAnnotation(name: String): String {
        if (!allAnnotations.add(name)) {
            throw DuplicateAnnotationException(name)
        }
        return name
    }

    override fun parseOption(option: String, args: ArgIterator): Boolean {
        // Define some shorthands...
        fun nextArg(): String = args.nextArgRequired(option)
        fun MutableSet<String>.addUniqueAnnotationArg(): String =
            nextArg().also { this += ensureUniqueAnnotation(it) }

        when (option) {
            "--policy-override-file" -> policyOverrideFiles.add(FileOrResource(nextArg()))

            "--default-remove" -> defaultPolicy.set(FilterPolicy.Remove)
            "--default-throw" -> defaultPolicy.set(FilterPolicy.Throw)
            "--default-keep" -> defaultPolicy.set(FilterPolicy.Keep)

            "--keep-annotation" ->
                keepAnnotations.addUniqueAnnotationArg()

            "--keep-class-annotation" ->
                keepClassAnnotations.addUniqueAnnotationArg()

            "--partially-allowed-annotation" ->
                partiallyAllowedAnnotations.addUniqueAnnotationArg()

            "--throw-annotation" ->
                throwAnnotations.addUniqueAnnotationArg()

            "--remove-annotation" ->
                removeAnnotations.addUniqueAnnotationArg()

            "--ignore-annotation" ->
                ignoreAnnotations.addUniqueAnnotationArg()

            "--substitute-annotation" ->
                substituteAnnotations.addUniqueAnnotationArg()

            "--redirect-annotation" ->
                redirectAnnotations.addUniqueAnnotationArg()

            "--redirection-class-annotation" ->
                redirectionClassAnnotations.addUniqueAnnotationArg()

            "--class-load-hook-annotation" ->
                classLoadHookAnnotations.addUniqueAnnotationArg()

            "--keep-static-initializer-annotation" ->
                keepStaticInitializerAnnotations.addUniqueAnnotationArg()

            "--package-redirect" ->
                packageRedirects += parsePackageRedirect(nextArg())

            "--annotation-allowed-classes-file" ->
                annotationAllowedClassesFile.set(nextArg())

            "--default-class-load-hook" ->
                defaultClassLoadHook.set(nextArg())

            "--default-method-call-hook" ->
                defaultMethodCallHook.set(nextArg())

            "--delete-finals" -> deleteFinals.set(true)

            // Following options are for debugging.
            "--enable-class-checker" -> enableClassChecker.set(true)
            "--no-class-checker" -> enableClassChecker.set(false)

            "--enable-pre-trace" -> enablePreTrace.set(true)
            "--no-pre-trace" -> enablePreTrace.set(false)

            "--enable-post-trace" -> enablePostTrace.set(true)
            "--no-post-trace" -> enablePostTrace.set(false)

            else -> return false
        }

        return true
    }

    override fun dumpFields(): String {
        return """
            keepAnnotations=$keepAnnotations,
            throwAnnotations=$throwAnnotations,
            removeAnnotations=$removeAnnotations,
            ignoreAnnotations=$ignoreAnnotations,
            keepClassAnnotations=$keepClassAnnotations,
            partiallyAllowedAnnotations=$partiallyAllowedAnnotations,
            substituteAnnotations=$substituteAnnotations,
            nativeSubstituteAnnotations=$redirectionClassAnnotations,
            classLoadHookAnnotations=$classLoadHookAnnotations,
            keepStaticInitializerAnnotations=$keepStaticInitializerAnnotations,
            packageRedirects=$packageRedirects,
            annotationAllowedClassesFile=$annotationAllowedClassesFile,
            defaultClassLoadHook=$defaultClassLoadHook,
            defaultMethodCallHook=$defaultMethodCallHook,
            policyOverrideFiles=${policyOverrideFiles.toTypedArray().contentToString()},
            defaultPolicy=$defaultPolicy,
            deleteFinals=$deleteFinals,
            enableClassChecker=$enableClassChecker,
            enablePreTrace=$enablePreTrace,
            enablePostTrace=$enablePostTrace,
        """.trimIndent()
    }
}
