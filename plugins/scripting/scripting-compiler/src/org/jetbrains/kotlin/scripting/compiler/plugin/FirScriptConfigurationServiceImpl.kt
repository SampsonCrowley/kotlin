/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin

import org.jetbrains.kotlin.fir.builder.FirScriptConfiguratorService
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.Name

fun firScriptConfigurationServiceFactory(): FirExtensionSessionComponent.Factory {
    return FirExtensionSessionComponent.Factory { session ->
        val stringArrayTypeRef = buildResolvedTypeRef {
            type = session.builtinTypes.stringType.coneType
        }
        FirScriptConfiguratorService(session) {
            it.valueParameters.add(
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
    }
}
