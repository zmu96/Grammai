package com.example.grammai

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import com.example.grammai.hangul.HangulCombiner

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
        val combiner = HangulCombiner()
        
        // 'ㄱ' 입력 (초성)
        val result1 = combiner.inputJaso("ㄱ")
        assertEquals("ㄱ", result1.composing)
        assertEquals("", result1.commit)
        
        // 'ㅏ' 입력 (중성) -> '가' 조합
        val result2 = combiner.inputJaso("ㅏ")
        assertEquals("가", result2.composing)
        assertEquals("", result2.commit)
        
        // 'ㄴ' 입력 (종성) -> '간' 조합
        val result3 = combiner.inputJaso("ㄴ")
        assertEquals("간", result3.composing)
        assertEquals("", result3.commit)
    }

    @Test
    fun testHangulCombinerConsonantTransition() {
        val combiner = HangulCombiner()
        
        // '가' 조합
        combiner.inputJaso("ㄱ")
        combiner.inputJaso("ㅏ")
        
        // 새로운 초성 'ㄴ' 입력 -> '가' 확정, '나' 시작
        val result = combiner.inputJaso("ㄴ")
        assertEquals("나", result.composing)
        assertEquals("가", result.commit)
    }

    @Test
    fun testHangulCombinerBackspace() {
        val combiner = HangulCombiner()
        
        // '간' 조합
        combiner.inputJaso("ㄱ")
        combiner.inputJaso("ㅏ")
        combiner.inputJaso("ㄴ")
        
        // 백스페이스 -> 종성 제거
        combiner.handleBackspace()
        assertEquals("가", combiner.getComposingText())
        
        // 백스페이스 -> 중성 제거
        combiner.handleBackspace()
        assertEquals("ㄱ", combiner.getComposingText())
        
        // 백스페이스 -> 초성 제거
        combiner.handleBackspace()
        assertEquals("", combiner.getComposingText())
    }

    @Test
    fun testHangulCombinerComplexJong() {
        val combiner = HangulCombiner()
        
        // '갈' 조합 (ㄱ + ㅏ + ㄹ)
        combiner.inputJaso("ㄱ")
        combiner.inputJaso("ㅏ")
        combiner.inputJaso("ㄹ")
        assertEquals("갈", combiner.getComposingText())
        
        // 'ㄱ' 추가 -> '갉' 조합 (겹받침 ㄺ)
        val result = combiner.inputJaso("ㄱ")
        assertEquals("갉", result.composing)
        assertEquals("", result.commit)
    }

    @Test
    fun testHangulCombinerDoubleVowel() {
        val combiner = HangulCombiner()
        
        // 'ㄱ' + 'ㅗ' = '고'
        combiner.inputJaso("ㄱ")
        combiner.inputJaso("ㅗ")
        assertEquals("고", combiner.getComposingText())
        
        // 'ㅏ' 추가 -> '고' + 'ㅏ' (이중 모음 불가, 모음 단독 커밋)
        val result = combiner.inputJaso("ㅏ")
        assertEquals("", result.composing)
        assertEquals("고ㅏ", result.commit)
    }

    @Test
    fun testHangulCombinerReset() {
        val combiner = HangulCombiner()
        
        // '가' 조합
        combiner.inputJaso("ㄱ")
        combiner.inputJaso("ㅏ")
        assertEquals("가", combiner.getComposingText())
        
        // 리셋
        combiner.resetJaso()
        assertEquals("", combiner.getComposingText())
    }

    @Test
    fun testHangulCombinerFinishComposing() {
        val combiner = HangulCombiner()
        
        // '가' 조합
        combiner.inputJaso("ㄱ")
        combiner.inputJaso("ㅏ")
        
        // 조합 완료
        val result = combiner.finishComposing()
        assertEquals("가", result)
        assertEquals("", combiner.getComposingText())
    }

    @Test
    fun testHangulCombinerNonHangulInput() {
        val combiner = HangulCombiner()
        
        // '가' 조합
        combiner.inputJaso("ㄱ")
        combiner.inputJaso("ㅏ")
        
        // 영문 'a' 입력 -> '가' 확정, 'a' 커밋
        val result = combiner.inputJaso("a")
        assertEquals("", result.composing)
        assertEquals("가a", result.commit)
    }
}
```