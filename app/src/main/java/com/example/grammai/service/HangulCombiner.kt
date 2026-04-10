package com.example.grammai.hangul

import android.util.Log

// Jamo 타입 정의
private const val TYPE_CHO = 0 // 초성
private const val TYPE_JUNG = 1 // 중성
private const val TYPE_JONG = 2 // 종성 (사용시 참고용)

// 한글 유니코드 기초 값 및 카운트
private const val HANGUL_BASE = 0xAC00
private const val JONG_COUNT = 28
private const val JUNG_COUNT = 21
private const val CHO_COUNT = 19

// 기본 자모 맵 (표준 순서)
private val CHO_MAP = listOf(
    "ㄱ","ㄲ","ㄴ","ㄷ","ㄸ","ㄹ","ㅁ","ㅂ","ㅃ","ㅅ","ㅆ","ㅇ","ㅈ","ㅉ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ"
)

private val JUNG_MAP = listOf(
    "ㅏ","ㅐ","ㅑ","ㅒ","ㅓ","ㅔ","ㅕ","ㅖ","ㅗ","ㅘ","ㅙ","ㅚ","ㅛ","ㅜ","ㅝ","ㅞ","ㅟ","ㅠ","ㅡ","ㅢ","ㅣ"
)

private val JONG_MAP = listOf(
    "", "ㄱ","ㄲ","ㄳ","ㄴ","ㄵ","ㄶ","ㄷ","ㄹ","ㄺ","ㄻ","ㄼ","ㄽ","ㄾ","ㄿ","ㅀ","ㅁ","ㅂ","ㅄ","ㅅ","ㅆ","ㅇ","ㅈ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ"
)

// 겹받침 생성 맵: (기존 jongIndex) -> (새 종성 인덱스) -> 합쳐진 종성 인덱
private val COMPLEX_JONG_MAP: Map<Int, Map<Int, Int>> = mapOf(
    1 to mapOf(19 to 3),
    4 to mapOf(22 to 5, 27 to 6),
    8 to mapOf(1 to 9, 16 to 10, 17 to 11, 19 to 12, 25 to 13, 26 to 14, 27 to 15),
    17 to mapOf(19 to 18)
)

// 겹받침 분리시 두 번째 받침의 JONG_MAP 인덱스
private val JONG_SPLIT_MAP = mapOf(
    3 to 19, 5 to 22, 6 to 27, 9 to 1, 10 to 16, 11 to 17, 12 to 19, 13 to 25, 14 to 26, 15 to 27, 18 to 19
)

// 이중 모음 결합 맵
private val COMPOSED_JUNG_MAP: Map<Pair<Int, Int>, Int> = mapOf(
    Pair(8, 0) to 9, Pair(8, 1) to 10, Pair(8, 20) to 11,
    Pair(13, 4) to 14, Pair(13, 5) to 15, Pair(13, 20) to 16,
    Pair(18, 20) to 19
)

// 이중 모음 분리
private val COMPOSED_JUNG_SPLIT: Map<Int, Int> = mapOf(
    9 to 8, 10 to 8, 11 to 8, 14 to 13, 15 to 13, 16 to 13, 19 to 18
)

// 겹받침 분리시 첫 번째 받침 인덱 (composed jong index -> first jong index)
// JONG_MAP 정의를 기준으로 정확한 인덱스를 대입하세요.
private val JONG_FIRST_MAP = mapOf(
    3 to 1,  // ㄳ (3) -> ㄱ (1)
    5 to 4,  // ㄵ (5) -> ㄴ (4)
    6 to 4,  // ㄶ (6) -> ㄴ (4)
    9 to 8,  // ㄺ (9) -> ㄹ (8)
    10 to 8, // ㄻ (10) -> ㄹ (8)
    11 to 8, // ㄼ (11) -> ㄹ (8)
    12 to 8, // ㄽ (12) -> ㄹ (8)
    13 to 8, // ㄾ (13) -> ㄹ (8)
    14 to 8, // ㄿ (14) -> ㄹ (8)
    15 to 8, // ㅀ (15) -> ㄹ (8)
    18 to 17 // ㅄ (18) -> ㅂ (17)
)

// 결과 데이터 클래스
data class HangulInputResult(val composing: String, val commit: String)

class HangulCombiner {

