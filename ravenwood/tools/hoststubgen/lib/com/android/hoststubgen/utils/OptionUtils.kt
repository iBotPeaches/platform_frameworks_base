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
package com.android.hoststubgen.utils

import com.android.hoststubgen.ArgumentsException
import com.android.hoststubgen.InputFileNotFoundException
import com.android.hoststubgen.JarResourceNotFoundException
import com.android.hoststubgen.log
import com.android.hoststubgen.normalizeTextLine
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.io.Reader

const val JAR_RESOURCE_PREFIX = "jar:"

/**
 * Base class for parsing arguments from commandline.
 */
abstract class BaseOptions {
    /**
     * Parse all arguments.
     *
     * This method should remain final. For customization in subclasses, override [parseOption].
     */
    fun parseArgs(args: List<String>) {
        val ai = ArgIterator.withAtFiles(args)
        while (true) {
            val arg = ai.nextArgOptional() ?: break

            if (log.maybeHandleCommandLineArg(arg) { ai.nextArgRequired(arg) }) {
                continue
            }
            try {
                if (!parseOption(arg, ai)) {
                    throw ArgumentsException("Unknown option: $arg")
                }
            } catch (e: SetOnce.SetMoreThanOnceException) {
                throw ArgumentsException("Duplicate or conflicting argument found: $arg")
            }
        }

        checkArgs()
    }

    /**
     * Print out all fields in this class.
     *
     * This method should remain final. For customization in subclasses, override [dumpFields].
     */
    final override fun toString(): String {
        val fields = dumpFields().prependIndent("  ")
        return "${this::class.simpleName} {\n$fields\n}"
    }

    /**
     * Check whether the parsed options are in a correct state.
     *
     * This method is called as the last step in [parseArgs].
     */
    open fun checkArgs() {}

    /**
     * Parse a single option. Return true if the option is accepted, otherwise return false.
     *
     * Subclasses override/extend this method to support more options.
     */
    abstract fun parseOption(option: String, args: ArgIterator): Boolean

    abstract fun dumpFields(): String
}

class ArgIterator(
    private val args: List<String>,
    private var currentIndex: Int = -1
) {
    val current: String
        get() = args[currentIndex]

    /**
     * Get the next argument, or [null] if there's no more arguments.
     */
    fun nextArgOptional(): String? {
        if ((currentIndex + 1) >= args.size) {
            return null
        }
        return args[++currentIndex]
    }

    /**
     * Get the next argument, or throw if
     */
    fun nextArgRequired(argName: String): String {
        nextArgOptional().let {
            if (it == null) {
                throw ArgumentsException("Missing parameter for option $argName")
            }
            if (it.isEmpty()) {
                throw ArgumentsException("Parameter can't be empty for option $argName")
            }
            return it
        }
    }

    companion object {
        fun withAtFiles(args: List<String>): ArgIterator {
            val expanded = mutableListOf<String>()
            expandAtFiles(args.asSequence(), expanded)
            return ArgIterator(expanded)
        }

        /**
         * Scan the arguments, and if any of them starts with an `@`, then load from the file
         * and use its content as arguments.
         *
         * In order to pass an argument that starts with an '@', use '@@' instead.
         *
         * In this file, each line is treated as a single argument.
         *
         * The file can contain '#' as comments.
         */
        private fun expandAtFiles(args: Sequence<String>, out: MutableList<String>) {
            args.forEach { arg ->
                if (arg.startsWith("@@")) {
                    out.add(arg.substring(1))
                    return@forEach
                } else if (!arg.startsWith('@')) {
                    out.add(arg)
                    return@forEach
                }

                // Read from the file, and add each line to the result.
                val file = FileOrResource(arg.substring(1))

                log.v("Expanding options file ${file.path}")

                val fileArgs = file
                    .open()
                    .buffered()
                    .lineSequence()
                    .map(::normalizeTextLine)
                    .filter(CharSequence::isNotEmpty)

                expandAtFiles(fileArgs, out)
            }
        }
    }
}

/**
 * A single value that can only set once.
 */
open class SetOnce<T>(private var value: T) {
    class SetMoreThanOnceException : Exception()

    private var set = false

    fun set(v: T): T {
        if (set) {
            throw SetMoreThanOnceException()
        }
        if (v == null) {
            throw NullPointerException("This shouldn't happen")
        }
        set = true
        value = v
        return v
    }

    val get: T
        get() = this.value

    val isSet: Boolean
        get() = this.set

    fun <R> ifSet(block: (T & Any) -> R): R? {
        if (isSet) {
            return block(value!!)
        }
        return null
    }

    override fun toString(): String {
        return "$value"
    }
}

class IntSetOnce(value: Int) : SetOnce<Int>(value) {
    fun set(v: String): Int {
        try {
            return this.set(v.toInt())
        } catch (e: NumberFormatException) {
            throw ArgumentsException("Invalid integer $v")
        }
    }
}

/**
 * A path either points to a file in filesystem, or an entry in the JAR.
 */
class FileOrResource(val path: String) {
    init {
        path.ensureFileExists()
    }

    /**
     * Either read from filesystem, or read from JAR resources.
     */
    fun open(): Reader {
        return if (path.startsWith(JAR_RESOURCE_PREFIX)) {
            val path = path.removePrefix(JAR_RESOURCE_PREFIX)
            InputStreamReader(this::class.java.classLoader.getResourceAsStream(path)!!)
        } else {
            FileReader(path)
        }
    }
}

fun String.ensureFileExists(): String {
    if (this.startsWith(JAR_RESOURCE_PREFIX)) {
        val cl = FileOrResource::class.java.classLoader
        val path = this.removePrefix(JAR_RESOURCE_PREFIX)
        if (cl.getResource(path) == null) {
            throw JarResourceNotFoundException(path)
        }
    } else if (!File(this).exists()) {
        throw InputFileNotFoundException(this)
    }
    return this
}
