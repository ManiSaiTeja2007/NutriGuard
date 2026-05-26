plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.nutriguard.zkvxqp"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  flavorDimensions.add("mode")
  productFlavors {
    create("developer") {
      dimension = "mode"
      applicationIdSuffix = ".dev"
    }
    create("benchmark") {
      dimension = "mode"
      applicationIdSuffix = ".benchmark"
    }
    create("internal") {
      dimension = "mode"
      applicationIdSuffix = ".internal"
    }
    create("production") {
      dimension = "mode"
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  sourceSets {
    getByName("debug") {
      assets.directories.add("src/androidTest/assets")
    }
    getByName("developer") {
      assets.directories.add("../benchmark")
    }
    getByName("benchmark") {
      assets.directories.add("../benchmark")
    }
  }

  tasks.register("verifyProductionAssets") {
    val mainAssetsDir = layout.projectDirectory.dir("src/main/assets").asFile
    doLast {
      if (mainAssetsDir.exists()) {
        val hasManifest = mainAssetsDir.walk().any { it.name == "master_manifest.json" }
        val hasDatasets = mainAssetsDir.walk().any { it.parentFile.name == "datasets" }
        if (hasManifest || hasDatasets) {
          throw GradleException("Safety violation: Benchmark datasets or manifests found in production assets directory!")
        }
      }
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.androidx.datastore.preferences)
  testImplementation(libs.junit)
  testImplementation("org.json:json:20240303")
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.core)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.uiautomator)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.register("unitTestClasses") {
    description = "Compile unit test classes for the debug variant."
    group = "build"
    dependsOn("compileDebugUnitTestSources")
}