    private var choIndex: Int = -1
    private var jungIndex: Int = -1
    private var jongIndex: Int = 0 // 0 == 받침 없음

    init {
        // 초기화 시 JONG_FIRST_MAP의 완전성 검증
        validateJongFirstMap()
    }

    // -----------------------------------------
    private fun getCombinedChar(): Char? {
        if (choIndex != -1 && jungIndex != -1) {
            val unicodeIndex = choIndex * JUNG_COUNT * JONG_COUNT + jungIndex * JONG_COUNT + jongIndex
            return (HANGUL_BASE + unicodeIndex).toChar()
        }
        return null
    }

    private fun getCurrentComposingText(): String {
        val combined = getCombinedChar()
        if (combined != null) return combined.toString()
        if (choIndex != -1 && jungIndex == -1) return CHO_MAP.getOrNull(choIndex) ?: ""
        return ""
    }

    fun resetJaso() {
        choIndex = -1
        jungIndex = -1
        jongIndex = 0
    }

    // 수정됨: committedBuffer 사용 로직 제거
    fun getComposingText(): String = getCurrentComposingText()

    // 수정됨: committedBuffer 사용 로직 제거. 최종 완성된 글자만 반환하도록 수정
    fun finishComposing(): String? {
        val finalText = getCurrentComposingText()
        resetJaso()
        return if (finalText.isNotEmpty()) finalText else null
    }

    /**
     * JONG_FIRST_MAP의 완전성을 검증합니다.
     * JONG_SPLIT_MAP에 있는 모든 키가 JONG_FIRST_MAP에도 있는지 확인합니다.
     */
    private fun validateJongFirstMap() {
        val missingKeys = JONG_SPLIT_MAP.keys - JONG_FIRST_MAP.keys
        if (missingKeys.isNotEmpty()) {
            Log.w("HangulCombiner", "Missing JONG_FIRST_MAP entries for indices: $missingKeys")
        }
    }

    /**
     * 주어진 인덱스가 유효한 범위인지 검증합니다.
     */
    private fun isValidIndex(index: Int, mapSize: Int): Boolean {
        return index >= 0 && index < mapSize
    }

    /**
     * 자모 타입과 인덱스를 안전하게 반환합니다.
     * 유효하지 않은 입력은 Triple(-1, -1, -1)을 반환합니다.
     */
    private fun getJamoTypeAndIndex(jaso: String): Triple<Int, Int, Int> {
        if (jaso.length != 1) return Triple(-1, -1, -1)

        val choIdx = CHO_MAP.indexOf(jaso)
        val jungIdx = JUNG_MAP.indexOf(jaso)
        val jongIdx = JONG_MAP.indexOf(jaso)

        // 모음 검사 (우선순위 높음)
        if (isValidIndex(jungIdx, JUNG_MAP.size)) {
            return Triple(TYPE_JUNG, jungIdx, -1)
        }

        // 자음 검사
        if (isValidIndex(choIdx, CHO_MAP.size)) {
            return Triple(TYPE_CHO, choIdx, jongIdx)
        }

        // 유효하지 않은 입력
        return Triple(-1, -1, -1)
    }

    /**
     * 겹받침을 분리할 때 첫 번째 받침 인덱스를 안전하게 가져옵니다.
     */
    private fun getFirstJongIndex(composedJongIndex: Int): Int {
        // JONG_FIRST_MAP에서 직접 조회
        val firstJongIndex = JONG_FIRST_MAP[composedJongIndex]
        if (firstJongIndex != null && isValidIndex(firstJongIndex, JONG_MAP.size)) {
            return firstJongIndex
        }

        // 폴백: 겹받침 문자열의 첫 글자로부터 인덱스 계산
        if (isValidIndex(composedJongIndex, JONG_MAP.size)) {
            val composedJongChar = JONG_MAP[composedJongIndex]
            if (composedJongChar.isNotEmpty()) {
                val firstChar = composedJongChar.substring(0, 1)
                val fallbackIndex = JONG_MAP.indexOf(firstChar)
                if (isValidIndex(fallbackIndex, JONG_MAP.size)) {
                    Log.w("HangulCombiner", "Using fallback for jong index $composedJongIndex -> $fallbackIndex")
                    return fallbackIndex
                }
            }
        }

        // 최후의 폴백: 받침 없음 (0)
        Log.e("HangulCombiner", "Failed to get first jong index for $composedJongIndex, using 0")
        return 0
    }

