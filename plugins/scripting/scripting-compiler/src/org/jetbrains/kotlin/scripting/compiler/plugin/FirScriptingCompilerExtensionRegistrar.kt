/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.services.FirScriptConfiguratorExtensionImpl
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys
import kotlin.script.experimental.host.ScriptingHostConfiguration

class FirScriptingCompilerExtensionRegistrar(
    private val hostConfiguration: ScriptingHostConfiguration, private val compilerConfiguration: CompilerConfiguration
) : FirExtensionRegistrar() {

    override fun ExtensionRegistrarContext.configurePlugin() {
        if (!compilerConfiguration.getBoolean(ScriptingConfigurationKeys.DISABLE_SCRIPTING_PLUGIN_OPTION)) {
            +FirScriptConfiguratorExtensionImpl.getFactory(hostConfiguration, compilerConfiguration)
        }
    }
}