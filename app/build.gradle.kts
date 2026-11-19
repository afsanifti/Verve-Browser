import java.io.File
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val ciVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1
val ciVersionName = providers.gradleProperty("versionName").orNull ?: "1.0"
val ciGithubRepository = providers.gradleProperty("githubRepository").orNull
    ?: System.getenv("GITHUB_REPOSITORY")
    ?: "OWNER/REPO"

val localProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

fun resolveSecret(key: String): String? =
    (System.getenv(key)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(key))
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
        ?.takeIf { it.isNotBlank() }

fun resolveKeystoreFile(rawPath: String?): File? {
    if (rawPath.isNullOrBlank()) return null
    val expanded = if (rawPath.startsWith("~/")) {
        System.getProperty("user.home") + rawPath.removePrefix("~")
    } else {
        rawPath
    }
    val candidate = File(expanded)
    return if (candidate.isAbsolute) candidate else rootProject.file(expanded)
}

android {
    namespace = "com.verve.browser"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.verve.browser"
        minSdk = 31
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName

        buildConfigField("String", "GITHUB_REPOSITORY", "\"$ciGithubRepository\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val rawKeystorePath = resolveSecret("KEYSTORE_FILE")
        val keystoreFile = resolveKeystoreFile(rawKeystorePath)
        if (keystoreFile != null && keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = resolveSecret("KEYSTORE_PASSWORD")
                keyAlias = resolveSecret("KEY_ALIAS")
                keyPassword = resolveSecret("KEY_PASSWORD")
            }
        } else {
            logger.warn("Release signing skipped: keystore not found at '${rawKeystorePath ?: "KEYSTORE_FILE not set"}'")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("androidx.webkit:webkit:1.18.0")
    implementation("com.google.android.gms:play-services-cronet:18.1.1")
    implementation("org.chromium.net:cronet-api:500.1.0")

    implementation(libs.materialKolor.material3)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val releaseApkProvider = layout.buildDirectory.file("outputs/apk/release/app-release.apk")

tasks.matching { it.name == "assembleRelease" }.configureEach {
    val apkFile = releaseApkProvider
    doLast {
        val apk = apkFile.get().asFile
        if (apk.exists()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
            val sha256 = digest.joinToString(separator = "") { b -> "%02x".format(b) }
            File(apk.parentFile, "${apk.name}.sha256").writeText("$sha256  ${apk.name}\n")
            println("\n>>> Release SHA-256: $sha256\n")
        } else {
            println("\n>>> Warning: Signed app-release.apk not found. Check KEYSTORE_FILE in local.properties.\n")
        }
    }
}