/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.ide

import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinBooleanExtrasSerializer
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializationExtension
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializer.Companion.javaIoSerializable
import org.jetbrains.kotlin.gradle.idea.tcs.extras.isCommonizedKey
import org.jetbrains.kotlin.gradle.idea.tcs.extras.isIdeaProjectLevelKey
import org.jetbrains.kotlin.gradle.idea.tcs.extras.isNativeDistributionKey
import org.jetbrains.kotlin.gradle.idea.tcs.extras.isNativeStdlibKey
import org.jetbrains.kotlin.gradle.kpm.idea.kotlinDebugKey

@InternalKotlinGradlePluginApi
val kotlinExtrasSerialization = IdeaKotlinExtrasSerializationExtension {
    register(kotlinDebugKey, javaIoSerializable())
    register(org.jetbrains.kotlin.gradle.idea.tcs.extras.KlibExtra.key, javaIoSerializable())
    register(isIdeaProjectLevelKey, IdeaKotlinBooleanExtrasSerializer)
    register(isNativeDistributionKey, IdeaKotlinBooleanExtrasSerializer)
    register(isNativeStdlibKey, IdeaKotlinBooleanExtrasSerializer)
    register(isCommonizedKey, IdeaKotlinBooleanExtrasSerializer)
}
