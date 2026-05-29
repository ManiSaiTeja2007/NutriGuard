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

    // Switch for Stage 13.1 runtime validation and rollback safety
    val useExecutionGraph: Boolean
        get() = true
}
