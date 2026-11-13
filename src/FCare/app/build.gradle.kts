import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)   // 내장 Kotlin 대신 직접 적용 (KSP 호환)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Gemini API 키·모델은 local.properties에서 읽는다 (GitHub에 올라가지 않는 파일, 개선사항 2)
//   gemini.apiKey=발급받은 키
//   gemini.model=gemini-2.5-flash   (생략하면 이 값. 사용 가능한 모델 이름은 Google AI Studio에서 확인)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Kotlin 컴파일 대상도 Java 설정(11)과 맞춘다
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

android {
    namespace = "com.triple.fcare"
    compileSdk = 36   // 새 DSL을 껐으므로 기존 방식으로 지정

    defaultConfig {
        applicationId = "com.triple.fcare"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("gemini.apiKey", "")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${localProps.getProperty("gemini.model", "gemini-2.5-flash")}\"")
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
    buildFeatures {
        compose = true
        buildConfig = true   // BuildConfig.GEMINI_API_KEY 생성
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)  // 하단 탭 아이콘
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.health.connect)   // Health Connect (3단계)
    implementation(libs.androidx.room.runtime)     // Room 로컬 DB (4단계)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx) // WorkManager 백그라운드 작업 (5단계)
    implementation(libs.androidx.lifecycle.viewmodel.compose) // 화면 상태 관리 (6단계)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}