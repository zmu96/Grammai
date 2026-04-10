package com.example.grammai.ime

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import com.example.grammai.R
import com.example.grammai.hangul.HangulCombiner

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


/**
 * 스레드 안전한 텍스트 버퍼
 * sentenceBuffer의 동시성 문제를 해결하기 위해 ReentrantReadWriteLock 사용
 */
private class ThreadSafeTextBuffer {
    private val buffer = StringBuilder()
    private val lock = ReentrantReadWriteLock()

    fun append(text: String) {
        lock.write {
            buffer.append(text)
        }
    }

    fun deleteCharAt(index: Int) {
        lock.write {
            if (index >= 0 && index < buffer.length) {
                buffer.deleteCharAt(index)
            }
        }
    }

    fun clear() {
        lock.write {
            buffer.clear()
        }
    }

    fun toString(block: (String) -> Unit) {
        lock.read {
            block(buffer.toString())
        }
    }

    fun isEmpty(): Boolean {
        return lock.read {
            buffer.isEmpty()
        }
    }

    fun isNotEmpty(): Boolean {
        return lock.read {
            buffer.isNotEmpty()
        }
    }

    fun length(): Int {
        return lock.read {
            buffer.length
        }
    }
}


class CorrectionImeService : InputMethodService(), View.OnClickListener {

    private lateinit var inputView: View
    private lateinit var hangulLayout: LinearLayout
    private lateinit var englishLayout: LinearLayout
    private lateinit var symbolLayout1: LinearLayout
    private lateinit var symbolLayout2: LinearLayout

    private lateinit var btnMemo: Button
    private lateinit var btnCorrect: Button

    private lateinit var memoLayout: LinearLayout
    private lateinit var memoEditText: android.widget.EditText

    private lateinit var shiftHangulBtn: Button
    private lateinit var shiftEnglishBtn: Button

    private var isMemoMode = false

    // 🔥 스레드 안전한 버퍼로 변경
    private val sentenceBuffer = ThreadSafeTextBuffer()

    private val combiner = HangulCombiner()

    private var isHangulMode = true
    private var isSymbolMode = false
    private var symbolPage = 1

    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftTapTime = 0L

    // 🔥 네트워크 타임아웃 설정 추가
    private companion object {
        private const val NETWORK_TIMEOUT_SECONDS = 10L
        private const val DOUBLE_TAP_DELAY_MS = 400L
        private const val UNSET_TIMESTAMP = 0L
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        combiner.resetJaso()
    }

    override fun onFinishInput() {
        super.onFinishInput()

        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        combiner.resetJaso()

        sentenceBuffer.clear()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        val ic = currentInputConnection
        ic?.finishComposingText()
        combiner.resetJaso()
    }


    override fun onCreate() {
        super.onCreate()
    }

    override fun onCreateInputView(): View {

        val inflater = LayoutInflater.from(this)

        inputView = inflater.inflate(R.layout.ime_keyboard_all_modes, null)

        hangulLayout = inputView.findViewById(R.id.layout_hangul)
        englishLayout = inputView.findViewById(R.id.layout_english)
        symbolLayout1 = inputView.findViewById(R.id.layout_symbol1)
        symbolLayout2 = inputView.findViewById(R.id.layout_symbol2)

        shiftHangulBtn = inputView.findViewById(R.id.key_h_shift)
        shiftEnglishBtn = inputView.findViewById(R.id.key_e_shift)


        btnMemo = inputView.findViewById(R.id.btn_memo)
        btnCorrect = inputView.findViewById(R.id.btn_correct)

        btnCorrect.setOnClickListener(this)

        memoLayout = inputView.findViewById(R.id.layout_memo)
        memoEditText = inputView.findViewById(R.id.edit_memo)

        btnMemo.setOnClickListener {
            if (isMemoMode) {
                hideMemo()
            } else {
                showMemo()
            }
        }



        fun saveBaseKeyText(view: View) {
            if (view is LinearLayout) {
                for (i in 0 until view.childCount) {
                    saveBaseKeyText(view.getChildAt(i))
                }
            } else if (view is Button) {
                if (view.tag == null) {
                    val text = view.text.toString()
                    view.tag = if (!isHangulMode && text.length == 1 && text[0].isLetter()) {
                        text.lowercase()
                    } else {
                        text
                    }
                }
            }

        }

        saveBaseKeyText(inputView)



        bindButtons(hangulLayout)
        bindButtons(englishLayout)
        bindButtons(symbolLayout1)
        bindButtons(symbolLayout2)

        updateLayoutVisibility()
        updateButtonText(inputView)

        return inputView
    }

