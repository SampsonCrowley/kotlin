/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.logging.Logger
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.service.ServiceRegistry
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinProjectNpmResolution
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinRootNpmResolution
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.*
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinPackageJsonTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnEnv

/**
 * # NPM resolution state manager
 *
 * ## Resolving process from Gradle
 *
 * **configuring**. Global initial state. [NpmResolverPlugin] should be applied for each project
 * that requires NPM resolution. When applied, [KotlinProjectNpmResolver] will be created for the
 * corresponding project and will subscribe to all js compilations. [NpmResolverPlugin] requires
 * kotlin mulitplatform or plaform plugin applied first.
 *
 * **up-to-date-checked**. This state is compilation local: one compilation may be in up-to-date-checked
 * state, while another may be steel in configuring state. New compilations may be added in this
 * state, but compilations that are already up-to-date-checked cannot be changed.
 * Initiated by calling [KotlinPackageJsonTask.producerInputs] getter (will be called by Gradle).
 * [KotlinCompilationNpmResolver] will create and **resolve** aggregated compilation configuration,
 * which contains all related compilation configuration and NPM tools configuration.
 * NPM tools configuration contains all dependencies that is required for all enabled
 * tasks related to this compilation. It is important to resolve this configuration inside particular
 * project and not globally. Aggregated configuration will be analyzed for gradle internal dependencies
 * (project dependencies), gradle external dependencies and npm dependencies. This collections will
 * be treated as `packageJson` task inputs.
 *
 * **package-json-created**. This state also compilation local. Initiated by executing `packageJson`
 * task for particular compilation. If `packageJson` task is up-to-date, this state is reached by
 * first calling [KotlinCompilationNpmResolver.getResolutionOrResolve] which may be called
 * by compilation that depends on this compilation. Note that package.json will be executed only for
 * required compilations, while other may be missed.
 *
 * **Prepared**.
 * Global final state. Initiated by executing global `rootPackageJson` task.
 *
 * **Installed**.
 * All created package.json files will be gathered and package manager will be executed.
 * Package manager will create lock file, that will be parsed for transitive npm dependencies
 * that will be added to the root [NpmDependency] objects. `kotlinNpmInstall` task may be up-to-date.
 * In this case, installed state will be reached by first call of [installIfNeeded] without executing
 * package manager.
 *
 * User can call [requireInstalled] to get resolution info.
 */
