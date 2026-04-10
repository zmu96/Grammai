package com.example.grammai.hangul

import android.util.Log

// Jamo 타입 정의
private const val TYPE_CHO = 0 // 초성
private const val TYPE_JUNG = 1 // 중성

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

    private fun getJamoTypeAndIndex(jaso: String): Triple<Int, Int, Int> {
        if (jaso.length != 1) return Triple(-1, -1, -1)
        val choIdx = CHO_MAP.indexOf(jaso)
        val jungIdx = JUNG_MAP.indexOf(jaso)
        val jongIdx = JONG_MAP.indexOf(jaso)
        if (jungIdx != -1) return Triple(TYPE_JUNG, jungIdx, -1)
        if (choIdx != -1) return Triple(TYPE_CHO, choIdx, jongIdx)
        return Triple(-1, -1, -1)
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

                            // 변경된 로직: JONG_FIRST_MAP을 사용하여 첫 번째 받침 인덱스를 명시적으로 가져옴
                            val firstJongIndex = JONG_FIRST_MAP[jongIndex] ?: run {
                                // 🚨 맵에 없을 경우 (예외 상황) 기존의 폴백 로직 사용
                                val firstJongCharFallback = JONG_MAP.getOrNull(jongIndex)?.substring(0, 1) ?: ""
                                JONG_MAP.indexOf(firstJongCharFallback).takeIf { it >= 0 } ?: 0
                            }

                            // 🚨 방어 로직: 조합기 상태를 첫 번째 받침 인덱스로 변경
                            jongIndex = firstJongIndex // 'ㄵ' (5) -> 'ㄴ' (4)로 변경

                            // 2. '안'을 확정 글자로 계산 및 커밋 텍스트 설정 (jongIndex는 이제 정확함)
                            val committedUnicodeIndex = choIndex * JUNG_COUNT * JONG_COUNT + jungIndex * JONG_COUNT + jongIndex
                            committedText = (HANGUL_BASE + committedUnicodeIndex).toChar().toString()

                            // 3. 두 번째 받침 ('ㅈ', 'ㅂ' 등)을 다음 글자의 초성으로 이동 (JONG_SPLIT_MAP 사용)
                            // ... (나머지 로직은 기존과 동일)
                            val secondJongIndex = splitSecondJongIndex
                            val secondJongChar = JONG_MAP.getOrNull(secondJongIndex) ?: ""
                            val newChoIndex = CHO_MAP.indexOf(secondJongChar).takeIf { it >= 0 } ?: CHO_MAP.indexOf("ㅇ")

                            // 4. Combiner 상태 리셋 후 새 글자 조합 시작 ('지')
                            resetJaso()
                            choIndex = newChoIndex
                            jungIndex = newIdx

                            return HangulInputResult(getCurrentComposingText(), committedText)

                        } else {
                            // 홑받침 분리 로직 (예: '간' + 'ㅣ' -> '가' + '니')
                            val movedChoChar = JONG_MAP.getOrNull(jongIndex) ?: ""
                            val movedChoIndex = CHO_MAP.indexOf(movedChoChar).takeIf { it >= 0 } ?: CHO_MAP.indexOf("ㅇ")
                            val committedUnicodeIndex = choIndex * JUNG_COUNT * JONG_COUNT + jungIndex * JONG_COUNT + 0
                            committedText = (HANGUL_BASE + committedUnicodeIndex).toChar().toString()
                            resetJaso()
                            choIndex = movedChoIndex
                            jungIndex = newIdx
                            return HangulInputResult(getCurrentComposingText(), committedText)
                        }
                    } else { // jongIndex == 0 (받침이 없는 상태에서 모음 추가)
                        val existingJung = jungIndex
                        val combined = COMPOSED_JUNG_MAP[Pair(existingJung, newIdx)]
                        if (combined != null) {
                            jungIndex = combined
                            return HangulInputResult(getCurrentComposingText(), "")
                        } else {
                            // 🚨 수정된 로직 (이전 글자 확정 후 'ㅇ'을 붙여 새 글자를 만드는 표준 로직 대신, 모음 단독 커밋)

                            // 1. 현재 조합 중인 글자(예: '가')를 확정 문자열에 추가
                            committedText = combinedChar.toString()

                            // 2. 조합기 상태를 리셋하여 다음 입력을 준비
                            resetJaso()

                            // 3. 새로 입력된 모음(예: 'ㅑ')을 모음 단독으로 확정 문자열에 추가
                            val vowelCommit = JUNG_MAP.getOrNull(newIdx) ?: jaso
                            committedText += vowelCommit // committedText는 이제 "가ㅑ"가 됨

                            // 4. 조합 중인 텍스트 없이 (reset 했으므로) 최종 확정 문자열을 반환
                            return HangulInputResult("", committedText)

                            /* // ❌ 원래 표준 IME 로직 (문제의 원인):
                            committedText = combinedChar.toString()
                            resetJaso()
                            choIndex = CHO_MAP.indexOf("ㅇ") // 이 코드가 새로운 글자 '야'를 만들었음
                            jungIndex = newIdx
                            return HangulInputResult(getCurrentComposingText(), committedText)
                            */
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
                    // 🚨 이 부분은 이전처럼 모음 단독 커밋을 유지합니다. (ㅠㅠ 입력 등)
                    val committedText = JUNG_MAP.getOrNull(newIdx) ?: jaso
                    resetJaso() // 조합기 상태 리셋
                    return HangulInputResult("", committedText) // 조합 텍스트 없이 확정 문자만 반환
                }
            }
        }

        return HangulInputResult(getCurrentComposingText(), committedText)
    }

    fun handleBackspace() {
        if (jongIndex > 0) {
            val splitSecond = JONG_SPLIT_MAP[jongIndex]
            if (splitSecond != null) {
                val firstJongChar = JONG_MAP.getOrNull(jongIndex)?.substring(0, 1) ?: ""
                val firstJongIndex = JONG_MAP.indexOf(firstJongChar).takeIf { it >= 0 } ?: 0
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
```