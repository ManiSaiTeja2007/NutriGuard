package com.example.core.config

import com.example.BuildConfig

object BuildCapabilities {
    val isDeveloperBuild: Boolean
        get() = BuildConfig.FLAVOR == "developer"

    val isBenchmarkBuild: Boolean
        get() = BuildConfig.FLAVOR == "benchmark"

    val isInternalBuild: Boolean
        get() = BuildConfig.FLAVOR == "internal"

    val isProductionBuild: Boolean
        get() = BuildConfig.FLAVOR == "production"

    val isDebugMode: Boolean
        get() = BuildConfig.DEBUG
}
