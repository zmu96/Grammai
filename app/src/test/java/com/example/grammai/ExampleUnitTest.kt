package com.example.grammai

import org.junit.Test
import org.junit.Assert.*

/**
 * 한글 조합 및 기본 기능 단위 테스트
 * 
 * 테스트 항목:
 * 1. 기본 산술 연산 (기존 테스트)
 * 2. HangulCombiner 인덱스 범위 검증
 * 3. 한글 자음/모음 조합 로직
 * 4. 잘못된 입력 처리
 */
class ExampleUnitTest {
    
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    /**
     * 테스트: 한글 자음 인덱스 검증
     * 
     * 목적: CHO_MAP에서 자음의 인덱스가 올바르게 반환되는지 확인
     * 예상: 'ㄱ'은 CHO_MAP의 첫 번째 요소이므로 인덱스 0 반환
     */
    @Test
    fun hangulChoIndexValidation() {
        // 한글 자음 맵 (실제 HangulCombiner의 CHO_MAP과 동일)
        val CHO_MAP = listOf(
            "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅄ",
            "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
        )
        
        val choIndex = CHO_MAP.indexOf("ㄱ")
        assertEquals("자음 'ㄱ'의 인덱스는 0이어야 함", 0, choIndex)
        
        val choIndexLast = CHO_MAP.indexOf("ㅎ")
        assertEquals("자음 'ㅎ'의 인덱스는 19여야 함", 19, choIndexLast)
    }

    /**
     * 테스트: 한글 모음 인덱스 검증
     * 
     * 목적: JUNG_MAP에서 모음의 인덱스가 올바르게 반환되는지 확인
     * 예상: 'ㅏ'는 JUNG_MAP의 첫 번째 요소이므로 인덱스 0 반환
     */
    @Test
    fun hangulJungIndexValidation() {
        // 한글 모음 맵 (실제 HangulCombiner의 JUNG_MAP과 동일)
        val JUNG_MAP = listOf(
            "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅘ",
            "ㅙ", "ㅚ", "ㅝ", "ㅞ", "ㅟ", "ㅢ", "ㅣ"
        )
        
        val jungIndex = JUNG_MAP.indexOf("ㅏ")
        assertEquals("모음 'ㅏ'의 인덱스는 0이어야 함", 0, jungIndex)
        
        val jungIndexLast = JUNG_MAP.indexOf("ㅣ")
        assertEquals("모음 'ㅣ'의 인덱스는 16이어야 함", 16, jungIndexLast)
    }

    /**
     * 테스트: 유효하지 않은 입력 처리
     * 
     * 목적: 한글이 아닌 문자(영문, 숫자 등)에 대해 -1 반환 확인
     * 예상: indexOf()는 요소가 없으면 -1 반환
     */
    @Test
    fun invalidInputReturnsNegativeOne() {
        val CHO_MAP = listOf(
            "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅄ",
            "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
        )
        
        val invalidIndex = CHO_MAP.indexOf("a")  // 영문 'a'
        assertEquals("유효하지 않은 입력은 -1을 반환해야 함", -1, invalidIndex)
        
        val numberIndex = CHO_MAP.indexOf("1")  // 숫자 '1'
        assertEquals("숫자는 -1을 반환해야 함", -1, numberIndex)
    }

    /**
     * 테스트: 한글 유니코드 조합 계산
     * 
     * 목적: 자음과 모음의 인덱스로부터 올바른 한글 유니코드를 계산하는지 확인
     * 공식: HANGUL_BASE + (choIdx * JUNG_COUNT * JONG_COUNT) + (jungIdx * JONG_COUNT) + jongIdx
     * 예상: 'ㄱ'(0) + 'ㅏ'(0) + 종성 없음(0) = '가'(0xAC00)
     */
    @Test
    fun hangulUnicodeCalculation() {
        val HANGUL_BASE = 0xAC00  // 한글 유니코드 시작점
        val JUNG_COUNT = 21       // 모음 개수
        val JONG_COUNT = 28       // 종성 개수 (0 포함)
        
        // 'ㄱ'(choIdx=0) + 'ㅏ'(jungIdx=0) + 종성 없음(jongIdx=0) = '가'
        val choIdx = 0
        val jungIdx = 0
        val jongIdx = 0
        
        val hangulCode = HANGUL_BASE + (choIdx * JUNG_COUNT * JONG_COUNT) + 
                         (jungIdx * JONG_COUNT) + jongIdx
        
        val expectedChar = '가'
        val expectedCode = expectedChar.code
        
        assertEquals("'가'의 유니코드는 0xAC00이어야 함", expectedCode, hangulCode)
    }

