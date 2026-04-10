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
import kotlinx.coroutines.*


// 서버 설정 상수화
object ServerConfig {
    const val BASE_URL = "https://api.grammai.com"
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 15L
    const val WRITE_TIMEOUT_SECONDS = 10L
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

    private val sentenceBuffer = StringBuilder()

    private val combiner = HangulCombiner()

    private var isHangulMode = true
    private var isSymbolMode = false
    private var symbolPage = 1

    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftTapTime = 0L

    // Coroutines 스코프
    private val scope = CoroutineScope(
        Dispatchers.Main + Job() + CoroutineExceptionHandler { _, exception ->
            Log.e("CorrectionIme", "Coroutine error", exception)
        }
    )

    // 타임아웃이 설정된 OkHttpClient
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(ServerConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ServerConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ServerConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    // Null 안전성을 위한 확장 함수
    private inline fun withInputConnection(block: (InputConnection) -> Unit) {
        currentInputConnection?.let(block)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        withInputConnection { ic ->
            ic.finishComposingText()
            combiner.resetJaso()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        withInputConnection { ic ->
            ic.finishComposingText()
            combiner.resetJaso()
        }
        sentenceBuffer.clear()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        withInputConnection { ic ->
            ic.finishComposingText()
            combiner.resetJaso()
        }
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

    private fun syncSentenceBufferWithEditor() {
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest(),
            0
        ) ?: return

        val currentText = extracted.text?.toString() ?: ""

        if (currentText.isEmpty()) {
            sentenceBuffer.clear()
        }
    }

    private fun showMemo() {
        syncSentenceBufferWithEditor()
        isMemoMode = true

        finishComposingAndSync()

        hangulLayout.visibility = View.GONE
        englishLayout.visibility = View.GONE
        symbolLayout1.visibility = View.GONE
        symbolLayout2.visibility = View.GONE

        memoLayout.visibility = View.VISIBLE

        memoEditText.setText(sentenceBuffer.toString())
        memoEditText.setSelection(memoEditText.text.length)
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

    // 공통 메서드: 조합 완료 및 동기화
    private fun finishComposingAndSync() {
        withInputConnection { ic ->
            val composing = combiner.getComposingText()
            if (composing.isNotEmpty()) {
                ic.commitText(composing, 1)
                sentenceBuffer.append(composing)
            }
            ic.finishComposingText()
            combiner.resetJaso()
        }
    }

    override fun onClick(v: View?) {

        val btn = v as? Button ?: return
        val text = btn.text.toString()

        withInputConnection { ic ->
            // 모드 변경 키들에 대해 공통 처리
            if (btn.id in listOf(
                    R.id.key_h_hangul_english, R.id.key_e_hangul_english,
                    R.id.key_s1_hangul_english, R.id.key_s2_mode_change,
                    R.id.key_h_symbol_change, R.id.key_e_symbol_change,
                    R.id.key_s1_symbol_change, R.id.key_s2_symbol_change,
                    R.id.key_s1_hangul_keyboard, R.id.key_s2_hangul_keyboard
                )) {
                finishComposingAndSync()
            }

            when (btn.id) {

                R.id.key_h_shift, R.id.key_e_shift -> {

                    val now = System.currentTimeMillis()
                    val DOUBLE_TAP_DELAY = 400L

                    if (isCapsLock) {
                        isCapsLock = false
                        isShifted = false
                        lastShiftTapTime = 0L
                    } else if (lastShiftTapTime != 0L && now - lastShiftTapTime < DOUBLE_TAP_DELAY) {
                        isCapsLock = true
                        isShifted = true
                        lastShiftTapTime = 0L
                    } else if (isShifted) {
                        isShifted = false
                        lastShiftTapTime = 0L
                    } else {
                        isShifted = true
                        lastShiftTapTime = now
                    }

                    updateShiftButtonUI()
                    updateButtonText(inputView)
                    return@withInputConnection
                }

                R.id.key_h_hangul_english, R.id.key_e_hangul_english,
                R.id.key_s1_hangul_english, R.id.key_s2_mode_change -> {
                    isHangulMode = !isHangulMode
                    isSymbolMode = false
                    updateLayoutVisibility()
                    inputView.let { updateButtonText(it) }
                    return@withInputConnection
                }

                R.id.key_h_symbol_change, R.id.key_e_symbol_change -> {
                    isSymbolMode = true
                    symbolPage = 1
                    updateLayoutVisibility()
                    inputView.let { updateButtonText(it) }
                    return@withInputConnection
                }

                R.id.key_s1_symbol_change -> {
                    symbolPage = 2
                    updateLayoutVisibility()
                    inputView.let { updateButtonText(it) }
                    return@withInputConnection
                }

                R.id.key_s2_symbol_change -> {
                    symbolPage = 1
                    updateLayoutVisibility()
                    inputView.let { updateButtonText(it) }
                    return@withInputConnection
                }

                R.id.key_h_delete, R.id.key_e_delete, R.id.key_s1_delete, R.id.key_s2_delete -> {
                    handleDelete(ic)
                    return@withInputConnection
                }

                R.id.key_h_space, R.id.key_e_space, R.id.key_s1_space, R.id.key_s2_space -> {
                    handleSpace(ic)
                    return@withInputConnection
                }

                R.id.key_h_enter, R.id.key_e_enter, R.id.key_s1_enter, R.id.key_s2_enter -> {
                    handleEnter(ic)
                    return@withInputConnection
                }

                R.id.key_h_comma, R.id.key_e_comma, R.id.key_s1_comma2, R.id.key_s2_comma -> {
                    commitRemaining()
                    ic.commitText(",", 1)
                    return@withInputConnection
                }

                R.id.key_h_period, R.id.key_e_period, R.id.key_s1_period, R.id.key_s2_period -> {
                    commitRemaining()
                    ic.commitText(".", 1)
                    return@withInputConnection
                }

                R.id.key_s1_hangul_keyboard, R.id.key_s2_hangul_keyboard -> {
                    isHangulMode = true
                    isSymbolMode = false
                    updateLayoutVisibility()
                    inputView.let { updateButtonText(it) }
                    return@withInputConnection
                }

                R.id.btn_correct -> {
                    val composing = combiner.getComposingText()
                    if (composing.isNotEmpty()) {
                        ic.commitText(composing, 1)
                        sentenceBuffer.append(composing)
                        combiner.resetJaso()
                    }
                    ic.finishComposingText()

                    val originalSentence = sentenceBuffer.toString()

                    if (originalSentence.isBlank()) return@withInputConnection

                    // 백그라운드에서 맞춤법 검사 수행
                    scope.launch {
                        requestCorrectionFromServer(originalSentence) { corrected ->
                            withInputConnection { innerIc ->
                                innerIc.deleteSurroundingText(originalSentence.length, 0)
                                innerIc.commitText(corrected, 1)
                                sentenceBuffer.clear()
                                sentenceBuffer.append(corrected)
                            }
                        }
                    }

                    return@withInputConnection
                }

                else -> {
                    handleCharacter(text, ic)
                }
            }
        }
    }

    private fun requestCorrectionFromServer(
        originalText: String,
        onResult: (String) -> Unit
    ) {
        // 입력 검증
        if (originalText.isBlank() || originalText.length > 1000) {
            onResult(originalText)
            return
        }

        val json = JSONObject().apply {
            put("text", originalText)
            put("timestamp", System.currentTimeMillis())
        }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${ServerConfig.BASE_URL}/api/v1/correct")
            .post(body)
            .addHeader("User-Agent", "GrammaI-Android")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("CorrectionService", "Network Error: ${e.message}")
                mainHandler.post {
                    onResult(originalText)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val corrected = try {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            Log.w("CorrectionService", "Server returned ${resp.code}")
                            return@use originalText
                        }
                        val body = resp.body?.string() ?: return@use originalText
                        JSONObject(body).optString("corrected", originalText)
                    }
                } catch (e: Exception) {
                    Log.e("CorrectionService", "Response parsing failed", e)
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
            sentenceBuffer.deleteCharAt(sentenceBuffer.length - 1)
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
        withInputConnection { ic ->
            val remain = combiner.finishComposing()
            if (remain != null) {
                ic.commitText(remain, 1)
                sentenceBuffer.append(remain)
            }
            combiner.resetJaso()
        }
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}