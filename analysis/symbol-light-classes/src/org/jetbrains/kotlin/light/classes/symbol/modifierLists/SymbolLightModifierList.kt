/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol.modifierLists

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.asJava.classes.cannotModify
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.asJava.elements.KtLightAbstractAnnotation
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightElementBase
import org.jetbrains.kotlin.light.classes.symbol.invalidAccess
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner

internal abstract class SymbolLightModifierList<out T : KtLightElement<KtModifierListOwner, PsiModifierListOwner>>(
    protected val owner: T,
    private val staticModifiers: Set<String>,
    private val lazyModifiers: Lazy<Set<String>>?,
    annotationsComputer: (PsiModifierList) -> List<PsiAnnotation>,
) : KtLightElementBase(owner), PsiModifierList, KtLightElement<KtModifierList, PsiModifierListOwner> {
    override val kotlinOrigin: KtModifierList? get() = owner.kotlinOrigin?.modifierList
    override fun getParent() = owner
    override fun setModifierProperty(name: String, value: Boolean) = cannotModify()
    override fun checkSetModifierProperty(name: String, value: Boolean) = throw IncorrectOperationException()
    override fun addAnnotation(qualifiedName: String): PsiAnnotation = cannotModify()
    override fun getApplicableAnnotations(): Array<out PsiAnnotation> = annotations
    override fun isEquivalentTo(another: PsiElement?) = another is SymbolLightModifierList<*> && owner == another.owner
    override fun isWritable() = false
    override fun toString() = "Light modifier list of $owner"

    override val givenAnnotations: List<KtLightAbstractAnnotation> get() = invalidAccess()

    private val lazyAnnotations: Lazy<List<PsiAnnotation>> = lazyPub {
        annotationsComputer(this)
    }

    override fun getAnnotations(): Array<out PsiAnnotation> = lazyAnnotations.value.toTypedArray()
    override fun findAnnotation(qualifiedName: String): PsiAnnotation? =
        lazyAnnotations.value.firstOrNull { it.qualifiedName == qualifiedName }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = kotlinOrigin.hashCode()

    override fun hasExplicitModifier(name: String) = hasModifierProperty(name)
    override fun hasModifierProperty(name: String): Boolean = name in staticModifiers || lazyModifiers?.value?.contains(name) == true
}
