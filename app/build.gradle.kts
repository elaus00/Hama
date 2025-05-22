plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.hama"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hama"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
        }
    }

    // 종속성 충돌 해결을 위한 설정
    configurations.all {
        resolutionStrategy {
            // HTTP 클라이언트 라이브러리 강제 버전 지정
            force("org.apache.httpcomponents.client5:httpclient5:5.3.1")
            force("org.apache.httpcomponents.core5:httpcore5:5.2.4")
            force("org.apache.httpcomponents.core5:httpcore5-h2:5.2.4")
        }
    }
}

dependencies {
    // 기본 안드로이드 라이브러리
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    
    // 테스트 라이브러리
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Hilt 의존성 주입
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Compose 관련
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.compose.runtime.livedata)

    // 로깅
    implementation(libs.slf4j.nop)

    // MCP 관련 의존성
    implementation("io.modelcontextprotocol:kotlin-sdk:0.4.0")
    implementation(libs.anthropic.java)

    // 비동기 처리
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.ktx.v270)

    // HTTP 클라이언트
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    // Ktor 클라이언트
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.plugins)

    // HTTP 관련 라이브러리 - 충돌 방지를 위해 runtimeOnly로 설정
    runtimeOnly(libs.httpclient5)
    runtimeOnly(libs.httpcore5)
    runtimeOnly(libs.httpcore5.h2)

    // Jackson 직렬화
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.jackson.datatype.jsr310)

    // WebSocket 라이브러리
    implementation(libs.java.websocket)

    // Kotlinx Serialization JSON
    implementation(libs.kotlinx.serialization.json.v173)
}
