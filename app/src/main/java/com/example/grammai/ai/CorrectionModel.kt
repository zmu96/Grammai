package com.example.grammai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import com.example.grammai.BuildConfig

/**
 * Gemini API를 사용하여 텍스트 교정을 수행하는 클래스입니다.
 */
class CorrectionModel {

    // 💡 [수정 완료] BuildConfig에서 안전하게 키를 불러옵니다.
    private val API_KEY = BuildConfig.GEMINI_API_KEY
    private val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-09-2025:generateContent?key=$API_KEY"

    /**
     * 입력된 문장을 Gemini 모델을 통해 문법적으로 교정합니다.
     * @param originalText 교정할 원본 문장
     * @return 교정된 문장 (수정된 텍스트)과 원본 텍스트를 포함하는 CorrectionResult 객체
     */
    suspend fun correct(originalText: String): CorrectionResult = withContext(Dispatchers.IO) {
        // 문장 교정을 위한 시스템 명령어 및 사용자 프롬프트 구성
        val systemInstruction = "You are a helpful Korean grammar and spelling checker. Your task is to analyze the user's Korean sentence and provide a single, corrected version of the entire sentence. DO NOT include any explanations, greetings, or surrounding text, only output the corrected sentence."
        val userPrompt = "다음 한국어 문장을 문법과 맞춤법에 맞게 교정해 주세요: \"$originalText\""

        val payload = createPayload(systemInstruction, userPrompt)

        // API 호출 및 결과 파싱
        try {
            val connection = createConnection()

            // 요청 본문 전송
            connection.outputStream.use { os ->
                val input = payload.toString().toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }

            // 응답 처리
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = readResponse(connection)
                val correctedText = parseCorrectionResponse(response)

                // 교정된 텍스트가 원본 텍스트와 다르고 공백이 아니면 성공
                if (correctedText.isNotBlank() && correctedText != originalText) {
                    return@withContext CorrectionResult(
                        original = originalText,
                        corrected = correctedText,
                        isCorrected = true
                    )
                }
            } else {
                // HTTP 오류 처리
                val errorStream = readErrorStream(connection)
                println("API HTTP Error ($responseCode): $errorStream")
            }
        } catch (e: Exception) {
            println("API Call Exception: ${e.message}")
        }

        // 오류 발생 또는 교정할 내용이 없는 경우
        return@withContext CorrectionResult(
            original = originalText,
            corrected = originalText,
            isCorrected = false
        )
    }

    // ------------------- Private Helper Methods -------------------

    private fun createPayload(systemInstruction: String, userPrompt: String): JSONObject {
        val payload = JSONObject()

        // 1. Contents (사용자 프롬프트)
        val contents = JSONArray().put(
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
        )
        payload.put("contents", contents)

        // 2. System Instruction (모델 역할 정의)
        val systemInstructionObject = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
        payload.put("systemInstruction", systemInstructionObject)

        // 3. Tools (Google Search Grounding 활성화 - 실시간 정보 접근 가능)
        payload.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        return payload
    }

    private fun createConnection(): HttpURLConnection {
        val url = URL(API_URL)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun readErrorStream(connection: HttpURLConnection): String {
        return connection.errorStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun parseCorrectionResponse(response: String): String {
        return try {
            val jsonResponse = JSONObject(response)
            val text = jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            // 모델이 불필요한 따옴표를 추가하는 경우 제거
            text.trim('"')
        } catch (e: Exception) {
            println("Response Parsing Error: ${e.message}")
            "" // 파싱 실패 시 빈 문자열 반환
        }
    }
}

/**
 * 교정 결과를 담는 데이터 클래스
 */
data class CorrectionResult(
    val original: String, // 원본 문장
    val corrected: String, // 교정된 문장
    val isCorrected: Boolean // 실제로 교정이 이루어졌는지 여부
)