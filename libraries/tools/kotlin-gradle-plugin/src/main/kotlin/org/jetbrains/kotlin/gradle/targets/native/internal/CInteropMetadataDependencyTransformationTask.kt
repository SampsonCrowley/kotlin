/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.commonizer.SharedCommonizerTarget
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.Companion.kotlinPropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.MetadataDependencyResolution.ChooseVisibleSourceSets
import org.jetbrains.kotlin.gradle.plugin.mpp.MetadataDependencyResolution.ChooseVisibleSourceSets.MetadataProvider.JarMetadataProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.MetadataDependencyResolution.ChooseVisibleSourceSets.MetadataProvider.ProjectMetadataProvider
import org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.sources.SourceSetMetadataStorageForIde
import org.jetbrains.kotlin.gradle.plugin.sources.resolveAllDependsOnSourceSets
import org.jetbrains.kotlin.gradle.tasks.dependsOn
import org.jetbrains.kotlin.gradle.tasks.locateOrRegisterTask
import org.jetbrains.kotlin.gradle.tasks.withType
import org.jetbrains.kotlin.gradle.utils.filesProvider
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import org.jetbrains.kotlin.gradle.utils.outputFilesProvider
import org.jetbrains.kotlin.library.KLIB_FILE_EXTENSION
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

internal fun Project.locateOrRegisterCInteropMetadataDependencyTransformationTask(
    sourceSet: DefaultKotlinSourceSet,
): TaskProvider<CInteropMetadataDependencyTransformationTask>? {
    if (!kotlinPropertiesProvider.enableCInteropCommonization) return null

    return locateOrRegisterTask(
        lowerCamelCaseName("transform", sourceSet.name, "CInteropDependenciesMetadata"),
        args = listOf(
            sourceSet,
            /* outputDirectory = */
            project.buildDir.resolve("kotlinSourceSetMetadata").resolve(sourceSet.name + "-cinterop")
        ),
        configureTask = { configureTaskOrder(); onlyIfSourceSetIsSharedNative() }
    )
}

internal fun Project.locateOrRegisterCInteropMetadataDependencyTransformationTaskForIde(
    sourceSet: DefaultKotlinSourceSet,
): TaskProvider<CInteropMetadataDependencyTransformationTask>? {
    if (!kotlinPropertiesProvider.enableCInteropCommonization) return null

    return locateOrRegisterTask(
        lowerCamelCaseName("transform", sourceSet.name, "CInteropDependenciesMetadataForIde"),
        invokeWhenRegistered = { commonizeTask.dependsOn(this) },
        args = listOf(
            sourceSet,
            /* outputDirectory = */
            SourceSetMetadataStorageForIde.sourceSetStorage(project, sourceSet.name).resolve("cinterop")
        ),
        configureTask = { configureTaskOrder(); onlyIfSourceSetIsSharedNative() }
    )
}

private fun CInteropMetadataDependencyTransformationTask.configureTaskOrder() {
    val dependsOnTasks = Callable {
        val allVisibleSourceSets = sourceSet.resolveAllDependsOnSourceSets() + sourceSet.getAdditionalVisibleSourceSets()
        project.tasks.withType<CInteropMetadataDependencyTransformationTask>().matching { it.sourceSet in allVisibleSourceSets }
    }
    mustRunAfter(dependsOnTasks)
}

private fun CInteropMetadataDependencyTransformationTask.onlyIfSourceSetIsSharedNative() {
    onlyIf { project.getCommonizerTarget(sourceSet) is SharedCommonizerTarget }
}

internal open class CInteropMetadataDependencyTransformationTask @Inject constructor(
    @get:Internal val sourceSet: DefaultKotlinSourceSet,
    @get:OutputDirectory val outputDirectory: File
) : DefaultTask() {

    @Suppress("unused")
    @get:InputFiles
    @get:Classpath
    protected val inputArtifactFiles: FileCollection = project.filesProvider {
        sourceSet.dependencyTransformations.values.map { it.configurationToResolve.withoutProjectDependencies() }
    }

    @get:Internal
    protected val chooseVisibleSourceSets
        get() = sourceSet.dependencyTransformations.values
            .flatMap { it.metadataDependencyResolutions }
            .filterIsInstance<ChooseVisibleSourceSets>()

    @Suppress("unused")
    @get:Input
    protected val dependencyProjectStructureMetadata
        get() = chooseVisibleSourceSets.map { it.projectStructureMetadata }

    @get:Internal
    val outputLibraryFiles = outputFilesProvider {
        outputDirectory.walkTopDown().maxDepth(2).filter { it.isFile && it.extension == KLIB_FILE_EXTENSION }.toList()
    }

    @TaskAction
    protected fun transformDependencies() {
        if (outputDirectory.isDirectory) {
            outputDirectory.deleteRecursively()
        }

        if (project.getCommonizerTarget(sourceSet) !is SharedCommonizerTarget) return
        chooseVisibleSourceSets.flatMap(::materializeMetadata)
    }

    private fun materializeMetadata(
        chooseVisibleSourceSets: ChooseVisibleSourceSets
    ): Set<File> = when (chooseVisibleSourceSets.metadataProvider) {
        /* Nothing to transform: We will use original commonizer output in such cases */
        is ProjectMetadataProvider -> emptySet()

        /* Extract/Materialize all cinterop files from composite jar file */
        is JarMetadataProvider ->
            chooseVisibleSourceSets.visibleSourceSetsProvidingCInterops.flatMap { visibleSourceSetName ->
                chooseVisibleSourceSets.metadataProvider.getSourceSetCInteropMetadata(
                    visibleSourceSetName, outputDirectory, materializeFiles = true
                )
            }.toSet()
    }

    private fun Configuration.withoutProjectDependencies(): FileCollection {
        return incoming.artifactView { view ->
            view.componentFilter { componentIdentifier ->
                componentIdentifier !is ProjectComponentIdentifier
            }
        }.files
    }
}