    private fun updateShiftButtonUI() {
        val isOn = isShifted || isCapsLock

        val activeColor = getColor(R.color.key_shift_active)
        val normalColor = getColor(R.color.key_function_background)

        shiftHangulBtn.setBackgroundColor(if (isOn) activeColor else normalColor)
        shiftEnglishBtn.setBackgroundColor(if (isOn) activeColor else normalColor)
    }


    /**
     * 🔥 실제 입력창의 텍스트와 sentenceBuffer를 동기화
     * InputConnection에서 실제 텍스트를 추출하여 버퍼를 업데이트
     */
    private fun syncSentenceBufferWithEditor() {
        val ic = currentInputConnection ?: return
        try {
            val extracted = ic.getExtractedText(
                android.view.inputmethod.ExtractedTextRequest(),
                0
            ) ?: return

            val currentText = extracted.text?.toString() ?: ""

            sentenceBuffer.clear()
            if (currentText.isNotEmpty()) {
                sentenceBuffer.append(currentText)
            }
        } catch (e: Exception) {
            Log.e("CorrectionImeService", "Failed to sync sentence buffer", e)
            sentenceBuffer.clear()
        }
    }


    private fun showMemo() {
        syncSentenceBufferWithEditor()
        isMemoMode = true

        commitRemaining()
        currentInputConnection?.finishComposingText()

        hangulLayout.visibility = View.GONE
        englishLayout.visibility = View.GONE
        symbolLayout1.visibility = View.GONE
        symbolLayout2.visibility = View.GONE

        memoLayout.visibility = View.VISIBLE

        sentenceBuffer.toString { text ->
            memoEditText.setText(text)
            memoEditText.setSelection(memoEditText.text.length)
        }
    }

    private fun hideMemo() {
        isMemoMode = false

        memoLayout.visibility = View.GONE

        updateLayoutVisibility()
    }


    private fun updateLayoutVisibility() {
        hangulLayout.visibility = if (isHangulMode && !isSymbolMode) View.VISIBLE else View.GONE
        englishLayout.visibility = if (!isHangulMode && !isSymbolMode) View.VISIBLE else View.GONE

        symbolLayout1.visibility = if (isSymbolMode && symbolPage == 1) View.VISIBLE else View.GONE
        symbolLayout2.visibility = if (isSymbolMode && symbolPage == 2) View.VISIBLE else View.GONE
    }

