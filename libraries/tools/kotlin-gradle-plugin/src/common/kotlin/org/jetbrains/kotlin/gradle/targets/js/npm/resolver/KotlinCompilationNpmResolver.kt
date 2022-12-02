/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.resolver

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Zip
import org.gradle.work.NormalizeLineEndings
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.categoryByName
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.plugin.mpp.disambiguateName
import org.jetbrains.kotlin.gradle.plugin.mpp.isMain
import org.jetbrains.kotlin.gradle.plugin.sources.KotlinDependencyScope
import org.jetbrains.kotlin.gradle.plugin.sources.compilationDependencyConfigurationByScope
import org.jetbrains.kotlin.gradle.plugin.sources.sourceSetDependencyConfigurationByScope
import org.jetbrains.kotlin.gradle.plugin.usesPlatformOf
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.Companion.kotlinNpmResolutionManager
import org.jetbrains.kotlin.gradle.targets.js.npm.*
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmProject.Companion.PACKAGE_JSON
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinCompilationNpmResolution
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinPackageJsonTask
import org.jetbrains.kotlin.gradle.tasks.registerTask
import org.jetbrains.kotlin.gradle.utils.CompositeProjectComponentArtifactMetadata
import org.jetbrains.kotlin.gradle.utils.`is`
import org.jetbrains.kotlin.gradle.utils.topRealPath
import java.io.File
import java.io.Serializable

/**
 * See [KotlinNpmResolutionManager] for details about resolution process.
 */
