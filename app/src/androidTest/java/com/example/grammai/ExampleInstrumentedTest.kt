package com.example.grammai

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.grammai", appContext.packageName)
    }

    @Test
    fun testHangulCombinerBasicInput() {
        // HangulCombiner의 기본 입력 테스트
        val combiner = com.example.grammai.hangul.HangulCombiner()
        
        // 초성 입력 테스트
        val choResult = combiner.inputJaso("ㄱ")
        assertEquals("ㄱ", choResult.composing)
        assertEquals("", choResult.commit)
        
        // 중성 입력 테스트
        val jungResult = combiner.inputJaso("ㅏ")
        assertEquals("가", jungResult.composing)
        assertEquals("", jungResult.commit)
    }

    @Test
    fun testHangulCombinerInvalidInput() {
        // HangulCombiner의 잘못된 입력 처리 테스트
        val combiner = com.example.grammai.hangul.HangulCombiner()
        
        // 빈 문자열 입력
        val emptyResult = combiner.inputJaso("")
        assertEquals("", emptyResult.composing)
        assertEquals("", emptyResult.commit)
        
        // 영문 입력
        val englishResult = combiner.inputJaso("a")
        assertEquals("", englishResult.composing)
        assertEquals("a", englishResult.commit)
    }

    @Test
    fun testHangulCombinerBackspace() {
        // HangulCombiner의 백스페이스 처리 테스트
        val combiner = com.example.grammai.hangul.HangulCombiner()
        
        // 초성 입력 후 백스페이스
        combiner.inputJaso("ㄱ")
        combiner.handleBackspace()
        assertEquals("", combiner.getComposingText())
        
        // 초성+중성 입력 후 백스페이스
        combiner.inputJaso("ㄱ")
        combiner.inputJaso("ㅏ")
        combiner.handleBackspace()
        assertEquals("ㄱ", combiner.getComposingText())
    }
}