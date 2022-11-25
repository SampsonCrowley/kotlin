/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.Project
import org.jetbrains.kotlin.compilerRunner.KotlinNativeLibraryGenerationRunner
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider
import org.jetbrains.kotlin.gradle.tasks.addArg
import org.jetbrains.kotlin.gradle.utils.lifecycleWithDuration
import org.jetbrains.kotlin.konan.library.KONAN_PLATFORM_LIBS_NAME_PREFIX
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.customerDistribution
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class PlatformLibrariesGenerator(val project: Project, val konanTarget: KonanTarget) {

    private val distribution =
        customerDistribution(project.konanHome)

    private val platformLibsDirectory =
        File(distribution.platformLibs(konanTarget)).absoluteFile

    private val defDirectory =
        File(distribution.platformDefs(konanTarget)).absoluteFile

    private val mode: String? by lazy {
        PropertiesProvider(project).nativePlatformLibrariesMode
    }

    private val presentDefs: Set<String> by lazy {
        defDirectory
            .listFiles { file -> file.extension == "def" }.orEmpty()
            .map { it.nameWithoutExtension }.toSet()
    }

    private fun Set<String>.toPlatformLibNames(): Set<String> =
        mapTo(mutableSetOf()) { "$KONAN_PLATFORM_LIBS_NAME_PREFIX$it" }

    /**
     * Checks that all platform libs for [konanTarget] actually exist in the [distribution].
     */
    private fun checkLibrariesInDistribution(): Boolean {
        val presentPlatformLibs = platformLibsDirectory
            .listFiles { file -> file.isDirectory }.orEmpty()
            .map { it.name }.toSet()

        // TODO: Check that all directories in presentPlatformLibs are real klibs when klib componentization is merged.
        return presentDefs.toPlatformLibNames().all { it in presentPlatformLibs }
    }

    /**
     * We store directories where platform libraries were detected/generated earlier
     * during this build to avoid redundant distribution checks.
     */
    private val alreadyProcessed: PlatformLibsInfo
        get() = project.rootProject.extensions.extraProperties.run {
            if (!has(GENERATED_LIBS_PROPERTY_NAME)) {
                set(GENERATED_LIBS_PROPERTY_NAME, PlatformLibsInfo())
            }
            @Suppress("UNCHECKED_CAST")
            get(GENERATED_LIBS_PROPERTY_NAME) as PlatformLibsInfo
        }

    private fun runGenerationTool() = with(project) {
        val args = mutableListOf("-target", konanTarget.visibleName)
        if (logger.isInfoEnabled) {
            args.add("-verbose")
        }

        mode?.let {
            args.addArg("-mode", it)
        }

        KotlinNativeLibraryGenerationRunner.fromProject(this).run(args)
    }

    fun generatePlatformLibsIfNeeded(): Unit = with(project) {
        if (!HostManager(distribution).isEnabled(konanTarget)) {
            // We cannot generate libs on a machine that doesn't support the requested target.
            return
        }

        // Don't run the generator if libraries for this target were already built during this Gradle invocation.
        val alreadyGenerated = alreadyProcessed.isGenerated(platformLibsDirectory)
        if (alreadyGenerated || !defDirectory.exists()) {
            return
        }

        // Check if libraries for this target already exist (requires reading from disc).
        val platformLibsAreReady = checkLibrariesInDistribution()
        if (platformLibsAreReady) {
            alreadyProcessed.setGenerated(platformLibsDirectory)
            return
        }

        val generationMessage = "Generate platform libraries for $konanTarget"

        logger.lifecycle(generationMessage)
        logger.lifecycleWithDuration("$generationMessage finished,") {
            runGenerationTool()
        }

        val librariesAreActuallyGenerated = checkLibrariesInDistribution()
        assert(librariesAreActuallyGenerated) { "Some platform libraries were not generated" }
        if (librariesAreActuallyGenerated) {
            alreadyProcessed.setGenerated(platformLibsDirectory)
        }
    }

    private class PlatformLibsInfo {
        private val generated: MutableSet<File> = Collections.newSetFromMap(ConcurrentHashMap<File, Boolean>())

        /**
         * Are platform libraries in the given directory (e.g. <dist>/klib/platform/ios_x64) generated.
         */
        fun isGenerated(path: File): Boolean =
            generated.contains(path)

        /**
         * Register that platform libraries in the given directory are generated.
         */
        fun setGenerated(path: File) {
            generated.add(path)
        }
    }

    companion object {
        private const val GENERATED_LIBS_PROPERTY_NAME = "org.jetbrains.kotlin.native.platform.libs.info"
    }
}