plugins {
    id("com.android.application")
}

android {
    namespace = "cn.llxhq.consumer"
    compileSdk = 34

    defaultConfig {
        applicationId = "cn.llxhq.consumer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// 纯框架实现，不引入 AndroidX / Material，APK 体积最小
