/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin.services

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.FirScriptConfiguratorExtension
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.builder.FirFileBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirScriptBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.Name
import kotlin.script.experimental.host.ScriptingHostConfiguration


class FirScriptConfiguratorExtensionImpl(
    session: FirSession,
    hostConfiguration: ScriptingHostConfiguration,
    compilerConfiguration: CompilerConfiguration
) : FirScriptConfiguratorExtension(session) {
    override fun FirScriptBuilder.configure(fileBuilder: FirFileBuilder) {
        val stringArrayTypeRef = buildResolvedTypeRef {
            type = session.builtinTypes.stringType.coneType
        }
        valueParameters.add(
            buildValueParameter {
                moduleData = session.moduleData
                origin = FirDeclarationOrigin.ScriptCustomization
                returnTypeRef = stringArrayTypeRef
                this.name = Name.identifier("args")
                this.symbol = FirValueParameterSymbol(this.name)
                isCrossinline = false
                isNoinline = false
                isVararg = true
            }
        )
    }

    companion object {
        fun getFactory(hostConfiguration: ScriptingHostConfiguration, compilerConfiguration: CompilerConfiguration): Factory {
            return Factory { session -> FirScriptConfiguratorExtensionImpl(session, hostConfiguration, compilerConfiguration) }
        }
    }

}
