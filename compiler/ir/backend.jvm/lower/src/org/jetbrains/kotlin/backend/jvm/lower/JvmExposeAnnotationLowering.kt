/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils

val jvmExposeAnnotationPhase = makeIrFilePhase(
    ::JvmExposeAnnotationLowering,
    name = "JvmExpose declarations",
    description = "Lower declarations annotated with JvmExpose",
    prerequisite = setOf(jvmInlineClassPhase),
)

private class JvmExposeAnnotationLowering(private val context: JvmBackendContext) : ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        for (function in irClass.functions) {
//            if (!function.hasAnnotation(DescriptorUtils.JVM_EXPOSE)) continue

            val old = function.name
            function.name = Name.identifier(old.identifier + JvmAbi.IMPL_SUFFIX_FOR_MANGLED_MEMBERS)

            irClass.declarations.add(function.deepCopyWithSymbols().also {
                it.name = old
            })
        }
    }
}
