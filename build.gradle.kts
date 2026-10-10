import java.util.Properties

plugins {
    id("com.android.application") version "9.4.1"
}

val signingProperties = Properties().apply {
    val localFile = rootProject.file("signing.local.properties")
    if (localFile.isFile) localFile.inputStream().use { load(it) }
}
fun signingValue(name: String): String? = providers.environmentVariable(name).orNull
    ?: signingProperties.getProperty(name)
val signingStore = signingValue("HOOK_KEYSTORE_PATH")
val signingPassword = signingValue("HOOK_KEYSTORE_PASSWORD")
val signingAlias = signingValue("HOOK_KEY_ALIAS")
val signingKeyPassword = signingValue("HOOK_KEY_PASSWORD")
val hasReleaseSigning = listOf(signingStore, signingPassword, signingAlias, signingKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "xyz.fearr.hookxbud"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.fearr.hookxbud"
        minSdk = 26
        targetSdk = 37
        versionCode = 13
        versionName = "0.12.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("distribution") {
                storeFile = rootProject.file(requireNotNull(signingStore))
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("distribution")
        }
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    doLast {
        check(hasReleaseSigning) {
            "Release signing is missing. Configure signing.local.properties or HOOK_KEYSTORE_* environment variables."
        }
        check(rootProject.file(requireNotNull(signingStore)).isFile) { "Release keystore does not exist." }
    }
}
tasks.matching { it.name == "packageRelease" }.configureEach {
    dependsOn(validateReleaseSigning)
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
}
