/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.builder.FirScriptBuilder
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent

class FirScriptConfiguratorService(
    session: FirSession,
    val configure: (FirScriptBuilder) -> Unit
) : FirExtensionSessionComponent(session)

val FirSession.scriptConfiguratorService: FirScriptConfiguratorService? by FirSession.nullableSessionComponentAccessor()
