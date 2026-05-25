package com.example.core.config

object FeatureFlags {
    val showOverlays: Boolean
        get() = BuildCapabilities.isDeveloperBuild && BuildCapabilities.isDebugMode

    val enableReplay: Boolean
        get() = BuildCapabilities.isDeveloperBuild || BuildCapabilities.isBenchmarkBuild || BuildCapabilities.isInternalBuild

    val enableBenchmarks: Boolean
        get() = BuildCapabilities.isBenchmarkBuild

    val enableTestImages: Boolean
        get() = BuildCapabilities.isDeveloperBuild

    val enableDiagnostics: Boolean
        get() = BuildCapabilities.isDeveloperBuild || BuildCapabilities.isInternalBuild
}
