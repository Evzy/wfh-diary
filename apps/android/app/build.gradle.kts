import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
}

private val localProperties: Properties =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

private fun localPropOrEnv(propertyKey: String): String {
    val fromFile = localProperties.getProperty(propertyKey, "")?.trim().orEmpty()
    if (fromFile.isNotEmpty()) return fromFile
    return System.getenv(propertyKey)?.trim().orEmpty()
}

@Suppress("UNCHECKED_CAST")
private val repoVersion: Map<String, Any> = run {
    val f = rootProject.file("../../version.json")
    JsonSlurper().parse(f) as Map<String, Any>
}

private fun escapeForBuildConfigString(s: String): String =
    buildString {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(c)
            }
        }
    }

android {
    namespace = "dev.wfhdiary.app"
    compileSdk = 35

    signingConfigs {
        getByName("debug") {
            val envPath = System.getenv("WFH_SHARED_DEBUG_KEYSTORE")
            val propPath = localProperties.getProperty("debug.storeFile")
            val keystoreFile =
                when {
                    !envPath.isNullOrBlank() -> file(envPath)
                    !propPath.isNullOrBlank() -> file(propPath)
                    else -> null
                }
            if (keystoreFile != null && keystoreFile.isFile) {
                val storePass =
                    localProperties.getProperty("debug.storePassword")
                        ?: System.getenv("WFH_SHARED_DEBUG_STORE_PASSWORD")
                val alias =
                    localProperties.getProperty("debug.keyAlias")
                        ?: System.getenv("WFH_SHARED_DEBUG_KEY_ALIAS")
                val keyPass =
                    localProperties.getProperty("debug.keyPassword")
                        ?: System.getenv("WFH_SHARED_DEBUG_KEY_PASSWORD")
                if (!storePass.isNullOrBlank() && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                    storeFile = keystoreFile
                    storePassword = storePass
                    keyAlias = alias
                    keyPassword = keyPass
                }
            }
        }
    }

    defaultConfig {
        applicationId = "dev.wfhdiary.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (repoVersion["versionCode"] as Number).toInt()
        versionName = repoVersion["versionName"] as String
        val mapsApiKey = escapeForBuildConfigString(localPropOrEnv("MAPS_API_KEY"))
        buildConfigField("boolean", "GOOGLE_MAPS_CONFIGURED", if (mapsApiKey.isNotEmpty()) "true" else "false")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey.ifEmpty { "MISSING_MAPS_API_KEY" }
        val updateOwner = escapeForBuildConfigString(
            localPropOrEnv("UPDATE_GITHUB_OWNER").ifEmpty { "Evzy" },
        )
        val updateRepo = escapeForBuildConfigString(
            localPropOrEnv("UPDATE_GITHUB_REPO").ifEmpty { "wfh-diary-releases" },
        )
        val updateToken = escapeForBuildConfigString(localPropOrEnv("UPDATE_GITHUB_TOKEN"))
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"$updateOwner\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"$updateRepo\"")
        buildConfigField("String", "UPDATE_GITHUB_TOKEN", "\"$updateToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.androidx.documentfile)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
