/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.platform.test.ravenwood.ravenizer

import com.android.hoststubgen.GeneralUserErrorException
import com.android.hoststubgen.HostStubGenClassProcessor
import com.android.hoststubgen.addBytesEntry
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.zipEntryNameToClassName
import com.android.hoststubgen.copyZipEntry
import com.android.hoststubgen.executableName
import com.android.hoststubgen.log
import com.android.platform.test.ravenwood.ravenizer.adapter.RunnerRewritingAdapter
import java.io.FileOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter

/**
 * Various stats on Ravenizer.
 */
data class RavenizerStats(
    /** Total end-to-end time. */
    var totalTime: Double = .0,

    /** Time took to build [ClassNodes] */
    var loadStructureTime: Double = .0,

    /** Time took to validate the classes */
    var validationTime: Double = .0,

    /** Total real time spent for converting the jar file */
    var totalProcessTime: Double = .0,

    /** Total real time spent for ravenizing class files (excluding I/O time). */
    var totalRavenizeTime: Double = .0,

    /** Total real time spent for processing class files HSG style (excluding I/O time). */
    var totalHostStubGenTime: Double = .0,

    /** Total real time spent for copying class files without modification. */
    var totalCopyTime: Double = .0,

    /** # of entries in the input jar file */
    var totalEntries: Int = 0,

    /** # of *.class files in the input jar file */
    var totalClasses: Int = 0,

    /** # of *.class files that have been processed. */
    var processedClasses: Int = 0,
) {
    override fun toString(): String {
        return """
            RavenizerStats {
              totalTime=$totalTime,
              loadStructureTime=$loadStructureTime,
              validationTime=$validationTime,
              totalProcessTime=$totalProcessTime,
              totalRavenizeTime=$totalRavenizeTime,
              totalHostStubGenTime=$totalHostStubGenTime,
              totalCopyTime=$totalCopyTime,
              totalEntries=$totalEntries,
              totalClasses=$totalClasses,
              processedClasses=$processedClasses,
            }
            """.trimIndent()
    }
}

/**
 * Main class.
 */
class Ravenizer {
    fun run(options: RavenizerOptions) {
        val stats = RavenizerStats()

        stats.totalTime = log.nTime {
            val inJar = ZipFile(options.inJar.get)
            val allClasses = ClassNodes.loadClassStructures(inJar, options.inJar.get) {
                stats.loadStructureTime = it
            }
            val processor = HostStubGenClassProcessor(options, allClasses)

            inJar.process(
                options.outJar.get,
                options.outJar.get,
                options.enableValidation.get,
                options.fatalValidation.get,
                options.stripMockito.get,
                processor,
                stats,
            )
        }
        log.i(stats.toString())
    }

    private fun ZipFile.process(
        inJar: String,
        outJar: String,
        enableValidation: Boolean,
        fatalValidation: Boolean,
        stripMockito: Boolean,
        processor: HostStubGenClassProcessor,
        stats: RavenizerStats,
    ) {
        if (enableValidation) {
            stats.validationTime = log.iTime("Validating classes") {
                if (!validateClasses(processor.allClasses)) {
                    val message = "Invalid test class(es) detected." +
                            " See error log for details."
                    if (fatalValidation) {
                        throw RavenizerInvalidTestException(message)
                    } else {
                        log.w("Warning: $message")
                    }
                }
            }
        }
        if (includeUnsupportedMockito(processor.allClasses)) {
            log.w("Unsupported Mockito detected in $inJar!")
        }

        stats.totalProcessTime = log.vTime("$executableName processing $inJar") {
            ZipArchiveOutputStream(FileOutputStream(outJar).buffered()).use { outZip ->
                entries.asSequence().forEach { entry ->
                    stats.totalEntries++
                    if (entry.name.endsWith(".dex")) {
                        // Seems like it's an ART jar file. We can't process it.
                        // It's a fatal error.
                        throw GeneralUserErrorException(
                            "$inJar is not a desktop jar file. It contains a *.dex file."
                        )
                    }

                    if (stripMockito && entry.name.isMockitoFile()) {
                        // Skip this entry
                        return@forEach
                    }

                    val className = zipEntryNameToClassName(entry.name)

                    if (className != null) {
                        stats.totalClasses += 1
                    }

                    if (className != null &&
                        shouldProcessClass(processor.allClasses, className)) {
                        processSingleClass(this, entry, outZip, processor, stats)
                    } else {
                        stats.totalCopyTime += log.nTime {
                            copyZipEntry(this, entry, outZip)
                        }
                    }
                }
            }
        }
    }

    private fun processSingleClass(
        inZip: ZipFile,
        entry: ZipArchiveEntry,
        outZip: ZipArchiveOutputStream,
        processor: HostStubGenClassProcessor,
        stats: RavenizerStats,
    ) {
        stats.processedClasses += 1
        inZip.getInputStream(entry).use { zis ->
            var classBytes = zis.readAllBytes()
            stats.totalRavenizeTime += log.vTime("Ravenize ${entry.name}") {
                classBytes = ravenizeSingleClass(entry, classBytes, processor.allClasses)
            }
            stats.totalHostStubGenTime += log.vTime("HostStubGen ${entry.name}") {
                classBytes = processor.processClassBytecode(classBytes)
            }
            // TODO: if the class does not change, use copyZipEntry
            outZip.addBytesEntry(entry.name, classBytes)
        }
    }

    /**
     * Whether a class needs to be processed. This must be kept in sync with [processSingleClass].
     */
    private fun shouldProcessClass(classes: ClassNodes, classInternalName: String): Boolean {
        return !classInternalName.shouldBypass()
                && RunnerRewritingAdapter.shouldProcess(classes, classInternalName)
    }

    private fun ravenizeSingleClass(
        entry: ZipArchiveEntry,
        input: ByteArray,
        allClasses: ClassNodes,
    ): ByteArray {
        val classInternalName = zipEntryNameToClassName(entry.name)
            ?: throw RavenizerInternalException("Unexpected zip entry name: ${entry.name}")

        val flags = ClassWriter.COMPUTE_MAXS
        val cw = ClassWriter(flags)
        var outVisitor: ClassVisitor = cw

        val enableChecker = false
        if (enableChecker) {
            outVisitor = CheckClassAdapter(outVisitor)
        }

        // This must be kept in sync with shouldProcessClass.
        outVisitor = RunnerRewritingAdapter.maybeApply(
            classInternalName, allClasses, outVisitor)

        val cr = ClassReader(input)
        cr.accept(outVisitor, ClassReader.EXPAND_FRAMES)

        return cw.toByteArray()
    }
}
