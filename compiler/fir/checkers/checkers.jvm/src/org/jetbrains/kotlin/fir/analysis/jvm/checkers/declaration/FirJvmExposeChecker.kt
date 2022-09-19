/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.jvm.checkers.declaration

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.analysis.diagnostics.jvm.FirJvmErrors
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.isOpen
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds

// TODO incomplete
object FirJvmExposeChecker : FirBasicDeclarationChecker() {
    override fun check(declaration: FirDeclaration, context: CheckerContext, reporter: DiagnosticReporter) {
        val jvmExpose = declaration.findAnnotation(StandardClassIds.Annotations.JvmExpose)
        jvmExpose?.let { context.checkJvmExpose(it, declaration, reporter) }
    }

    private fun CheckerContext.checkJvmExpose(jvmExpose: FirAnnotation, declaration: FirDeclaration, reporter: DiagnosticReporter) {
        when {
            declaration is FirMemberDeclaration && Visibilities.isPrivate(declaration.visibility) ||
                    declaration.findAnnotation(StandardClassIds.Annotations.JvmSynthetic) != null ->
                reporter.reportOn(jvmExpose.source, FirJvmErrors.INAPPLICABLE_JVM_EXPOSE, this)

            declaration is FirFunction && !isRenamableFunction(declaration) ->
                reporter.reportOn(jvmExpose.source, FirJvmErrors.INAPPLICABLE_JVM_EXPOSE, this)

            declaration is FirCallableDeclaration && (declaration.isOverride || declaration.isOpen) ->
                reporter.reportOn(jvmExpose.source, FirJvmErrors.INAPPLICABLE_JVM_EXPOSE, this)
        }
    }

    private fun FirDeclaration.findAnnotation(id: ClassId): FirAnnotation? = annotations.find {
        it.annotationTypeRef.coneType.classId == id
    }

    /**
     * Local functions can't be renamed as well as functions not present in any class, like intrinsics.
     */
    private fun CheckerContext.isRenamableFunction(function: FirFunction): Boolean {
        val containingClass = function.getContainingClassSymbol(session)
        return containingClass != null || !function.symbol.callableId.isLocal
    }
}
