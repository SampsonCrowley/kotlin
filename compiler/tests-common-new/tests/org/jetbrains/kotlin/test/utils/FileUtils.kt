/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.utils

import org.jetbrains.kotlin.test.directives.model.Directive
import java.io.File

private const val KT = ".kt"
private const val KTS = ".kts"

private const val FIR_PREFIX = ".fir"
private const val LL_FIR_PREFIX = ".ll.fir"

private const val FIR_KT = "$FIR_PREFIX$KT"
private const val FIR_KTS = "$FIR_PREFIX$KTS"

val File.isFirTestData: Boolean
    get() = isCustomTestData(FIR_PREFIX)

val File.originalTestDataFile: File
    get() = if (isFirTestData) {
        val originalTestDataFileName =
            if (name.endsWith(KTS)) "${name.removeSuffix(FIR_KTS)}$KTS"
            else "${name.removeSuffix(FIR_KT)}$KT"
        parentFile.resolve(originalTestDataFileName)
    } else {
        this
    }

val File.firTestDataFile: File
    get() = customTestDataFile(FIR_PREFIX)

/**
 * @see File.llFirTestDataFile
 */
val File.isLLFirTestData: Boolean
    get() = isCustomTestData(LL_FIR_PREFIX)

/**
 * An LL FIR test data file allows tailoring the expected output of a test to the LL FIR case. In very rare cases, LL FIR may legally
 * diverge from the output of the K2 compiler, such as when the compiler's error behavior is deliberately unspecified. (For an example, see
 * `kotlinJavaKotlinCycle.ll.fir.kt`.)
 */
val File.llFirTestDataFile: File
    get() = customTestDataFile(LL_FIR_PREFIX)

private fun File.isCustomTestData(prefix: String): Boolean =
    name.endsWith("$prefix$KT") || name.endsWith("$prefix$KTS")

private fun File.customTestDataFile(prefix: String): File {
    return if (isCustomTestData(prefix)) {
        this
    } else {
        val customTestDataFileName =
            if (name.endsWith(KTS)) "${name.removeSuffix(KTS)}$prefix$KTS"
            else "${name.removeSuffix(KT)}$prefix$KT"
        parentFile.resolve(customTestDataFileName)
    }
}

fun File.withExtension(extension: String): File {
    return withSuffixAndExtension(suffix = "", extension)
}

fun File.withSuffixAndExtension(suffix: String, extension: String): File {
    @Suppress("NAME_SHADOWING")
    val extension = extension.removePrefix(".")
    return parentFile.resolve("$nameWithoutExtension$suffix.$extension")
}

/*
 * Please use this method only in places where `TestModule` is not accessible
 * In other cases use testModule.directives
 */
fun File.isDirectiveDefined(directive: String): Boolean = this.useLines { line ->
    line.any { it == directive }
}

fun File.removeDirectiveFromFile(directive: Directive) {
    val directiveName = directive.name
    val directiveRegexp = "^// $directiveName(:.*)?$(\n)?".toRegex(RegexOption.MULTILINE)
    val text = readText()
    val directiveRange = directiveRegexp.find(text)?.range
        ?: error("Directive $directiveName was not found in $this")
    val textWithoutDirective = text.removeRange(directiveRange)
    writeText(textWithoutDirective)
}