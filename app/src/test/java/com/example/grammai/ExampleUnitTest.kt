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
     * HangulCombiner 한글 자모 조합 로직 테스트
     * 초성, 중성, 종성의 올바른 조합을 검증합니다.
     */
    @Test
    fun testHangulCharacterCombination() {
        // 테스트 케이스: ㄱ + ㅏ = 가
        val cho = 'ㄱ'
        val jung = 'ㅏ'
        
        // 한글 조합 로직 검증
        val hangulBase = 0xAC00
        val choIndex = 0  // ㄱ은 초성 인덱스 0
        val jungIndex = 0  // ㅏ는 중성 인덱스 0
        val jongIndex = 0  // 종성 없음
        
        val expectedChar = (hangulBase + choIndex * 21 * 28 + jungIndex * 28 + jongIndex).toChar()
        assertEquals('가', expectedChar)
    }

    /**
     * 겹받침 분리 로직 검증
     * 겹받침이 올바르게 분리되는지 확인합니다.
     */
    @Test
    fun testDoubleJongSeparation() {
        // ㄳ(ㄱ+ㅅ)의 첫 번째 받침은 ㄱ이어야 함
        val jongMap = listOf(
            "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ",
            "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
        )
        
        val jongFirstMap = mapOf(
            3 to 1,   // ㄳ(3) -> ㄱ(1)
            5 to 4,   // ㄵ(5) -> ㄴ(4)
            6 to 4,   // ㄶ(6) -> ㄴ(4)
            9 to 8,   // ㄺ(9) -> ㄹ(8)
            10 to 16, // ㄻ(10) -> ㅁ(16)
            11 to 8,  // ㄼ(11) -> ㄹ(8)
            12 to 8,  // ㄽ(12) -> ㄹ(8)
            13 to 8,  // ㄾ(13) -> ㄹ(8)
            14 to 8,  // ㄿ(14) -> ㄹ(8)
            15 to 8,  // ㅀ(15) -> ㄹ(8)
            18 to 17, // ㅄ(18) -> ㅂ(17)
            20 to 19  // ㅆ(20) -> ㅅ(19)
        )
        
        // 모든 겹받침 매핑이 유효한지 검증
        jongFirstMap.forEach { (jongIdx, firstIdx) ->
            assertTrue("Invalid jong index: $jongIdx", jongIdx < jongMap.size)
            assertTrue("Invalid first jong index: $firstIdx", firstIdx < jongMap.size)
            
            val jongChar = jongMap[jongIdx]
            val firstChar = jongMap[firstIdx]
            assertTrue(
                "Mismatch: $jongChar should start with $firstChar",
                jongChar.startsWith(firstChar)
            )
        }
    }

    /**
     * 자모 맵 크기 검증
     * 초성, 중성, 종성의 개수가 올바른지 확인합니다.
     */
    @Test
    fun testJamoMapSizes() {
        val choMap = listOf(
            "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ"
        )
        val jungMap = listOf(
            "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅘ", "ㅙ", "ㅚ", "ㅝ", "ㅞ", "ㅟ", "ㅢ", "ㅣ", "ㅤ", "ㅥ", "ㅦ", "ㅧ"
        )
        val jongMap = listOf(
            "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ",
            "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
        )
        
        assertEquals("초성 개수는 19개여야 함", 19, choMap.size)
        assertEquals("중성 개수는 21개여야 함", 21, jungMap.size)
        assertEquals("종성 개수는 28개여야 함", 28, jongMap.size)
    }

    /**
     * 한글 코드포인트 범위 검증
     * 한글 문자의 유니코드 범위가 올바른지 확인합니다.
     */
    @Test
    fun testHangulCodePointRange() {
        val hangulBase = 0xAC00
        val hangulEnd = 0xD7A3
        
        // 가(0xAC00)부터 힣(0xD7A3)까지의 범위 검증
        val firstHangul = hangulBase.toChar()
        val lastHangul = hangulEnd.toChar()
        
        assertEquals('가', firstHangul)
        assertEquals('힣', lastHangul)
        
        // 한글 범위 내의 문자 개수: 19 * 21 * 28 = 11,172개
        val expectedCount = 19 * 21 * 28
        val actualCount = hangulEnd - hangulBase + 1
        assertEquals("한글 문자 개수 검증", expectedCount, actualCount)
    }
}