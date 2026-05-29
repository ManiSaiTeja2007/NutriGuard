plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.json:json:20240303")
}

kotlin {
    sourceSets {
        getByName("main") {
            kotlin.setSrcDirs(listOf("."))
            kotlin.exclude("build/**")
        }
    }
}

tasks.register<JavaExec>("pspRefresh") {
    group = "governance"
    description = "Runs the automated PSP verification and generation pipeline."
    classpath = sourceSets.getByName("main").runtimeClasspath
    mainClass.set("PSPRefreshKt")
}