abstract class KotlinNpmResolutionManager internal constructor(
//    @Transient private val nodeJsSettings: NodeJsRootExtension?,
//    val stateHolderProvider: Provider<KotlinNpmResolutionManagerStateHolder>,
//    val rootProjectName: String,
//    val rootProjectVersion: String,
//    @Transient
//    val buildServiceRegistry: BuildServiceRegistry,
//    internal val gradleNodeModulesProvider: Provider<GradleNodeModulesCache>,
//    internal val compositeNodeModulesProvider: Provider<CompositeNodeModulesCache>,
//    internal val mayBeUpToDateTasksRegistry: Provider<MayBeUpToDatePackageJsonTasksRegistry>,
//    @Transient
//    val yarnEnvironment_: Provider<YarnEnv>?,
//    @Transient
//    val npmEnvironment_: Provider<NpmEnvironment>?,
//    @Transient
//    val yarnResolutions_: Provider<List<YarnResolution>>?
) : BuildService<KotlinNpmResolutionManager.Parameters> {

    internal interface Parameters : BuildServiceParameters {
        val resolver: Property<KotlinRootNpmResolver>
//        val rootProjectName: Property<String>
//        val rootProjectVersion: Property<String>
//        val tasksRequirements: Property<TasksRequirements>
//        val versions: Property<NpmVersions>
//        val projectPackagesDir: Property<File>
//        val rootProjectDir: Property<File>
////        val nodeJs: Property<NodeJsRootExtension>
////        val yarn: Property<YarnRootExtension>
        // pulled up from compilation resolver since it was failing with ClassNotFoundException on deserialization, see KT-49061
        val packageJsonHandlers: MapProperty<String, List<PackageJson.() -> Unit>>

        val gradleNodeModulesProvider: Property<GradleNodeModulesCache>
        val compositeNodeModulesProvider: Property<CompositeNodeModulesCache>
        val mayBeUpToDateTasksRegistry: Property<MayBeUpToDatePackageJsonTasksRegistry>
    }

    //    val resolver = KotlinRootNpmResolver(
//        parameters.rootProjectName,
//        parameters.rootProjectVersion,
//        parameters.tasksRequirements,
//        parameters.versions,
//        parameters.projectPackagesDir,
//        parameters.rootProjectDir,
////        parameters.nodeJs,
////        parameters.yarn,
////        buildServiceRegistry,
//        parameters.gradleNodeModulesProvider,
//        parameters.compositeNodeModulesProvider,
//        parameters.mayBeUpToDateTasksRegistry,
////        yarnEnvironment_,
////        npmEnvironment_,
////        yarnResolutions_
//    )

//    abstract class KotlinNpmResolutionManagerStateHolder : BuildService<BuildServiceParameters.None> {
//        @Volatile
//        internal var state: ResolutionState? = null
//    }
//
//    private val stateHolder get() = stateHolderProvider.get()

    val resolver
        get() = parameters.resolver

    @Volatile
    var state: ResolutionState = ResolutionState.Configuring(resolver.get())

    sealed class ResolutionState {
        abstract val npmProjects: List<NpmProject>

        class Configuring(val resolver: KotlinRootNpmResolver) : ResolutionState() {
            override val npmProjects: List<NpmProject>
                get() = resolver.compilations.map { it.npmProject }
        }

        open class Prepared(val preparedInstallation: KotlinRootNpmResolver.Installation) : ResolutionState() {
            override val npmProjects: List<NpmProject>
                get() = npmProjectsByProjectResolutions(preparedInstallation.projectResolutions)
        }

        class Installed internal constructor(internal val resolved: KotlinRootNpmResolution) : ResolutionState() {
            override val npmProjects: List<NpmProject>
                get() = npmProjectsByProjectResolutions(resolved.projects)
        }

        class Error(val wrappedException: Throwable) : ResolutionState() {
            override val npmProjects: List<NpmProject>
                get() = emptyList()
        }

        companion object {
            fun npmProjectsByProjectResolutions(
                resolutions: Map<String, KotlinProjectNpmResolution>
            ): List<NpmProject> {
                return resolutions
                    .values
                    .flatMap { it.npmProjects.map { it.npmProject } }
            }
        }
    }

//    @Incubating
//    internal fun requireInstalled(
//        services: ServiceRegistry,
//        logger: Logger,
//    ) = installIfNeeded(services = services, logger = logger)

    internal fun requireConfiguringState(): KotlinRootNpmResolver =
        (this.state as? ResolutionState.Configuring ?: error("NPM Dependencies already resolved and installed")).resolver

    internal fun isConfiguringState(): Boolean =
        this.state is ResolutionState.Configuring

    internal fun prepare(
        logger: Logger,
        npmEnvironment: NpmEnvironment,
        yarnEnvironment: YarnEnv,
    ) = prepareIfNeeded(logger = logger, npmEnvironment, yarnEnvironment)

    internal fun installIfNeeded(
        args: List<String> = emptyList(),
        services: ServiceRegistry,
        logger: Logger,
        npmEnvironment: NpmEnvironment,
        yarnEnvironment: YarnEnv,
    ): KotlinRootNpmResolution? {
        synchronized(this) {
            if (state is ResolutionState.Installed) {
                return (state as ResolutionState.Installed).resolved
            }

            if (state is ResolutionState.Error) {
                return null
            }

            return try {
                val installation = prepareIfNeeded(logger = logger, npmEnvironment, yarnEnvironment)
                val resolution = installation
                    .install(args, services, logger, npmEnvironment, yarnEnvironment)
                state = ResolutionState.Installed(resolution)
                resolution
            } catch (e: Exception) {
                state = ResolutionState.Error(e)
                throw e
            }
        }
    }

//    internal val packageJsonFiles: Collection<File>
//        get() = state.npmProjects.map { it.packageJsonFile }

    private fun prepareIfNeeded(
        logger: Logger,
        npmEnvironment: NpmEnvironment,
        yarnEnvironment: YarnEnv,
    ): KotlinRootNpmResolver.Installation {
        val state0 = this.state
        return when (state0) {
            is ResolutionState.Prepared -> {
                state0.preparedInstallation
            }

            is ResolutionState.Configuring -> {
                synchronized(this) {
                    val state1 = this.state
                    when (state1) {
                        is ResolutionState.Prepared -> state1.preparedInstallation
                        is ResolutionState.Configuring -> {
                            state1.resolver.prepareInstallation(
                                logger,
                                npmEnvironment,
                                yarnEnvironment,
                                this
                            ).also {
                                this.state = ResolutionState.Prepared(it)
                            }
                        }

                        is ResolutionState.Installed -> error("Project already installed")
                        is ResolutionState.Error -> throw state1.wrappedException
                    }
                }
            }

            is ResolutionState.Installed -> error("Project already installed")
            is ResolutionState.Error -> throw state0.wrappedException
        }
    }
}