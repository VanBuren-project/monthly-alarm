plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.anqi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anqi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal sideload build: packaged as a non-debuggable release,
            // signed with the local debug key so it can be reinstalled later.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // The host profile is not writable; keep Robolectric's SDK download cache
    // and lock file inside the workspace.
    val robolectricHome = rootProject.layout.projectDirectory.dir(".robolectric-home").asFile
    val testTmp = rootProject.layout.projectDirectory.dir(".test-tmp").asFile
    val robolectricDeps = rootProject.projectDir.parentFile.resolve(".toolchain/robolectric-deps")
    robolectricHome.mkdirs()
    testTmp.mkdirs()
    systemProperty("user.home", robolectricHome.absolutePath)
    systemProperty("java.io.tmpdir", testTmp.absolutePath)
    if (robolectricDeps.isDirectory) {
        systemProperty("robolectric.dependency.dir", robolectricDeps.absolutePath)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit.ktx)
    testImplementation(libs.robolectric)
}
