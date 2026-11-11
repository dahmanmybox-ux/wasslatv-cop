plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// تنظيف: حذف أكواد واجهة أندرويد القديمة (Compose) والملفات غير المستعملة قبل كل بناء.
// الواجهة الآن كلها في assets (HTML/CSS/JS) والغلاف الوحيد هو WebActivity.kt
run {
    file("src/main/java/dz/wassla/tv").listFiles()
        ?.filter { it.extension == "kt" && it.name != "WebActivity.kt" }
        ?.forEach { it.delete() }
    listOf("official_channels.json", "builtin_channels.json").forEach { file("src/main/assets/$it").delete() }
}

android {
    namespace = "dz.wassla.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "dz.wassla.tv"
        minSdk = 21
        // 29: يسمح بقراءة مجلد التنزيلات وUSB مباشرة للبحث عن ملفات القنوات الجديدة (التطبيق خارج المتجر)
        targetSdk = 29
        versionCode = 22
        versionName = "15.1.0"
    }
    // Fixed key so every new version installs over the previous one (keep the repo private)
    signingConfigs {
        create("release") {
            storeFile = file("wasslatv.jks")
            storePassword = "wasslatv2026"
            keyAlias = "wasslatv"
            keyPassword = "wasslatv2026"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