    companion object {
        fun getShiftedHangulJaso(jaso: String): String {
            return when (jaso) {
                "ㄱ" -> "ㄲ"; "ㄷ" -> "ㄸ"; "ㅂ" -> "ㅃ"; "ㅅ" -> "ㅆ"; "ㅈ" -> "ㅉ"
                "ㅐ" -> "ㅒ"; "ㅔ" -> "ㅖ"; else -> jaso
            }
        }
        fun getUnshiftedHangulJaso(jaso: String): String? {
            return when (jaso) {
                "ㄲ" -> "ㄱ"; "ㄸ" -> "ㄷ"; "ㅃ" -> "ㅂ"; "ㅆ" -> "ㅅ"; "ㅉ" -> "ㅈ"
                "ㅒ" -> "ㅐ"; "ㅖ" -> "ㅔ"; else -> null
            }
        }
    }

    fun inputJaso(jaso: String): HangulInputResult {
        val (newType, newIdx, newJongIdx) = getJamoTypeAndIndex(jaso)
        var committedText = ""

        // 비-한글 문자 입력 시, 현재 조합 중인 글자를 확정하지 않고 바로 커밋합니다.
        if (newType == -1) {
            committedText = jaso
            return HangulInputResult(getCurrentComposingText(), committedText)
        }

        val combinedChar = getCombinedChar()
        if (combinedChar != null) {
            when (newType) {
                TYPE_CHO -> {
                    if (jongIndex == 0) {
                        if (newJongIdx > 0) {
                            jongIndex = newJongIdx
                            return HangulInputResult(getCurrentComposingText(), "")
                        } else {
                            committedText = combinedChar.toString()
                            resetJaso()
                            choIndex = newIdx
                            return HangulInputResult(getCurrentComposingText(), committedText)
                        }
                    } else {
                        val complex = COMPLEX_JONG_MAP[jongIndex]?.get(newJongIdx)
                        if (complex != null) {
                            jongIndex = complex
                            return HangulInputResult(getCurrentComposingText(), "")
                        } else {
                            committedText = combinedChar.toString()
                            resetJaso()
                            choIndex = newIdx
                            return HangulInputResult(getCurrentComposingText(), committedText)
                        }
                    }
                }
                TYPE_JUNG -> {
                    if (jongIndex > 0) {
                        val splitSecondJongIndex = JONG_SPLIT_MAP[jongIndex]

                        // 겹받침 분리 로직 (예: '앉' + 'ㅣ' -> '안' + '지')
                        if (splitSecondJongIndex != null) {

                            // 변경된 로직: 안전한 getFirstJongIndex 함수 사용
                            val firstJongIndex = getFirstJongIndex(jongIndex)

                            // 방어 로직: 조합기 상태를 첫 번째 받침 인덱스로 변경
                            jongIndex = firstJongIndex

                            // 2. '안'을 확정 글자로 계산 및 커밋 텍스트 설정 (jongIndex는 이제 정확함)
                            val committedUnicodeIndex = choIndex * JUNG_COUNT * JONG_COUNT + jungIndex * JONG_COUNT + jongIndex
                            committedText = (HANGUL_BASE + committedUnicodeIndex).toChar().toString()

                            // 3. 두 번째 받침 ('ㅈ', 'ㅂ' 등)을 다음 글자의 초성으로 이동 (JONG_SPLIT_MAP 사용)
                            val secondJongIndex = splitSecondJongIndex
                            if (isValidIndex(secondJongIndex, JONG_MAP.size)) {
                                val secondJongChar = JONG_MAP[secondJongIndex]
                                val newChoIndex = CHO_MAP.indexOf(secondJongChar).takeIf { it >= 0 } ?: CHO_MAP.indexOf("ㅇ")

                                // 4. Combiner 상태 리셋 후 새 글자 조합 시작 ('지')
                                resetJaso()
                                choIndex = newChoIndex
                                jungIndex = newIdx
                            } else {
                                Log.e("HangulCombiner", "Invalid second jong index: $secondJongIndex")
                                resetJaso()
                                choIndex = CHO_MAP.indexOf("ㅇ")
                                jungIndex = newIdx
                            }

                            return HangulInputResult(getCurrentComposingText(), committedText)

                        } else {
                            // 홑받침 분리 로직 (예: '간' + 'ㅣ' -> '가' + '니')
                            if (isValidIndex(jongIndex, JONG_MAP.size)) {
                                val movedChoChar = JONG_MAP[jongIndex]
                                val movedChoIndex = CHO_MAP.indexOf(movedChoChar).takeIf { it >= 0 } ?: CHO_MAP.indexOf("ㅇ")
                                val committedUnicodeIndex = choIndex * JUNG_COUNT * JONG_COUNT + jungIndex * JONG_COUNT + 0
                                committedText = (HANGUL_BASE + committedUnicodeIndex).toChar().toString()
                                resetJaso()
                                choIndex = movedChoIndex
                                jungIndex = newIdx
                            } else {
                                Log.e("HangulCombiner", "Invalid jong index for split: $jongIndex")
                                val committedUnicodeIndex = choIndex * JUNG_COUNT * JONG_COUNT + jungIndex * JONG_COUNT + 0
                                committedText = (HANGUL_BASE + committedUnicodeIndex).toChar().toString()
                                resetJaso()
                                choIndex = CHO_MAP.indexOf("ㅇ")
                                jungIndex = newIdx
                            }
                            return HangulInputResult(getCurrentComposingText(), committedText)
                        }
                    } else { // jongIndex == 0 (받침이 없는 상태에서 모음 추가)
                        val existingJung = jungIndex
                        val combined = COMPOSED_JUNG_MAP[Pair(existingJung, newIdx)]
                        if (combined != null) {
                            jungIndex = combined
                            return HangulInputResult(getCurrentComposingText(), "")
                        } else {
                            // 수정된 로직 (이전 글자 확정 후 모음 단독 커밋)

                            // 1. 현재 조합 중인 글자(예: '가')를 확정 문자열에 추가
                            committedText = combinedChar.toString()

                            // 2. 조합기 상태를 리셋하여 다음 입력을 준비
                            resetJaso()

                            // 3. 새로 입력된 모음(예: 'ㅑ')을 모음 단독으로 확정 문자열에 추가
                            val vowelCommit = JUNG_MAP.getOrNull(newIdx) ?: jaso
                            committedText += vowelCommit

                            // 4. 조합 중인 텍스트 없이 (reset 했으므로) 최종 확정 문자열을 반환
                            return HangulInputResult("", committedText)
                        }
                    }
                }
            }
        }

        if (choIndex != -1) {
            if (jungIndex == -1) {
                when (newType) {
                    TYPE_JUNG -> {
                        jungIndex = newIdx
                        return HangulInputResult(getCurrentComposingText(), "")
                    }
                    TYPE_CHO -> {
                        committedText = CHO_MAP.getOrNull(choIndex) ?: ""
                        resetJaso()
                        choIndex = newIdx
                        return HangulInputResult(getCurrentComposingText(), committedText)
                    }
                }
            }
        } else { // choIndex == -1 (조합기 상태가 완전히 비어있을 때)
            when (newType) {
                TYPE_CHO -> {
                    // 초성(자음)이 단독으로 들어오면 조합 시작 (예: 'ㄱ' 입력)
                    choIndex = newIdx
                    return HangulInputResult(getCurrentComposingText(), "")
                }
                TYPE_JUNG -> {
                    // 모음 단독 커밋을 유지합니다. (ㅠㅠ 입력 등)
                    val committedText = JUNG_MAP.getOrNull(newIdx) ?: jaso
                    resetJaso()
                    return HangulInputResult("", committedText)
                }
            }
        }

        return HangulInputResult(getCurrentComposingText(), committedText)
    }

    fun handleBackspace() {
        if (jongIndex > 0) {
            val splitSecond = JONG_SPLIT_MAP[jongIndex]
            if (splitSecond != null) {
                val firstJongIndex = getFirstJongIndex(jongIndex)
                jongIndex = firstJongIndex
            } else {
                jongIndex = 0
            }
            return
        }

        if (jungIndex != -1) {
            val split = COMPOSED_JUNG_SPLIT[jungIndex]
            if (split != null) jungIndex = split
            else jungIndex = -1
            return
        }

        if (choIndex != -1) {
            choIndex = -1
            return
        }
    }
}