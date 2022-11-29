/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrCompilation
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNpmResolutionManager
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmProject.Companion.PACKAGE_JSON
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.MayBeUpToDatePackageJsonTasksRegistry
import org.jetbrains.kotlin.gradle.utils.property
import java.io.File
import javax.inject.Inject

abstract class PublicPackageJsonTask
@Inject
constructor(
    @Transient
    private val compilation: KotlinJsCompilation
) : DefaultTask() {
    private val npmProject = compilation.npmProject

    @Transient
    private val nodeJs = npmProject.nodeJs
    private val resolutionManager = project.rootProject.kotlinNpmResolutionManager

    private val compilationName = compilation.disambiguatedName
    private val projectPath = project.path

    private val packageJsonHandlers = compilation.packageJsonHandlers

    @get:Internal
    internal abstract val mayBeUpToDateTasksRegistry: Property<MayBeUpToDatePackageJsonTasksRegistry>

    @get:Internal
    internal abstract val gradleNodeModules: Property<GradleNodeModulesCache>

    @get:Internal
    internal abstract val compositeNodeModules: Property<CompositeNodeModulesCache>

    @get:Input
    val packageJsonCustomFields: Map<String, Any?>
        get() = PackageJson(fakePackageJsonValue, fakePackageJsonValue)
            .apply {
                packageJsonHandlers.forEach { it() }
            }.customFields

    private val compilationResolver
        get() = resolutionManager.get().resolver.get()[projectPath][compilationName]

    private val confCompResolver
        get() = nodeJs.let {
            it.resolver[projectPath][compilationName]
        }

    private val compilationResolution
        get() = compilationResolver.getResolutionOrResolveIfForced(
            gradleNodeModules,
            compositeNodeModules,
            mayBeUpToDateTasksRegistry
        ) ?: error("Compilation resolution isn't available")

    @get:Input
    val externalDependencies: Collection<NpmDependencyDeclaration>
        get() = compilationResolution.externalNpmDependencies

    private val publicPackageJsonTaskName by lazy {
        npmProject.publicPackageJsonTaskName
    }

    private val defaultPackageJsonFile by lazy {
        project.buildDir
            .resolve("tmp")
            .resolve(publicPackageJsonTaskName)
            .resolve(PACKAGE_JSON)
    }

    @get:OutputFile
    var packageJsonFile: File by property { defaultPackageJsonFile }

    private val isJrIrCompilation = compilation is KotlinJsIrCompilation
    private val projectVersion = project.version.toString()

    @TaskAction
    fun resolve() {
        packageJson(npmProject.name, projectVersion, npmProject.main, externalDependencies, packageJsonHandlers).let { packageJson ->
            packageJson.main = "${npmProject.name}.js"

            if (isJrIrCompilation) {
                packageJson.types = "${npmProject.name}.d.ts"
            }

            packageJson.apply {
                listOf(
                    dependencies,
                    devDependencies,
                    peerDependencies,
                    optionalDependencies
                ).forEach { it.processDependencies() }
            }

            packageJson.saveTo(this@PublicPackageJsonTask.packageJsonFile)
        }
    }

    private fun MutableMap<String, String>.processDependencies() {
        filter { (_, version) ->
            version.isFileVersion()
        }.forEach { (key, _) ->
            remove(key)
        }
    }

    companion object {
        const val NAME = "publicPackageJson"
    }
}