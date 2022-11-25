/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.backend.konan

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.serialization.codedInputStream
import org.jetbrains.kotlin.backend.common.serialization.proto.IrFile
import org.jetbrains.kotlin.backend.konan.driver.CompilerDriver
import org.jetbrains.kotlin.backend.konan.driver.DynamicCompilerDriver
import org.jetbrains.kotlin.backend.konan.driver.StaticCompilerDriver
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.library.impl.createKonanLibrary
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.resolver.TopologicalLibraryOrder
import org.jetbrains.kotlin.library.uniqueName
import org.jetbrains.kotlin.library.unresolvedDependencies
import org.jetbrains.kotlin.protobuf.ExtensionRegistryLite
import org.jetbrains.kotlin.backend.konan.descriptors.isInteropLibrary
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity

class KonanDriver(
        val project: Project,
        val environment: KotlinCoreEnvironment,
        val configuration: CompilerConfiguration,
        val spawnCompilation: (List<String>) -> Unit
) {
    fun run() {
        val fileNames = configuration.get(KonanConfigKeys.LIBRARY_TO_ADD_TO_CACHE)?.let { libPath ->
            if (configuration.get(KonanConfigKeys.MAKE_PER_FILE_CACHE) != true)
                configuration.get(KonanConfigKeys.FILES_TO_CACHE)
            else {
                val lib = createKonanLibrary(File(libPath), "default", null, true)
                (0 until lib.fileCount()).map { fileIndex ->
                    val proto = IrFile.parseFrom(lib.file(fileIndex).codedInputStream, ExtensionRegistryLite.newInstance())
                    proto.fileEntry.name
                }
            }
        }
        if (fileNames != null) {
            configuration.put(KonanConfigKeys.MAKE_PER_FILE_CACHE, true)
            configuration.put(KonanConfigKeys.FILES_TO_CACHE, fileNames)
        }

        var konanConfig = KonanConfig(project, configuration)
        ensureModuleName(konanConfig)

        val autoCacheableFrom = configuration.get(KonanConfigKeys.AUTO_CACHEABLE_FROM)!!.map { File(it) }

        if (konanConfig.isFinalBinary
                && !konanConfig.optimizationsEnabled
                && autoCacheableFrom.isNotEmpty()
        ) {
            val allLibraries = konanConfig.resolvedLibraries.getFullList(TopologicalLibraryOrder)
            val uniqueNameToLibrary = allLibraries.associateBy { it.uniqueName }
            val caches = mutableMapOf<KotlinLibrary, String>()

            allLibraries.forEach { library ->
                konanConfig.cachedLibraries.getLibraryCache(library)?.let {
                    caches[library] = it.rootDirectory
                    return@forEach
                }
                val libraryPath = library.libraryFile.absolutePath
                val isLibraryAutoCacheable = library.isDefault || autoCacheableFrom.any { libraryPath.startsWith(it.absolutePath) }
                if (!isLibraryAutoCacheable)
                    return@forEach

                val dependencies = library.unresolvedDependencies.map { uniqueNameToLibrary[it.path]!! }
                val dependencyCaches = dependencies.map { caches[it] }
                if (dependencyCaches.any { it == null }) {
                    configuration.report(CompilerMessageSeverity.LOGGING, "SKIPPING ${library.libraryName} as some of the dependencies aren't cached")
                    return@forEach
                }

                val makePerFileCache = !library.isInteropLibrary()
                configuration.report(CompilerMessageSeverity.LOGGING, "CACHING ${library.libraryName}")
                val libraryCacheDirectory = if (library.isDefault)
                    konanConfig.systemCacheDirectory
                else
                    CachedLibraries.computeVersionedCacheDirectory(konanConfig.systemCacheDirectory, library, allLibraries)
                val args = buildList {
                    add("-target")
                    add(konanConfig.target.toString())
                    add("-p")
                    add("static_cache")
                    if (konanConfig.debug)
                        add("-g")
                    addAll(konanConfig.additionalCacheFlags)
                    if (konanConfig.partialLinkage)
                        add("-Xpartial-linkage")
                    konanConfig.configuration.get(KonanConfigKeys.EXTERNAL_DEPENDENCIES)?.let {
                        add("-Xexternal-dependencies=$it")
                    }

                    add("-Xadd-cache=$libraryPath")
                    dependencies.forEach { dependency ->
                        if (!dependency.isDefault) {
                            add("-l")
                            add(dependency.libraryFile.absolutePath)
                        }
                    }
                    dependencies.forEachIndexed { index, dependency ->
                        val dependencyCache = dependencyCaches[index]
                        add("-Xcached-library=${dependency.libraryFile.absolutePath},$dependencyCache")
                    }
                    add("-Xcache-directory=${libraryCacheDirectory.absolutePath}")
                    if (makePerFileCache)
                        add("-Xmake-per-file-cache")
                }
                args.forEach { configuration.report(CompilerMessageSeverity.LOGGING, "    $it") }

                val libraryCache = libraryCacheDirectory.child(
                        if (makePerFileCache)
                            CachedLibraries.getPerFileCachedLibraryName(library)
                        else
                            CachedLibraries.getCachedLibraryName(library)
                )
                try {
                    // TODO: Run monolithic cache builds in parallel.
                    libraryCacheDirectory.mkdirs()
                    spawnCompilation(args)
                    caches[library] = libraryCache.absolutePath
                } catch (t: Throwable) {
                    configuration.report(CompilerMessageSeverity.WARNING,
                            ("e: ${t.message}\n${t.stackTraceToString()}\nFalling back to not use cache for ${library.libraryName}"))

                    libraryCache.deleteRecursively()
                }
            }

            konanConfig = KonanConfig(project, configuration) // TODO: Just set freshly built caches.
        }

        pickCompilerDriver(konanConfig).run(konanConfig, environment)
    }

    private fun ensureModuleName(config: KonanConfig) {
        if (environment.getSourceFiles().isEmpty()) {
            val libraries = config.resolvedLibraries.getFullList()
            val moduleName = config.moduleId
            if (libraries.any { it.uniqueName == moduleName }) {
                val kexeModuleName = "${moduleName}_kexe"
                config.configuration.put(KonanConfigKeys.MODULE_NAME, kexeModuleName)
                assert(libraries.none { it.uniqueName == kexeModuleName })
            }
        }
    }

    private fun pickCompilerDriver(config: KonanConfig): CompilerDriver {
        config.configuration[KonanConfigKeys.FORCE_COMPILER_DRIVER]?.let {
            return when (it) {
                "dynamic" -> DynamicCompilerDriver()
                "static" -> StaticCompilerDriver()
                else -> error("Unknown compiler driver. Possible values: dynamic, static")
            }
        }
        // Dynamic driver is WIP, so it might not support all possible configurations.
        return if (DynamicCompilerDriver.supportsConfig()) {
            DynamicCompilerDriver()
        } else {
            StaticCompilerDriver()
        }
    }
}