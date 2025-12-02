// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// 💡 [추가 시작] secrets.properties 파일에서 API Key를 로드하는 로직 💡

// 1. secrets.properties 파일 경로 지정
val apiKeyPropertiesFile = rootProject.file("secrets.properties")
val apiKeyProperties = java.util.Properties()

// 2. 파일이 존재하면 내용을 로드
if (apiKeyPropertiesFile.exists()) {
    java.io.FileInputStream(apiKeyPropertiesFile).use {
        apiKeyProperties.load(it)
    }
}

// 3. 로드된 API Key를 모든 하위 모듈에서 접근할 수 있도록 'extra' 속성에 추가
// 'geminiApiKey'라는 이름으로 값을 노출합니다.
extra.set("geminiApiKey", apiKeyProperties.getProperty("GEMINI_API_KEY") ?: "")

// 💡 [추가 끝]