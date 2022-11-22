/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin

import org.jetbrains.kotlin.fir.builder.FirScriptConfiguratorService
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent

fun firScriptConfigurationServiceFactory(): FirExtensionSessionComponent.Factory {
    return FirExtensionSessionComponent.Factory { session ->
        FirScriptConfiguratorService(session) {
            println(1)
        }
    }
}
