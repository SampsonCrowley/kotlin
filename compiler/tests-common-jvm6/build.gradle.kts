plugins {
    kotlin("jvm")
    id("jps-compatible")
}

project.updateJvmTarget("1.6")

dependencies {
    api(kotlinStdlib())
    testApi(project(":kotlin-test:kotlin-test-jvm"))
    kotlinCompilerClasspath(project(":libraries:tools:stdlib-compiler-classpath"))
}

sourceSets {
    "main" { }
    "test" { projectDefault() }
}

testsJar {}