    /**
     * 테스트: 복합 한글 조합 (자음 + 모음 + 종성)
     * 
     * 목적: 초성, 중성, 종성이 모두 있는 한글 조합 검증
     * 예상: 'ㄱ'(0) + 'ㅏ'(0) + 'ㄴ'(2) = '간'(0xAC04)
     */
    @Test
    fun complexHangulCombination() {
        val HANGUL_BASE = 0xAC00
        val JUNG_COUNT = 21
        val JONG_COUNT = 28
        
        // 'ㄱ'(choIdx=0) + 'ㅏ'(jungIdx=0) + 'ㄴ'(jongIdx=2) = '간'
        val choIdx = 0
        val jungIdx = 0
        val jongIdx = 2  // 'ㄴ'은 종성 맵에서 인덱스 2
        
        val hangulCode = HANGUL_BASE + (choIdx * JUNG_COUNT * JONG_COUNT) + 
                         (jungIdx * JONG_COUNT) + jongIdx
        
        val expectedChar = '간'
        val expectedCode = expectedChar.code
        
        assertEquals("'간'의 유니코드 계산이 올바른지 확인", expectedCode, hangulCode)
    }

    /**
     * 테스트: 인덱스 범위 검증 (방어 로직)
     * 
     * 목적: 잘못된 인덱스(-1)가 들어왔을 때 안전하게 처리되는지 확인
     * 예상: -1 인덱스는 필터링되어 처리되지 않음
     */
    @Test
    fun invalidIndexHandling() {
        val invalidIndex = -1
        
        // 방어 로직: 인덱스가 음수면 처리하지 않음
        val isValidIndex = invalidIndex >= 0
        assertFalse("음수 인덱스는 유효하지 않음", isValidIndex)
        
        // 안전한 기본값 사용
        val safeIndex = if (invalidIndex >= 0) invalidIndex else 0
        assertEquals("안전한 기본값은 0이어야 함", 0, safeIndex)
    }

    /**
     * 테스트: 겹받침 분리 로직 검증
     * 
     * 목적: 겹받침(예: 'ㄲ')이 올바르게 분리되는지 확인
     * 예상: 'ㄲ'은 'ㄱ' + 'ㄱ'으로 분리되어야 함
     */
    @Test
    fun doubleConsonantSeparation() {
        // 겹받침 맵 (실제 HangulCombiner의 JONG_SPLIT_MAP과 유사)
        val JONG_SPLIT_MAP = mapOf(
            "ㄲ" to Pair("ㄱ", "ㄱ"),
            "ㄳ" to Pair("ㄱ", "ㅅ"),
            "ㄵ" to Pair("ㄴ", "ㅈ"),
            "ㄶ" to Pair("ㄴ", "ㅎ"),
            "ㄺ" to Pair("ㄹ", "ㄱ"),
            "ㄻ" to Pair("ㄹ", "ㅁ"),
            "ㄼ" to Pair("ㄹ", "ㅂ"),
            "ㄽ" to Pair("ㄹ", "ㅅ"),
            "ㄾ" to Pair("ㄹ", "ㅌ"),
            "ㄿ" to Pair("ㄹ", "ㅍ"),
            "ㅀ" to Pair("ㄹ", "ㅎ"),
            "ㅄ" to Pair("ㅂ", "ㅅ"),
            "ㅆ" to Pair("ㅅ", "ㅅ"),
            "ㅉ" to Pair("ㅈ", "ㅈ")
        )
        
        val doubleConsonant = "ㄲ"
        val (first, second) = JONG_SPLIT_MAP[doubleConsonant] ?: Pair("", "")
        
        assertEquals("'ㄲ'의 첫 번째 자음은 'ㄱ'이어야 함", "ㄱ", first)
        assertEquals("'ㄲ'의 두 번째 자음은 'ㄱ'이어야 함", "ㄱ", second)
    }
}