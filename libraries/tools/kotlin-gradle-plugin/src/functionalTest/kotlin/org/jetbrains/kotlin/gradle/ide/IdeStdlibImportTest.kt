/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName", "DuplicatedCode")

package org.jetbrains.kotlin.gradle.ide

import org.jetbrains.kotlin.compilerRunner.konanVersion
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.idea.testFixtures.tcs.assertMatches
import org.jetbrains.kotlin.gradle.idea.testFixtures.tcs.binaryCoordinates
import org.jetbrains.kotlin.gradle.idea.testFixtures.tcs.friendSourceDependency
import org.jetbrains.kotlin.gradle.kpm.idea.mavenCentralCacheRedirector
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType
import org.jetbrains.kotlin.gradle.plugin.ide.kotlinIdeMultiplatformImport
import org.jetbrains.kotlin.gradle.utils.androidExtension
import org.junit.Test

class IdeStdlibImportTest {

    @Test
    fun `test single jvm target`() {
        val project = createProjectWithDefaultStdlibEnabled()

        val kotlin = project.multiplatformExtension
        kotlin.jvm()

        project.evaluate()

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val jvmMain = kotlin.sourceSets.getByName("jvmMain")
        val jvmTest = kotlin.sourceSets.getByName("jvmTest")

        project.kotlinIdeMultiplatformImport.resolveDependencies(commonMain).assertMatches(
            jvmStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(commonTest).assertMatches(
            friendSourceDependency(":/jvmMain"),
            friendSourceDependency(":/commonMain"),
            jvmStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(jvmMain).assertMatches(
            dependsOnDependency(commonMain),
            jvmStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(jvmTest).assertMatches(
            dependsOnDependency(commonTest),
            friendSourceDependency(":/jvmMain"),
            friendSourceDependency(":/commonMain"),
            jvmStdlibDependencies(kotlin),
        )
    }

    @Test
    fun `test single native target`() {
        val project = createProjectWithDefaultStdlibEnabled()

        val kotlin = project.multiplatformExtension
        kotlin.linuxX64("linux")

        project.evaluate()

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val linuxMain = kotlin.sourceSets.getByName("linuxMain")
        val linuxTest = kotlin.sourceSets.getByName("linuxTest")

        project.kotlinIdeMultiplatformImport.resolveDependencies(commonMain).assertMatches(
            linuxStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(commonTest).assertMatches(
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/linuxMain"),
            linuxStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(linuxMain).assertMatches(
            dependsOnDependency(commonMain),
            linuxStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(linuxTest).assertMatches(
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/linuxMain"),
            dependsOnDependency(commonTest),
            linuxStdlibDependencies(kotlin),
        )
    }

    @Test
    fun `test single js target`() {
        val project = createProjectWithDefaultStdlibEnabled()

        val kotlin = project.multiplatformExtension
        kotlin.js(KotlinJsCompilerType.IR)

        project.evaluate()

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val jsMain = kotlin.sourceSets.getByName("jsMain")
        val jsTest = kotlin.sourceSets.getByName("jsTest")

        project.kotlinIdeMultiplatformImport.resolveDependencies(commonMain).assertMatches(
            jsStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(commonTest).assertMatches(
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/jsMain"),
            jsStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(jsMain).assertMatches(
            dependsOnDependency(commonMain),
            jsStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(jsTest).assertMatches(
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/jsMain"),
            dependsOnDependency(commonTest),
            jsStdlibDependencies(kotlin),
        )
    }

    @Test
    fun `test single android target`() {
        val project = createProjectWithAndroidAndDefaultStdlibEnabled()

        val kotlin = project.multiplatformExtension

        kotlin.android()

        project.evaluate()

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val androidMain = kotlin.sourceSets.getByName("androidMain")
        val androidUnitTest = kotlin.sourceSets.getByName("androidTest")
        val androidInstrumentedTest = kotlin.sourceSets.getByName("androidAndroidTest")

        project.kotlinIdeMultiplatformImport.resolveDependencies(commonMain).assertMatches(
            androidStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(commonTest).assertMatches(
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/androidMain"),
            androidStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(androidMain).assertMatches(
            dependsOnDependency(commonMain),
            androidStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(androidUnitTest).assertMatches(
            dependsOnDependency(commonTest),
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/androidMain"),
            androidStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(androidInstrumentedTest).assertMatches(
            dependsOnDependency(commonTest),
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/androidMain"),
            friendSourceDependency(":/androidDebug"),
            androidStdlibDependencies(kotlin),
        )
    }

    @Test
    fun `test jvm+native shared simple project`() {
        val project = createProjectWithDefaultStdlibEnabled()

        val kotlin = project.multiplatformExtension

        kotlin.jvm()
        kotlin.linuxX64("linux")

        project.evaluate()

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val jvmMain = kotlin.sourceSets.getByName("jvmMain")
        val jvmTest = kotlin.sourceSets.getByName("jvmTest")
        val linuxMain = kotlin.sourceSets.getByName("linuxMain")
        val linuxTest = kotlin.sourceSets.getByName("linuxTest")

        project.kotlinIdeMultiplatformImport.resolveDependencies(commonMain).assertMatches(
            commonStdlibDependencies(kotlin)
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(commonTest).assertMatches(
            friendSourceDependency(":/commonMain"),
            commonStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(jvmMain).assertMatches(
            dependsOnDependency(commonMain),
            jvmStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(jvmTest).assertMatches(
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/jvmMain"),
            dependsOnDependency(commonTest),
            jvmStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(linuxMain).assertMatches(
            dependsOnDependency(commonMain),
            linuxStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(linuxTest).assertMatches(
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/linuxMain"),
            dependsOnDependency(commonTest),
            linuxStdlibDependencies(kotlin),
        )
    }

    @Test
    fun `test bamboo jvm`() {
        val project = createProjectWithDefaultStdlibEnabled()

        val kotlin = project.multiplatformExtension
        kotlin.jvm()
        kotlin.linuxX64("linux")

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val jvmMain = kotlin.sourceSets.getByName("jvmMain")
        val jvmTest = kotlin.sourceSets.getByName("jvmTest")
        val jvmIntermediateMain = kotlin.sourceSets.create("jvmIntermediateMain") {
            it.dependsOn(commonMain)
            jvmMain.dependsOn(it)
        }
        val jvmIntermediateTest = kotlin.sourceSets.create("jvmIntermediateTest") {
            it.dependsOn(commonTest)
            jvmTest.dependsOn(it)
        }

        project.evaluate()

        project.kotlinIdeMultiplatformImport.resolveDependencies(commonMain).assertMatches(
            commonStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(commonTest).assertMatches(
            friendSourceDependency(":/commonMain"),
            commonStdlibDependencies(kotlin),
        )

        project.kotlinIdeMultiplatformImport.resolveDependencies(jvmIntermediateMain).assertMatches(
            dependsOnDependency(commonMain),
            jvmStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(jvmIntermediateTest).assertMatches(
            dependsOnDependency(commonTest),
            friendSourceDependency(":/jvmMain"),
            friendSourceDependency(":/jvmIntermediateMain"),
            friendSourceDependency(":/commonMain"),
            jvmStdlibDependencies(kotlin),
        )
    }

    @Test
    fun `test bamboo linux`() {
        val project = createProjectWithDefaultStdlibEnabled()

        val kotlin = project.multiplatformExtension
        kotlin.jvm()
        kotlin.linuxX64("linux")

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val linuxMain = kotlin.sourceSets.getByName("linuxMain")
        val linuxTest = kotlin.sourceSets.getByName("linuxTest")
        val linuxIntermediateMain = kotlin.sourceSets.create("linuxIntermediateMain") {
            it.dependsOn(commonMain)
            linuxMain.dependsOn(it)
        }
        val linuxIntermediateTest = kotlin.sourceSets.create("linuxIntermediateTest") {
            it.dependsOn(commonTest)
            linuxTest.dependsOn(it)
        }

        project.evaluate()

        project.kotlinIdeMultiplatformImport.resolveDependencies(linuxIntermediateMain).assertMatches(
            dependsOnDependency(commonMain),
            linuxStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(linuxIntermediateTest).assertMatches(
            dependsOnDependency(commonTest),
            friendSourceDependency(":/jvmMain"),
            friendSourceDependency(":/jvmIntermediateMain"),
            friendSourceDependency(":/commonMain"),
            linuxStdlibDependencies(kotlin),
        )
    }

    @Test
    fun `test nativeShared`() {
        val project = createProjectWithDefaultStdlibEnabled()

        val kotlin = project.multiplatformExtension
        kotlin.jvm()
        kotlin.linuxX64("x64")
        kotlin.linuxArm64("arm64")

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val x64Main = kotlin.sourceSets.getByName("x64Main")
        val x64Test = kotlin.sourceSets.getByName("x64Test")
        val arm64Main = kotlin.sourceSets.getByName("arm64Main")
        val arm64Test = kotlin.sourceSets.getByName("arm64Test")
        val linuxSharedMain = kotlin.sourceSets.create("linuxSharedMain") {
            it.dependsOn(commonMain)
            x64Main.dependsOn(it)
            arm64Main.dependsOn(it)
        }
        val linuxSharedTest = kotlin.sourceSets.create("linuxSharedTest") {
            it.dependsOn(commonTest)
            x64Test.dependsOn(it)
            arm64Test.dependsOn(it)
        }

        project.evaluate()

        project.kotlinIdeMultiplatformImport.resolveDependencies(linuxSharedMain).assertMatches(
            dependsOnDependency(commonMain),
            binaryCoordinates("org.jetbrains.kotlin:stdlib-native:${project.konanVersion}"),
            // TODO (kirpichenkov): commonized native distribution
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(linuxSharedTest).assertMatches(
            dependsOnDependency(commonTest),
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/linuxSharedMain"),
            binaryCoordinates("org.jetbrains.kotlin:stdlib-native:${project.konanVersion}"),
            // TODO (kirpichenkov): commonized native distribution
        )
    }

    @Test
    fun `test jvm + android`() {
        val project = createProjectWithAndroidAndDefaultStdlibEnabled()

        val kotlin = project.multiplatformExtension

        kotlin.android()
        kotlin.jvm()

        project.evaluate()

        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val commonTest = kotlin.sourceSets.getByName("commonTest")
        val androidMain = kotlin.sourceSets.getByName("androidMain")
        val androidUnitTest = kotlin.sourceSets.getByName("androidTest")
        val androidInstrumentedTest = kotlin.sourceSets.getByName("androidAndroidTest")

        project.kotlinIdeMultiplatformImport.resolveDependencies(commonMain).assertMatches(
            // TODO (kirpichenkov): correct stdlib
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(commonTest).assertMatches(
            friendSourceDependency(":/commonMain"),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(androidMain).assertMatches(
            dependsOnDependency(commonMain),
            androidStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(androidUnitTest).assertMatches(
            dependsOnDependency(commonTest),
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/androidMain"),
            androidStdlibDependencies(kotlin),
        )
        project.kotlinIdeMultiplatformImport.resolveDependencies(androidInstrumentedTest).assertMatches(
            dependsOnDependency(commonTest),
            friendSourceDependency(":/commonMain"),
            friendSourceDependency(":/androidMain"),
            friendSourceDependency(":/androidDebug"),
            androidStdlibDependencies(kotlin),
        )
    }

    private fun createProjectWithDefaultStdlibEnabled() = buildProject {
        enableDependencyVerification(false)
        enableDefaultStdlibDependency(true)
        applyMultiplatformPlugin()
        repositories.mavenLocal()
        repositories.mavenCentralCacheRedirector()
    }

    private fun createProjectWithAndroidAndDefaultStdlibEnabled() = buildProject {
        enableDefaultStdlibDependency(false)
        enableDependencyVerification(false)
        applyMultiplatformPlugin()
        plugins.apply("com.android.library")
        androidExtension.compileSdkVersion(33)
        repositories.mavenLocal()
        repositories.mavenCentralCacheRedirector()
        repositories.google()
    }

    private fun commonStdlibDependencies(kotlin: KotlinMultiplatformExtension) = listOf(
        binaryCoordinates("org.jetbrains.kotlin:kotlin-stdlib-common:${kotlin.coreLibrariesVersion}"),
    )

    private fun androidStdlibDependencies(kotlin: KotlinMultiplatformExtension) = listOf<Any>(
        // TODO (kirpichenkov): android stdlib is missing
    )

    private fun jvmStdlibDependencies(kotlin: KotlinMultiplatformExtension) = listOf(
        binaryCoordinates("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlin.coreLibrariesVersion}"),
        binaryCoordinates("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${kotlin.coreLibrariesVersion}"),
        binaryCoordinates("org.jetbrains.kotlin:kotlin-stdlib:${kotlin.coreLibrariesVersion}"),
        binaryCoordinates("org.jetbrains:annotations:13.0"),
    )

    private fun jsStdlibDependencies(kotlin: KotlinMultiplatformExtension) = listOf(
        binaryCoordinates("org.jetbrains.kotlin:kotlin-stdlib-js:${kotlin.coreLibrariesVersion}"),
    )

    private fun linuxStdlibDependencies(kotlin: KotlinMultiplatformExtension) = listOf(
        binaryCoordinates("org.jetbrains.kotlin.native:platform.iconv:${kotlin.project.konanVersion}"),
        binaryCoordinates("org.jetbrains.kotlin.native:platform.posix:${kotlin.project.konanVersion}"),
        binaryCoordinates("org.jetbrains.kotlin.native:platform.zlib:${kotlin.project.konanVersion}"),
        binaryCoordinates("org.jetbrains.kotlin.native:platform.linux:${kotlin.project.konanVersion}"),
        binaryCoordinates("org.jetbrains.kotlin.native:platform.builtin:${kotlin.project.konanVersion}"),
        binaryCoordinates("org.jetbrains.kotlin:stdlib-native:${kotlin.project.konanVersion}"),
    )
}
