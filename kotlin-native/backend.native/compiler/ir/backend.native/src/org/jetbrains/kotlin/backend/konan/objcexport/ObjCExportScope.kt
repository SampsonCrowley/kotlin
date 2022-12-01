/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.objcexport

import org.jetbrains.kotlin.backend.konan.descriptors.isInterface
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.types.KotlinType

interface ObjCExportScope {
    val parent: ObjCExportScope?
        get() = null

    fun getGenericTypeUsage(typeParameterDescriptor: TypeParameterDescriptor?): ObjCGenericTypeUsage? =
            this.parent?.getGenericTypeUsage(typeParameterDescriptor)

    fun derive(kotlinType: KotlinType): ObjCExportScope = ObjCTypeExportScope(kotlinType = kotlinType, parent = this)
}

internal object ObjCNoneExportScope : ObjCExportScope {
    override fun getGenericTypeUsage(typeParameterDescriptor: TypeParameterDescriptor?): ObjCGenericTypeUsage? = null
}

internal class ObjCClassExportScope constructor(container: DeclarationDescriptor, val namer: ObjCExportNamer) : ObjCExportScope {
    private val typeParameterNames: List<TypeParameterDescriptor> =
            if (container is ClassDescriptor && !container.isInterface) {
                container.typeConstructor.parameters
            } else {
                emptyList<TypeParameterDescriptor>()
            }

    override fun getGenericTypeUsage(typeParameterDescriptor: TypeParameterDescriptor?): ObjCGenericTypeUsage? {
        return typeParameterNames.firstOrNull {
            typeParameterDescriptor != null &&
                    (it == typeParameterDescriptor || (it.isCapturedFromOuterDeclaration && it.original == typeParameterDescriptor))
        }?.let {
            ObjCGenericTypeParameterUsage(it, namer)
        }
    }
}

internal class ObjCTypeExportScope(val kotlinType: KotlinType, override val parent: ObjCExportScope?) : ObjCExportScope {
    class RecursionBreachException(type: KotlinType) : Exception("$type already encountered during type mapping process.")

    init {
        var parent = this.parent
        while (parent != null && parent is ObjCTypeExportScope) {
            if (parent.kotlinType == kotlinType)
                throw RecursionBreachException(kotlinType)
            parent = parent.parent
        }
    }
}