    private fun bindButtons(view: View) {
        if (view is LinearLayout) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is LinearLayout) bindButtons(child)
                else if (child is Button) child.setOnClickListener(this)
            }
        }
    }


    override fun onClick(v: View?) {

        val btn = v as? Button ?: return
        val text = btn.text.toString()
        val ic = currentInputConnection ?: return


        if (btn.id in listOf(
                R.id.key_h_hangul_english, R.id.key_e_hangul_english,
                R.id.key_s1_hangul_english, R.id.key_s2_mode_change,
                R.id.key_h_symbol_change, R.id.key_e_symbol_change,
                R.id.key_s1_symbol_change, R.id.key_s2_symbol_change,
                R.id.key_s1_hangul_keyboard, R.id.key_s2_hangul_keyboard
            )) {

            val composing = combiner.getComposingText()
            if (composing.isNotEmpty()) {
                ic.commitText(composing, 1)
                sentenceBuffer.append(composing)
            }
            ic.finishComposingText()
            combiner.resetJaso()

        }

        when (btn.id) {

            R.id.key_h_shift, R.id.key_e_shift -> {

                val now = System.currentTimeMillis()

                if (isCapsLock) {
                    isCapsLock = false
                    isShifted = false
                    lastShiftTapTime = UNSET_TIMESTAMP
                }
                else if (lastShiftTapTime != UNSET_TIMESTAMP && now - lastShiftTapTime < DOUBLE_TAP_DELAY_MS) {
                    isCapsLock = true
                    isShifted = true
                    lastShiftTapTime = UNSET_TIMESTAMP
                }
                else if (isShifted) {
                    isShifted = false
                    lastShiftTapTime = UNSET_TIMESTAMP
                }
                else {
                    isShifted = true
                    lastShiftTapTime = now
                }

                updateShiftButtonUI()
                updateButtonText(inputView)
                return
            }

            R.id.key_h_hangul_english, R.id.key_e_hangul_english,
            R.id.key_s1_hangul_english, R.id.key_s2_mode_change -> {
                ic.finishComposingText()
                isHangulMode = !isHangulMode
                isSymbolMode = false
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
                return
            }

            R.id.key_h_symbol_change, R.id.key_e_symbol_change -> {
                ic.finishComposingText()
                isSymbolMode = true
                symbolPage = 1
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
                return
            }

            R.id.key_s1_symbol_change -> {
                ic.finishComposingText()
                symbolPage = 2
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
                return
            }

            R.id.key_s2_symbol_change -> {
                ic.finishComposingText()
                symbolPage = 1
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
                return
            }

            R.id.key_h_delete, R.id.key_e_delete, R.id.key_s1_delete, R.id.key_s2_delete -> {
                handleDelete(ic)
                return
            }

            R.id.key_h_space, R.id.key_e_space, R.id.key_s1_space, R.id.key_s2_space -> {
                handleSpace(ic)
                return
            }

            R.id.key_h_enter, R.id.key_e_enter, R.id.key_s1_enter, R.id.key_s2_enter -> {
                handleEnter(ic)
                return
            }

            R.id.key_h_comma, R.id.key_e_comma, R.id.key_s1_comma2, R.id.key_s2_comma -> {
                commitRemaining()
                ic.commitText(",", 1)
                sentenceBuffer.append(",")
                return
            }

            R.id.key_h_period, R.id.key_e_period, R.id.key_s1_period, R.id.key_s2_period -> {
                commitRemaining()
                ic.commitText(".", 1)
                sentenceBuffer.append(".")
                return
            }

            R.id.key_s1_hangul_keyboard, R.id.key_s2_hangul_keyboard -> {
                ic.finishComposingText()
                isHangulMode = true
                isSymbolMode = false
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
                return
            }

            R.id.btn_correct -> {
                val composing = combiner.getComposingText()
                if (composing.isNotEmpty()) {
                    ic.commitText(composing, 1)
                    sentenceBuffer.append(composing)
                    combiner.resetJaso()
                }
                ic.finishComposingText()

                sentenceBuffer.toString { originalSentence ->
                    if (originalSentence.isBlank()) return@toString

                    requestCorrectionFromServer(originalSentence) { corrected ->
                        val ic = currentInputConnection ?: return@requestCorrectionFromServer
                        try {
                            ic.deleteSurroundingText(originalSentence.length, 0)
                            ic.commitText(corrected, 1)

                            sentenceBuffer.clear()
                            sentenceBuffer.append(corrected)
                        } catch (e: Exception) {
                            Log.e("CorrectionImeService", "Failed to apply correction", e)
                        }
                    }
                }

                return
            }

            else -> {
                handleCharacter(text, ic)
            }
        }
    }

    /* =====================================================
   🔥 서버 교정 요청 함수 (보안 강화 버전)
   ===================================================== */

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 🔥 서버에 교정 요청
     * BuildConfig를 통해 서버 URL을 외부화하고, 타임아웃을 설정함
     */
    private fun requestCorrectionFromServer(
        originalText: String,
        onResult: (String) -> Unit
    ) {
        // 입력 검증: 텍스트 길이 제한
        val sanitizedText = originalText.take(1000)

        val json = JSONObject()
        json.put("text", sanitizedText)

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        // 🔥 BuildConfig에서 서버 URL을 읽음 (하드코딩 제거)
        val serverUrl = try {
            val buildConfigClass = Class.forName("com.example.grammai.BuildConfig")
            val field = buildConfigClass.getField("CORRECTION_SERVER_URL")
            field.get(null) as? String ?: "http://115.23.150.161:8000"
        } catch (e: Exception) {
            Log.w("CorrectionImeService", "Failed to read BuildConfig, using default URL", e)
            "http://115.23.150.161:8000"
        }

        val request = Request.Builder()
            .url("$serverUrl/correct")
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("CorrectionImeService", "Network error: ${e.message}")

                mainHandler.post {
                    onResult(originalText)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val corrected = try {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        JSONObject(responseBody).getString("corrected")
                    } else {
                        Log.w("CorrectionImeService", "Server returned status: ${response.code}")
                        originalText
                    }
                } catch (e: Exception) {
                    Log.e("CorrectionImeService", "Failed to parse response", e)
                    originalText
                }

                mainHandler.post {
                    onResult(corrected)
                }
            }
        })
    }


    private fun handleDelete(ic: InputConnection) {
        val composing = combiner.getComposingText()
        if (composing.isNotEmpty()) {
            combiner.handleBackspace()
            ic.setComposingText(combiner.getComposingText(), 1)
            return
        }
        ic.deleteSurroundingText(1, 0)

        if (sentenceBuffer.isNotEmpty()) {
            val len = sentenceBuffer.length()
            if (len > 0) {
                sentenceBuffer.deleteCharAt(len - 1)
            }
        }
    }

    private fun handleSpace(ic: InputConnection) {
        commitRemaining()
        ic.commitText(" ", 1)

        sentenceBuffer.append(" ")
    }

    private fun handleEnter(ic: InputConnection) {
        commitRemaining()
        ic.commitText("\n", 1)

        sentenceBuffer.append("\n")
    }


    private fun handleCharacter(text: String, ic: InputConnection) {
        var input = text

        if (isShifted || isCapsLock) {
            input = if (isHangulMode)
                HangulCombiner.getShiftedHangulJaso(text)
            else
                text.uppercase()
        }

        if (!isHangulMode || isSymbolKey(input)) {
            commitRemaining()
            ic.commitText(input, 1)
            sentenceBuffer.append(input)

            if (isShifted && !isCapsLock) {
                isShifted = false
                updateButtonText(inputView)
                updateShiftButtonUI()
            }
            return
        }


        val result = combiner.inputJaso(input)
        if (result.commit.isNotEmpty()) {
            ic.commitText(result.commit, 1)

            sentenceBuffer.append(result.commit)
        }
        val composingText = result.composing
        if (composingText.isNotEmpty()) ic.setComposingText(composingText, 1)
        else ic.finishComposingText()

        if (isShifted && !isCapsLock) {
            isShifted = false
            updateButtonText(inputView)
            updateShiftButtonUI()
        }
    }



    private fun commitRemaining() {
        val ic = currentInputConnection ?: return
        val remain = combiner.finishComposing()

        if (remain != null) {
            ic.commitText(remain, 1)

            sentenceBuffer.append(remain)
        }

        combiner.resetJaso()
    }

    private fun isSymbolKey(text: String): Boolean {
        if (text.length != 1) return false

        val c = text[0]

        return !c.isLetterOrDigit() &&
                Character.UnicodeBlock.of(c) != Character.UnicodeBlock.HANGUL_SYLLABLES &&
                Character.UnicodeBlock.of(c) != Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
    }



    private fun updateButtonText(view: View) {
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is LinearLayout) updateButtonText(child)
                else if (child is Button) {


                    if (child.id in listOf(
                            R.id.key_h_delete, R.id.key_e_delete, R.id.key_s1_delete, R.id.key_s2_delete,
                            R.id.key_h_space, R.id.key_e_space, R.id.key_s1_space, R.id.key_s2_space,
                            R.id.key_h_enter, R.id.key_e_enter, R.id.key_s1_enter, R.id.key_s2_enter,
                            R.id.key_h_symbol_change, R.id.key_e_symbol_change,
                            R.id.key_s1_symbol_change, R.id.key_s2_symbol_change,
                            R.id.key_h_hangul_english, R.id.key_e_hangul_english,
                            R.id.key_s1_hangul_english, R.id.key_s2_mode_change
                        )
                    ) continue

                    if (isSymbolMode) {
                        when (child.id) {
                            R.id.key_s1_symbol_change -> child.text = "1/2"
                            R.id.key_s2_symbol_change -> child.text = "2/2"
                        }
                        continue
                    }

                    val baseText = child.tag as? String ?: child.text.toString()

                    val newText = when {
                        isShifted && isHangulMode ->
                            HangulCombiner.getShiftedHangulJaso(baseText)

                        isShifted && !isHangulMode ->
                            baseText.uppercase()

                        !isShifted && isHangulMode ->
                            HangulCombiner.getUnshiftedHangulJaso(baseText) ?: baseText

                        else ->
                            baseText.lowercase()
                    }

                    child.text = newText

                }
            }
        }
    }
}