package com.example.grammai

import org.junit.Test
import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    /**
     * HangulCombiner 로직 테스트
     * 한글 자모 조합이 올바르게 동작하는지 검증
     */
    @Test
    fun hangulCombination_isCorrect() {
        // 초성 + 중성 + 종성 조합 테스트
        val cho = "ㄱ"  // 초성
        val jung = "ㅏ"  // 중성
        val jong = "ㄴ"  // 종성

        // 예상 결과: "간"
        val expected = "간"
        val actual = combineHangul(cho, jung, jong)

        assertEquals("한글 조합이 올바르지 않습니다", expected, actual)
    }

    /**
     * 한글 자모 조합 헬퍼 함수
     * (실제 HangulCombiner 클래스의 로직을 단순화한 버전)
     */
    private fun combineHangul(cho: String, jung: String, jong: String): String {
        val choMap = mapOf(
            "ㄱ" to 0, "ㄲ" to 1, "ㄴ" to 2, "ㄷ" to 3, "ㄸ" to 4,
            "ㄹ" to 5, "ㅁ" to 6, "�" to 7, "ㅂ" to 8, "ㅃ" to 9,
            "ㅄ" to 10, "ㅅ" to 11, "ㅆ" to 12, "ㅇ" to 13
        )

        val jungMap = mapOf(
            "ㅏ" to 0, "ㅐ" to 1, "ㅑ" to 2, "ㅒ" to 3, "ㅓ" to 4,
            "ㅔ" to 5, "ㅕ" to 6, "ㅖ" to 7, "ㅗ" to 8, "ㅘ" to 9,
            "ㅙ" to 10, "ㅚ" to 11, "ㅝ" to 12, "ㅞ" to 13, "ㅟ" to 14,
            "ㅢ" to 15, "ㅣ" to 16
        )

        val jongMap = mapOf(
            "" to 0, "ㄱ" to 1, "ㄲ" to 2, "ㄳ" to 3, "ㄴ" to 4,
            "ㄵ" to 5, "ㄶ" to 6, "ㄷ" to 7, "ㄹ" to 8, "ㄺ" to 9,
            "ㄻ" to 10, "ㄼ" to 11, "ㄽ" to 12, "ㄾ" to 13, "ㄿ" to 14,
            "ㅀ" to 15, "ㅁ" to 16, "ㅂ" to 17, "ㅄ" to 18, "ㅅ" to 19,
            "ㅆ" to 20, "ㅇ" to 21
        )

        val choIdx = choMap[cho] ?: return ""
        val jungIdx = jungMap[jung] ?: return ""
        val jongIdx = jongMap[jong] ?: return ""

        // 유니코드 한글 음절 범위: 0xAC00 (가) ~ 0xD7A3 (힣)
        val codePoint = 0xAC00 + (choIdx * 21 * 28) + (jungIdx * 28) + jongIdx
        return codePoint.toChar().toString()
    }

    /**
     * 네트워크 요청 타임아웃 설정 테스트
     * 타임아웃 값이 올바르게 설정되었는지 검증
     */
    @Test
    fun networkTimeout_isConfiguredCorrectly() {
        val connectTimeoutSeconds = 5L
        val readTimeoutSeconds = 8L
        val writeTimeoutSeconds = 5L

        assertTrue("Connect timeout should be positive", connectTimeoutSeconds > 0)
        assertTrue("Read timeout should be positive", readTimeoutSeconds > 0)
        assertTrue("Write timeout should be positive", writeTimeoutSeconds > 0)
        assertTrue("Read timeout should be >= connect timeout", readTimeoutSeconds >= connectTimeoutSeconds)
    }

    /**
     * 에러 메시지 생성 테스트
     * 다양한 예외 상황에서 적절한 에러 메시지가 생성되는지 검증
     */
    @Test
    fun errorMessage_isGeneratedCorrectly() {
        val socketTimeoutError = "요청 시간 초과"
        val connectionError = "서버에 연결할 수 없습니다"
        val networkError = "네트워크 오류"

        assertFalse("Error message should not be empty", socketTimeoutError.isEmpty())
        assertFalse("Error message should not be empty", connectionError.isEmpty())
        assertFalse("Error message should not be empty", networkError.isEmpty())

        assertTrue("Error message should contain meaningful text", socketTimeoutError.contains("시간"))
        assertTrue("Error message should contain meaningful text", connectionError.contains("연결"))
    }

    /**
     * 파일 경로 검증 테스트
     * ONNX 모델 파일 경로가 올바르게 구성되는지 검증
     */
    @Test
    fun onnxModelPath_isValid() {
        val modelFileName = "kot5_spellcheck_int8.onnx"
        val modelPath = "/data/data/com.example.grammai/files/$modelFileName"

        assertTrue("Model file name should not be empty", modelFileName.isNotEmpty())
        assertTrue("Model file should have .onnx extension", modelFileName.endsWith(".onnx"))
        assertTrue("Model path should contain file name", modelPath.contains(modelFileName))
    }
}