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

import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.findAnyAnnotation
import com.android.hoststubgen.filters.AnnotationBasedFilter
import com.android.hoststubgen.filters.ClassWidePolicyPropagatingFilter
import com.android.hoststubgen.filters.ConstantFilter
import com.android.hoststubgen.filters.DefaultHookInjectingFilter
import com.android.hoststubgen.filters.FilterRemapper
import com.android.hoststubgen.filters.ImplicitOutputFilter
import com.android.hoststubgen.filters.KeepNativeFilter
import com.android.hoststubgen.filters.OutputFilter
import com.android.hoststubgen.filters.SanitizationFilter
import com.android.hoststubgen.filters.TextFileFilterPolicyBuilder
import com.android.hoststubgen.hosthelper.HostStubGenProcessedAsKeep
import com.android.hoststubgen.utils.ClassPredicate
import com.android.hoststubgen.visitors.BaseAdapter
import com.android.hoststubgen.visitors.ImplGeneratingAdapter
import com.android.hoststubgen.visitors.PackageRedirectRemapper
import java.io.PrintWriter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.util.CheckClassAdapter
import org.objectweb.asm.util.TraceClassVisitor

/**
 * This class implements bytecode transformation of HostStubGen.
 */
class HostStubGenClassProcessor(
    private val options: HostStubGenClassProcessorOptions,
    val allClasses: ClassNodes,
    private val errors: HostStubGenErrors = HostStubGenErrors(),
    private val stats: HostStubGenStats? = null,
) {
    val filter = buildFilter()
    val remapper = FilterRemapper(filter)

    private val packageRedirector = PackageRedirectRemapper(options.packageRedirects)
    private val processedAnnotation = setOf(HostStubGenProcessedAsKeep.CLASS_DESCRIPTOR)

    /**
     * Build the filter, which decides what classes/methods/fields should be put in stub or impl
     * jars, and "how". (e.g. with substitution?)
     */
    private fun buildFilter(): OutputFilter {
        // We build a "chain" of multiple filters here.
        //
        // The filters are build in from "inside", meaning the first filter created here is
        // the last filter used, so it has the least precedence.
        //
        // So, for example, the "remove" annotation, which is handled by AnnotationBasedFilter,
        // can override a class-wide annotation, which is handled by
        // ClassWidePolicyPropagatingFilter, and any annotations can be overridden by the
        // text-file based filter, which is handled by parseTextFilterPolicyFile.

        // The first filter is for the default policy from the command line options.
        var filter: OutputFilter = ConstantFilter(options.defaultPolicy.get, "default-by-options")

        // Next, we build a filter that preserves all native methods by default
        filter = KeepNativeFilter(allClasses, filter)

        // Next, we need a filter that resolves "class-wide" policies.
        // This is used when a member (methods, fields, nested classes) don't get any policies
        // from upper filters. e.g. when a method has no annotations, then this filter will apply
        // the class-wide policy, if any. (if not, we'll fall back to the above filter.)
        filter = ClassWidePolicyPropagatingFilter(allClasses, filter)

        // Inject default hooks from options.
        filter = DefaultHookInjectingFilter(
            allClasses,
            options.defaultClassLoadHook.get,
            options.defaultMethodCallHook.get,
            filter
        )

        val annotationAllowedPredicate = options.annotationAllowedClassesFile.get.let { file ->
            if (file == null) {
                ClassPredicate.newConstantPredicate(true) // Allow all classes
            } else {
                ClassPredicate.loadFromFile(file, false)
            }
        }

        // Next, Java annotation based filter.
        val annotFilter = AnnotationBasedFilter(
            errors,
            allClasses,
            options.keepAnnotations,
            options.keepClassAnnotations,
            options.throwAnnotations,
            options.removeAnnotations,
            options.ignoreAnnotations,
            options.substituteAnnotations,
            options.redirectAnnotations,
            options.redirectionClassAnnotations,
            options.classLoadHookAnnotations,
            options.partiallyAllowedAnnotations,
            options.keepStaticInitializerAnnotations,
            annotationAllowedPredicate,
            filter
        )
        filter = annotFilter

        // Next, "text based" filter, which allows to override policies without touching
        // the target code.
        if (options.policyOverrideFiles.isNotEmpty()) {
            val builder = TextFileFilterPolicyBuilder(allClasses, filter)
            options.policyOverrideFiles.forEach(builder::parse)
            filter = builder.createOutputFilter()
            annotFilter.annotationAllowedMembers = builder.annotationAllowedMembersFilter
        }

        // Apply the implicit filter.
        filter = ImplicitOutputFilter(errors, allClasses, filter)

        // Add a final sanitization step.
        filter = SanitizationFilter(errors, allClasses, filter)

        return filter
    }

    private fun buildVisitor(base: ClassVisitor, className: String): ClassVisitor {
        // Connect to the base visitor
        var outVisitor: ClassVisitor = base

        if (options.enableClassChecker.get) {
            outVisitor = CheckClassAdapter(outVisitor)
        }

        // Remapping should happen at the end.
        outVisitor = ClassRemapper(outVisitor, remapper)

        val visitorOptions = BaseAdapter.Options(
            errors = errors,
            stats = stats,
            deleteClassFinals = options.deleteFinals.get,
            deleteMethodFinals = options.deleteFinals.get,
        )

        val verbosePrinter = PrintWriter(log.getWriter(LogLevel.Verbose))

        // Inject TraceClassVisitor for debugging.
        if (options.enablePostTrace.get) {
            outVisitor = TraceClassVisitor(outVisitor, verbosePrinter)
        }

        // Handle --package-redirect
        if (!packageRedirector.isEmpty) {
            // Don't apply the remapper on redirect-from classes.
            // Otherwise, if the target jar actually contains the "from" classes (which
            // may or may not be the case) they'd be renamed.
            // But we update all references in other places, so, a method call to a "from" class
            // would be replaced with the "to" class. All type references (e.g. variable types)
            // will be updated too.
            if (!packageRedirector.isTarget(className)) {
                outVisitor = ClassRemapper(outVisitor, packageRedirector)
            } else {
                log.v(
                    "Class $className is a redirect-from class, not applying" +
                            " --package-redirect"
                )
            }
        }

        outVisitor = ImplGeneratingAdapter(allClasses, outVisitor, filter, visitorOptions)

        // Inject TraceClassVisitor for debugging.
        if (options.enablePreTrace.get) {
            outVisitor = TraceClassVisitor(outVisitor, verbosePrinter)
        }

        return outVisitor
    }

    fun processClassBytecode(bytecode: ByteArray): ByteArray {
        val cr = ClassReader(bytecode)

        // If the class was already processed previously, skip
        val clz = allClasses.getClass(cr.className)
        if (clz.findAnyAnnotation(processedAnnotation) != null) {
            return bytecode
        }

        // COMPUTE_FRAMES wouldn't be happy if code uses
        val flags = ClassWriter.COMPUTE_MAXS // or ClassWriter.COMPUTE_FRAMES
        val cw = ClassWriter(flags)

        val outVisitor = buildVisitor(cw, cr.className)

        cr.accept(outVisitor, ClassReader.EXPAND_FRAMES)
        return cw.toByteArray()
    }
}
