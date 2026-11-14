import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

fun localProp(key: String): String =
    localProps.getProperty(key, "").replace("\"", "\\\"")

val proxyUrlProp = localProp("conversion.proxy.url").trim()
val embedDirectKeys = localProps.getProperty("conversion.embed.direct.keys", "")
    .trim()
    .equals("true", ignoreCase = true) || proxyUrlProp.isEmpty()

fun directKeyOrBlank(key: String): String =
    if (embedDirectKeys) localProp(key) else ""

android {
    namespace = "com.yomi.reader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yomi.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CONVERSION_PROXY_URL", "\"$proxyUrlProp\"")
        buildConfigField("String", "CONVERSION_PROXY_TOKEN", "\"${localProp("conversion.proxy.token")}\"")
        buildConfigField("String", "CLOUDCONVERT_API_KEY", "\"${directKeyOrBlank("cloudconvert.api.key")}\"")
        buildConfigField("String", "CLOUDCONVERT_BACKUP_API_KEY", "\"${directKeyOrBlank("cloudconvert.backup.api.key")}\"")
        buildConfigField("String", "ILOVEPDF_PUBLIC_KEY", "\"${directKeyOrBlank("ilovepdf.public.key")}\"")
        buildConfigField("String", "ILOVEPDF_BACKUP_PUBLIC_KEY", "\"${directKeyOrBlank("ilovepdf.backup.public.key")}\"")
        buildConfigField("String", "ILOVEPDF_SECRET_KEY", "\"${directKeyOrBlank("ilovepdf.secret.key")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Database (Native SQLite)
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coil Image Loading
    implementation(libs.coil.compose)

    // Apache POI (Office format parsing: docx, xlsx, pptx)
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)

    // Document File
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    // Google Credential Manager & Google ID
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}