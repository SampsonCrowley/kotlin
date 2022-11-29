/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin.services

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider

class ScriptDefinitionProviderService(
    session: FirSession,
    provider: ScriptDefinitionProvider
) : FirExtensionSessionComponent(session), ScriptDefinitionProvider by provider {

    companion object {
        fun getFactory(provider: ScriptDefinitionProvider): Factory {
            return Factory { session -> ScriptDefinitionProviderService(session, provider) }
        }
    }
}

val FirSession.scriptDefinitionProviderService: ScriptDefinitionProviderService? by FirSession.nullableSessionComponentAccessor()
