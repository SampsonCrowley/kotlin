/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.metadata.jvm.deserialization

import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion

/**
 * The version of the metadata serialized by the compiler and deserialized by the compiler and reflection.
 * This version includes the version of the core protobuf messages (metadata.proto) as well as JVM extensions (jvm_metadata.proto).
 */
class JvmMetadataVersion(versionArray: IntArray, val isStrictSemantics: Boolean) : BinaryVersion(*versionArray) {
    constructor(vararg numbers: Int) : this(numbers, isStrictSemantics = false)

    fun isCompatibleWithDeployMetadataVersion(): Boolean {
        return isCompatibleInternal(INSTANCE_INC)
    }

    fun isCompatible(metadataVersionFromLanguageVersion: JvmMetadataVersion): Boolean {
        // * Compiler of deployVersion X (INSTANCE) with LV Y (metadataVersionFromLanguageVersion)
        //   * can read metadata with version <= max(X+1, Y)
        val limitVersion = maxOf(INSTANCE_INC, metadataVersionFromLanguageVersion)
        return isCompatibleInternal(limitVersion)
    }

    private fun isCompatibleInternal(limitVersion: JvmMetadataVersion): Boolean {
        // NOTE: 1.0 is a pre-Kotlin-1.0 metadata version, with which the current compiler is incompatible
        return (major != 1 || minor != 0) &&
                if (isStrictSemantics) {
                    isCompatibleTo(INSTANCE)
                } else {
                    this <= limitVersion
                }
    }

    private fun inc(): JvmMetadataVersion {
        if (minor < 9 || major > 1) return JvmMetadataVersion(major, minor + 1, patch)
        return JvmMetadataVersion(2, 0, patch)
    }

    companion object {
        @JvmField
        val INSTANCE = JvmMetadataVersion(1, 8, 0)

        private val INSTANCE_INC = INSTANCE.inc()

        @JvmField
        val INVALID_VERSION = JvmMetadataVersion()
    }
}
