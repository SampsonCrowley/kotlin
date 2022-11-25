/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.utils

internal fun ByteArray.toHexString() = joinToString("") { (0xFF and it.toInt()).toString(16).padStart(2, '0') }