internal class KotlinCompilationNpmResolver(
//    @Transient
    val projectResolver: KotlinProjectNpmResolver,
    @Transient
    val compilation: KotlinJsCompilation
) : Serializable {
    //    @Transient
    var rootResolver = projectResolver.resolver

    val npmProject = compilation.npmProject

    val compilationDisambiguatedName = compilation.disambiguatedName

//    val packageJsonHandlers by lazy {
//        compilation.packageJsonHandlers
//    }

    val npmVersion by lazy {
        project.version.toString()
    }

//    val nodeJs get() = rootResolver.nodeJs
//    private val nodeJs_ get() = nodeJs ?: unavailableValueError("nodeJs")

    val target get() = compilation.target

    val project get() = target.project

    val projectPath: String = project.path

    @Transient
    val packageJsonTaskHolder: TaskProvider<KotlinPackageJsonTask>? =
        KotlinPackageJsonTask.create(compilation)

    @Transient
    val publicPackageJsonTaskHolder: TaskProvider<PublicPackageJsonTask> =
        project.registerTask<PublicPackageJsonTask>(
            npmProject.publicPackageJsonTaskName,
            listOf(compilation)
        ) {
            it.dependsOn(packageJsonTaskHolder)
            it.usesService(project.kotlinNpmResolutionManager)

            it.mayBeUpToDateTasksRegistry.set(
                MayBeUpToDatePackageJsonTasksRegistry.registerIfAbsent(project)
            )

            it.gradleNodeModules.set(
                project.gradle.sharedServices.registerIfAbsent("gradle-node-modules", GradleNodeModulesCache::class.java) {
                    error("must be already registered")
                }
            )

            it.compositeNodeModules.set(
                project.gradle.sharedServices.registerIfAbsent("composite-node-modules", CompositeNodeModulesCache::class.java) {
                    error("must be already registered")
                }
            )
        }.also { packageJsonTask ->
            if (compilation.isMain()) {
                project.tasks
                    .withType(Zip::class.java)
                    .named(npmProject.target.artifactsTaskName)
                    .configure {
                        it.dependsOn(packageJsonTask)
                    }
            }
        }

    override fun toString(): String = "KotlinCompilationNpmResolver(${npmProject.name})"

    var packageJsonProducer: PackageJsonProducer? = null

    val _packageJsonProducer: PackageJsonProducer
        get() {
            return packageJsonProducer ?: run {
                val visitor = ConfigurationVisitor()
                visitor.visit(createAggregatedConfiguration())
                visitor.toPackageJsonProducer()
                    .also { packageJsonProducer = it }
                /*.also { it.compilationResolver = this }*/
            }
        }

//    val packageJsonProducer: PackageJsonProducer
//        get() {
//            val packageJsonProducer = packageJsonProducer_
//            packageJsonProducer.compilationResolver = this
//            return packageJsonProducer
//        }

    private var closed = false
    private var resolution: KotlinCompilationNpmResolution? = null

    @Synchronized
    fun resolve(
        skipWriting: Boolean = false,
        packageJsonProducer: PackageJsonProducer,
        npmResolutionManager: KotlinNpmResolutionManager
    ): KotlinCompilationNpmResolution {
        check(!closed) { "$this already closed" }
        check(resolution == null) { "$this already resolved" }

        return packageJsonProducer.createPackageJson(
            skipWriting,
            npmResolutionManager
        ).also {
            resolution = it
        }
    }

    @Synchronized
    fun getResolutionOrResolve(
        npmResolutionManager: KotlinNpmResolutionManager,
        packageJsonProducer: PackageJsonProducer
    ): KotlinCompilationNpmResolution {

        return resolution ?: resolve(
            skipWriting = true,
            packageJsonProducer,
            npmResolutionManager
        )
    }

    @Synchronized
    fun close(
        npmResolutionManager: KotlinNpmResolutionManager
    ): KotlinCompilationNpmResolution {
        check(!closed) { "$this already closed" }
        val resolution = resolution!! /* getResolutionOrResolve(npmResolutionManager) */
        closed = true
        return resolution
    }

    fun createAggregatedConfiguration(): Configuration {
        val all = project.configurations.create(compilation.disambiguateName("npm"))

        all.usesPlatformOf(target)
        all.attributes.attribute(Usage.USAGE_ATTRIBUTE, KotlinUsages.consumerRuntimeUsage(target))
        all.attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.categoryByName(Category.LIBRARY))
        all.isVisible = false
        all.isCanBeConsumed = false
        all.isCanBeResolved = true
        all.description = "NPM configuration for $compilation."

        KotlinDependencyScope.values().forEach { scope ->
            val compilationConfiguration = project.compilationDependencyConfigurationByScope(
                compilation,
                scope
            )
            all.extendsFrom(compilationConfiguration)
            compilation.allKotlinSourceSets.forEach { sourceSet ->
                val sourceSetConfiguration = project.configurations.sourceSetDependencyConfigurationByScope(sourceSet, scope)
                all.extendsFrom(sourceSetConfiguration)
            }
        }

        // We don't have `kotlin-js-test-runner` in NPM yet
        all.dependencies.add(rootResolver.versions.kotlinJsTestRunner.createDependency(project))

        return all
    }

    data class ExternalGradleDependency(
        val dependency: ResolvedDependency,
        val artifact: ResolvedArtifact
    ) : Serializable

    data class FileCollectionExternalGradleDependency(
        val files: Collection<File>,
        val dependencyVersion: String?
    ) : Serializable

    data class FileExternalGradleDependency(
        val dependencyName: String,
        val dependencyVersion: String,
        val file: File
    ) : Serializable

    data class CompositeDependency(
        val dependencyName: String,
        val dependencyVersion: String,
        val includedBuildDir: File,
        @Transient
        val includedBuild: IncludedBuild?
    ) : Serializable

    data class InternalDependency(
        val projectPath: String,
        val compilationName: String,
        val projectName: String
    ) : Serializable

    inner class ConfigurationVisitor {
        private val internalDependencies = mutableSetOf<InternalDependency>()
        private val internalCompositeDependencies = mutableSetOf<CompositeDependency>()
        private val externalGradleDependencies = mutableSetOf<ExternalGradleDependency>()
        private val externalNpmDependencies = mutableSetOf<NpmDependencyDeclaration>()
        private val fileCollectionDependencies = mutableSetOf<FileCollectionExternalGradleDependency>()

        private val visitedDependencies = mutableSetOf<ResolvedDependency>()

        fun visit(configuration: Configuration) {
            configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach {
                visitDependency(it)
            }

            configuration.allDependencies.forEach { dependency ->
                when (dependency) {
                    is NpmDependency -> externalNpmDependencies.add(dependency.toDeclaration())
                    is FileCollectionDependency -> fileCollectionDependencies.add(
                        FileCollectionExternalGradleDependency(
                            dependency.files.files,
                            dependency.version
                        )
                    )
                }
            }

            //TODO: rewrite when we get general way to have inter compilation dependencies
            if (compilation.name == KotlinCompilation.TEST_COMPILATION_NAME) {
                val main = compilation.target.compilations.findByName(KotlinCompilation.MAIN_COMPILATION_NAME) as KotlinJsCompilation
                internalDependencies.add(
                    InternalDependency(
                        projectResolver.projectPath,
                        main.disambiguatedName,
                        projectResolver[main].npmProject.name
                    )
                )
            }

            val hasPublicNpmDependencies = externalNpmDependencies.isNotEmpty()

            if (compilation.isMain() && hasPublicNpmDependencies) {
                project.tasks
                    .withType(Zip::class.java)
                    .named(npmProject.target.artifactsTaskName)
                    .configure { task ->
                        task.from(publicPackageJsonTaskHolder)
                    }
            }
        }

        private fun visitDependency(dependency: ResolvedDependency) {
            if (dependency in visitedDependencies) return
            visitedDependencies.add(dependency)
            visitArtifacts(dependency, dependency.moduleArtifacts)

            dependency.children.forEach {
                visitDependency(it)
            }
        }

        private fun visitArtifacts(
            dependency: ResolvedDependency,
            artifacts: MutableSet<ResolvedArtifact>
        ) {
            artifacts.forEach { visitArtifact(dependency, it) }
        }

        private fun visitArtifact(
            dependency: ResolvedDependency,
            artifact: ResolvedArtifact
        ) {
            val artifactId = artifact.id
            val componentIdentifier = artifactId.componentIdentifier

            if (artifactId `is` CompositeProjectComponentArtifactMetadata) {
                visitCompositeProjectDependency(dependency, componentIdentifier as ProjectComponentIdentifier)
                return
            }

            if (componentIdentifier is ProjectComponentIdentifier) {
                visitProjectDependency(componentIdentifier)
                return
            }

            externalGradleDependencies.add(ExternalGradleDependency(dependency, artifact))
        }

        private fun visitCompositeProjectDependency(
            dependency: ResolvedDependency,
            componentIdentifier: ProjectComponentIdentifier
        ) {
            check(target is KotlinJsIrTarget) {
                """
                Composite builds for Kotlin/JS are supported only for IR compiler.
                Use kotlin.js.compiler=ir in gradle.properties or
                js(IR) {
                ...
                }
                """.trimIndent()
            }

            (componentIdentifier as DefaultProjectComponentIdentifier).let { identifier ->
                val includedBuild = project.gradle.includedBuild(identifier.identityPath.topRealPath().name!!)
                internalCompositeDependencies.add(
                    CompositeDependency(dependency.moduleName, dependency.moduleVersion, includedBuild.projectDir, includedBuild)
                )
            }
        }

        private fun visitProjectDependency(
            componentIdentifier: ProjectComponentIdentifier
        ) {
            val dependentProject = project.findProject(componentIdentifier.projectPath)
                ?: error("Cannot find project ${componentIdentifier.projectPath}")

            rootResolver.findDependentResolver(project, dependentProject)
                ?.forEach { dependentResolver ->
                    internalDependencies.add(
                        InternalDependency(
                            dependentResolver.projectPath,
                            dependentResolver.compilationDisambiguatedName,
                            dependentResolver.npmProject.name
                        )
                    )
                }
        }

        fun toPackageJsonProducer() = PackageJsonProducer(
            internalDependencies,
            internalCompositeDependencies,
            externalGradleDependencies.map {
                FileExternalGradleDependency(
                    it.dependency.moduleName,
                    it.dependency.moduleVersion,
                    it.artifact.file
                )
            },
            externalNpmDependencies,
            fileCollectionDependencies,
            projectPath,
            this@KotlinCompilationNpmResolver
        )
    }

    @Suppress("unused")
    class PackageJsonProducerInputs(
        @get:Input
        val internalDependencies: Collection<String>,

        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        @get:IgnoreEmptyDirectories
        @get:NormalizeLineEndings
        @get:InputFiles
        val internalCompositeDependencies: Collection<File>,

        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        @get:IgnoreEmptyDirectories
        @get:NormalizeLineEndings
        @get:InputFiles
        val externalGradleDependencies: Collection<File>,

        @get:Input
        val externalDependencies: Collection<String>,

        @get:Input
        val fileCollectionDependencies: Collection<File>
    )

    @Suppress("MemberVisibilityCanBePrivate")
    class PackageJsonProducer(
        var internalDependencies: Collection<InternalDependency>,
        var internalCompositeDependencies: Collection<CompositeDependency>,
        var externalGradleDependencies: Collection<FileExternalGradleDependency>,
        var externalNpmDependencies: Collection<NpmDependencyDeclaration>,
        var fileCollectionDependencies: Collection<FileCollectionExternalGradleDependency>,
        val projectPath: String,
        val compilationResolver: KotlinCompilationNpmResolver
    ) : Serializable {
        private val projectPackagesDir by lazy { compilationResolver.rootResolver.projectPackagesDir }
        private val rootDir by lazy { compilationResolver.rootResolver.rootProjectDir }

//        @Transient
//        internal lateinit var compilationResolver: KotlinCompilationNpmResolver

        val inputs: PackageJsonProducerInputs
            get() = PackageJsonProducerInputs(
                internalDependencies.map { it.projectName },
                internalCompositeDependencies.flatMap { it.getPackages() },
                externalGradleDependencies.map { it.file },
                externalNpmDependencies.map { it.uniqueRepresentation() },
                fileCollectionDependencies.flatMap { it.files }
            )

        fun createPackageJson(
            skipWriting: Boolean,
            npmResolutionManager: KotlinNpmResolutionManager
        ): KotlinCompilationNpmResolution {
            internalDependencies.map {
                val kotlinCompilationNpmResolver = compilationResolver.rootResolver[it.projectPath][it.compilationName]
                kotlinCompilationNpmResolver.getResolutionOrResolve(
                    npmResolutionManager,
                    kotlinCompilationNpmResolver._packageJsonProducer
                ) ?: error("Unresolved dependent npm package: ${compilationResolver} -> $it")
            }
            val importedExternalGradleDependencies = externalGradleDependencies.mapNotNull {
                npmResolutionManager.parameters.gradleNodeModulesProvider.get().get(it.dependencyName, it.dependencyVersion, it.file)
            } + fileCollectionDependencies.flatMap { dependency ->
                dependency.files
                    // Gradle can hash with FileHasher only files and only existed files
                    .filter { it.isFile }
                    .map { file ->
                        npmResolutionManager.parameters.gradleNodeModulesProvider.get().get(
                            file.name,
                            dependency.dependencyVersion ?: "0.0.1",
                            file
                        )
                    }
            }.filterNotNull()
            val transitiveNpmDependencies = importedExternalGradleDependencies.flatMap {
                it.dependencies
            }.filter { it.scope != NpmDependency.Scope.DEV }

            val compositeDependencies = internalCompositeDependencies.flatMap { dependency ->
                dependency.getPackages()
                    .map { file ->
                        npmResolutionManager.parameters.compositeNodeModulesProvider.get().get(
                            dependency.dependencyName,
                            dependency.dependencyVersion,
                            file
                        )
                    }
            }.filterNotNull()

            val toolsNpmDependencies = compilationResolver.rootResolver.tasksRequirements
                .getCompilationNpmRequirements(projectPath, compilationResolver.compilationDisambiguatedName)

            val otherNpmDependencies = toolsNpmDependencies + transitiveNpmDependencies
            val allNpmDependencies = disambiguateDependencies(externalNpmDependencies, otherNpmDependencies)
            val packageJsonHandlers =
                npmResolutionManager.parameters.packageJsonHandlers.get()["$projectPath:${compilationResolver.compilationDisambiguatedName}"]
                    ?: emptyList() /*if (compilationResolver.compilation != null) {
                compilationResolver.compilation.packageJsonHandlers
            } else {
                compilationResolver.rootResolver.getPackageJsonHandlers(projectPath, compilationResolver.compilationDisambiguatedName)
            }*/

            val packageJson = packageJson(
                compilationResolver.npmProject.name,
                compilationResolver.npmVersion,
                compilationResolver.npmProject.main,
                allNpmDependencies,
                packageJsonHandlers
            )

            compositeDependencies.forEach {
                packageJson.dependencies[it.name] = it.version
            }

            packageJsonHandlers.forEach {
                it(packageJson)
            }

            if (!skipWriting) {
                packageJson.saveTo(compilationResolver.npmProject.packageJsonFile)
            }

            return KotlinCompilationNpmResolution(
                compilationResolver.npmProject,
                compositeDependencies,
                importedExternalGradleDependencies,
                allNpmDependencies,
                packageJson
            )
        }

        private fun disambiguateDependencies(
            direct: Collection<NpmDependencyDeclaration>,
            others: Collection<NpmDependencyDeclaration>,
        ): Collection<NpmDependencyDeclaration> {
            val unique = others.groupBy(NpmDependencyDeclaration::name)
                .filterKeys { k -> direct.none { it.name == k } }
                .mapNotNull { (name, dependencies) ->
                    dependencies.maxByOrNull { dep ->
                        SemVer.from(dep.version, true)
                    }?.also { selected ->
                        if (dependencies.size > 1) {
                            compilationResolver.project.logger.warn(
                                """
                                Transitive npm dependency version clash for compilation "${compilationResolver.compilation.name}"
                                    Candidates:
                                ${dependencies.joinToString("\n") { "\t\t" + it.name + "@" + it.version }}
                                    Selected:
                                        ${selected.name}@${selected.version}
                                """.trimIndent()
                            )
                        }
                    }
                }
            return direct + unique
        }

        internal fun CompositeDependency.getPackages(): List<File> {
            val packages = includedBuildDir.resolve(projectPackagesDir.relativeTo(rootDir))
            return packages
                .list()
                ?.map { packages.resolve(it) }
                ?.map { it.resolve(PACKAGE_JSON) }
                ?: emptyList()
        }
    }
}
