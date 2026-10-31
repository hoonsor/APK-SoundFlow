plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// SemVer → versionCode（1.2.3 → 10203）。發新版：改這一行後 push vX.Y.Z tag（CI 會檢查一致）
val appVersion = "0.7.3"
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }

// 共用簽章 hoonsor-release.jks：由 CI 從 Secrets 解出；本機或沒有 Secrets 時退回 debug 簽章
val signingPath: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() && file(it).exists() }

// Agent API 的 describe 直接讀 docs/agent-manifest.json（唯一來源），編譯時複製進 assets
val agentAssetsDir = layout.buildDirectory.dir("generated/agentAssets")
val copyAgentManifest = tasks.register<Copy>("copyAgentManifest") {
    from(rootProject.file("docs/agent-manifest.json"))
    into(agentAssetsDir)
}

android {
    namespace = "tw.hoonsor.soundflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "tw.hoonsor.soundflow"
        minSdk = 30
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        // Supabase（AI_News 專案，與新知雷達共用）。publishable key 本來就是公開金鑰，資料靠 RLS＋登入保護
        buildConfigField("String", "SUPABASE_URL", "\"https://yczvjdawynmcorvauyey.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_k9bqvK15Ud76GLTFuT1S-A_kELRKi2N\"")
    }

    signingConfigs {
        if (signingPath != null) {
            create("hoonsor") {
                storeFile = file(signingPath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("prod") {
            dimension = "channel"
            resValue("string", "app_name", "SoundFlow")
            resValue("string", "share_label", "SoundFlow 轉成音檔")
            resValue("string", "cover_share_label", "設為 SoundFlow 封面")
        }
        create("lab") {
            dimension = "channel"
            applicationIdSuffix = ".lab"
            versionNameSuffix = "-lab"
            resValue("string", "app_name", "SoundFlow LAB")
            // 分享選單要分得出正式版與 LAB 版
            resValue("string", "share_label", "SoundFlow LAB 轉成音檔")
            resValue("string", "cover_share_label", "設為 SoundFlow LAB 封面")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingPath != null) signingConfigs.getByName("hoonsor") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    sourceSets["main"].assets.srcDir(agentAssetsDir)

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

tasks.named("preBuild") { dependsOn(copyAgentManifest) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.squareup.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.glance.appwidget)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
