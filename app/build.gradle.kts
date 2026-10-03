import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 签名信息从 keystore.properties 读取，而不是硬编码在代码里。
// 该文件被 .gitignore 排除 —— 密码永远不进版本库。
// 文件不存在时（例如别人刚 clone 下来），签名配置为空，项目依然可以构建。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { stream -> keystoreProps.load(stream) }
}
val hasSigningKey = keystorePropsFile.exists() &&
    !keystoreProps.getProperty("storePassword").isNullOrBlank()

android {
    namespace = "com.yanhu.subblock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yanhu.subblock"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.0"

        // 作者与仓库地址集中在这里定义，改一处即可全局同步
        buildConfigField("String", "AUTHOR_NAME", "\"Lihoo\"")
        buildConfigField("String", "GITHUB_URL", "\"https://github.com/Amatorelover/SubBlock\"")
    }

    signingConfigs {
        create("personal") {
            if (hasSigningKey) {
                storeFile = file(keystoreProps.getProperty("storeFile") ?: "keystore/subblock.jks")
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias") ?: "subblock"
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasSigningKey) signingConfigs.getByName("personal") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
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
        // 「关于」页要读取版本号与作者信息，需要开启 BuildConfig
        buildConfig = true
    }

    // 个人自用项目：不因为静态检查的警告中断打包
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
