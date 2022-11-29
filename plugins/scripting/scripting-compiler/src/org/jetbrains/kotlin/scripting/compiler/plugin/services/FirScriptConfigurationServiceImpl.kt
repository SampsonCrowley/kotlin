/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin.services

import org.jetbrains.kotlin.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.FirScriptConfiguratorExtension
import org.jetbrains.kotlin.fir.builder.FirScriptConfiguratorExtension.Factory
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.StringScriptSource


class FirScriptConfiguratorExtensionImpl(
    session: FirSession,
    hostConfiguration: ScriptingHostConfiguration,
    compilerConfiguration: CompilerConfiguration
) : FirScriptConfiguratorExtension(session) {
    @OptIn(SymbolInternals::class)
    override fun FirScriptBuilder.configure(fileBuilder: FirFileBuilder) {

        val definition = session.scriptDefinitionProviderService?.let { provider ->
            fileBuilder.sourceFile?.toSourceCode()?.let { script ->
                provider.findDefinition(script)
            } ?: provider.getDefaultDefinition()
        }

        if (definition != null) {

            definition.compilationConfiguration[ScriptCompilationConfiguration.baseClass]?.let { baseClass ->
                val baseClassFqn = FqName.fromSegments(baseClass.typeName.split("."))
                contextReceivers.add(buildContextReceiverWithFqName(baseClassFqn))

                val baseClassSymbol =
                    session.dependenciesSymbolProvider.getClassLikeSymbolByClassId(ClassId(baseClassFqn.parent(), baseClassFqn.shortName()))
                            as? FirRegularClassSymbol
                if (baseClassSymbol != null) {
                    // assuming that if base class will be unresolved, the error will be reported on the contextReceiver
                    baseClassSymbol.fir.primaryConstructorIfAny(session)?.fir?.valueParameters?.forEach {
                        valueParameters.add(buildValueParameterCopy(it) { origin = FirDeclarationOrigin.ScriptCustomization } )
                    }
                }
            }
        }
    }

    private fun buildContextReceiverWithFqName(baseClassFqn: FqName) =
        buildContextReceiver {
            typeRef = buildUserTypeRef {
                isMarkedNullable = false
                qualifier.addAll(
                    baseClassFqn.pathSegments().map {
                        FirQualifierPartImpl(null, it, FirTypeArgumentListImpl(null))
                    }
                )
            }
        }

    companion object {
        fun getFactory(hostConfiguration: ScriptingHostConfiguration, compilerConfiguration: CompilerConfiguration): Factory {
            return Factory { session -> FirScriptConfiguratorExtensionImpl(session, hostConfiguration, compilerConfiguration) }
        }
    }

}

fun KtSourceFile.toSourceCode(): SourceCode? = when (this) {
    is KtPsiSourceFile -> (psiFile as? KtFile)?.let(::KtFileScriptSource) ?: VirtualFileScriptSource(psiFile.virtualFile)
    is KtVirtualFileSourceFile -> VirtualFileScriptSource(virtualFile)
    is KtIoFileSourceFile -> FileScriptSource(file)
    is KtInMemoryTextSourceFile -> StringScriptSource(text.toString(), name)
    else -> null
}
