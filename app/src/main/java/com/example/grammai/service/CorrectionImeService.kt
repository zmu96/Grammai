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
import org.json.JSONException
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit


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

    private var isShifted = false          // 기존
    private var isCapsLock = false         // 🔥 추가
    private var lastShiftTapTime = 0L      // 🔥 추가

    // ✅ 타임아웃 설정이 추가된 OkHttpClient
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val mainHandler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }

    // ✅ sentenceBuffer 동기화 함수 추가
    private fun syncSentenceBufferFromEditor() {
        val ic = currentInputConnection ?: return
        try {
            val extracted = ic.getExtractedText(
                android.view.inputmethod.ExtractedTextRequest(),
                0
            ) ?: return

            sentenceBuffer.clear()
            sentenceBuffer.append(extracted.text ?: "")
            
            Log.d("IME_SYNC", "Buffer synced: ${sentenceBuffer.length} chars")
        } catch (e: Exception) {
            Log.w("IME_SYNC", "Failed to sync buffer: ${e.message}")
        }
    }

    // -----------------------------
    // 🔥 추가된 부분: 새 입력창 시작할 때 조합 완전 종료
    // -----------------------------
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        combiner.resetJaso()
        sentenceBuffer.clear()
        syncSentenceBufferFromEditor()
    }

    // -----------------------------
    // 🔥 추가된 부분: 입력창 종료될 때 조합 완전 종료
    // -----------------------------
    override fun onFinishInput() {
        super.onFinishInput()

        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        combiner.resetJaso()

        sentenceBuffer.clear()
    }

    // -----------------------------
    // 🔥 추가된 부분: 키보드 UI가 다시 보여질 때 조합 완전 종료
    // (키보드 내렸다 올릴 때 반드시 호출됨)
    // -----------------------------
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        val ic = currentInputConnection
        ic?.finishComposingText()
        combiner.resetJaso()
    }


    override fun onCreate() {
        super.onCreate()
      //  Log.d("IME_CHECK", "IME onCreate")
    }

    // -----------------------------
    // InputView 생성
    // -----------------------------
    override fun onCreateInputView(): View {

        val inflater = LayoutInflater.from(this)

        inputView = inflater.inflate(R.layout.ime_keyboard_all_modes, null)

        hangulLayout = inputView.findViewById(R.id.layout_hangul)
        englishLayout = inputView.findViewById(R.id.layout_english)
        symbolLayout1 = inputView.findViewById(R.id.layout_symbol1)
        symbolLayout2 = inputView.findViewById(R.id.layout_symbol2)

        shiftHangulBtn = inputView.findViewById(R.id.key_h_shift)
        shiftEnglishBtn = inputView.findViewById(R.id.key_e_shift)


        // ✅ TopBar 버튼 연결
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



        // 🔥 모든 키의 "원본 텍스트"를 tag에 저장
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

        // 조합 완전 종료
        commitRemaining()
        currentInputConnection?.finishComposingText()

        // 키보드 숨김
        hangulLayout.visibility = View.GONE
        englishLayout.visibility = View.GONE
        symbolLayout1.visibility = View.GONE
        symbolLayout2.visibility = View.GONE

        // 메모 표시
        memoLayout.visibility = View.VISIBLE

        // 🔥 STEP 3
        memoEditText.setText(sentenceBuffer.toString())
        memoEditText.setSelection(memoEditText.text.length)
    }

    private fun hideMemo() {
        isMemoMode = false

        // 메모 숨김
        memoLayout.visibility = View.GONE

        // 키보드 복원
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


    // -----------------------------
    // 키 입력 처리
    // -----------------------------
    override fun onClick(v: View?) {

        val btn = v as? Button ?: return
        val text = btn.text.toString()
        val ic = currentInputConnection ?: return


      //  Log.d("IME_CHECK", "onClick entered, id=${btn.id}")

        // 🔥 조합 강제 종료 추가
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

                // 🔥 STEP 3 핵심: 버퍼 동기화
                sentenceBuffer.append(composing)
            }
            ic.finishComposingText()
            combiner.resetJaso()

        }
      //  Log.d("IME_CHECK", "BEFORE when, id=${btn.id}")
        when (btn.id) {

            R.id.key_h_shift, R.id.key_e_shift -> {

                val now = System.currentTimeMillis()
                val DOUBLE_TAP_DELAY = 400L

                // 🔒 1. Caps Lock ON → Shift 누르면 Caps Lock OFF
                if (isCapsLock) {
                    isCapsLock = false
                    isShifted = false
                    lastShiftTapTime = 0L
                }
                // 🔥 2. 빠른 2연타 → Caps Lock ON
                else if (lastShiftTapTime != 0L && now - lastShiftTapTime < DOUBLE_TAP_DELAY) {
                    isCapsLock = true
                    isShifted = true
                    lastShiftTapTime = 0L
                }
                // 🔹 3. Shift ON 상태에서 다시 누름 → Shift OFF
                else if (isShifted) {
                    isShifted = false
                    lastShiftTapTime = 0L
                }
                // 🔸 4. Shift OFF → Shift 1회용 ON
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
                ic.finishComposingText()   // 🔥 추가
                isHangulMode = !isHangulMode
                isSymbolMode = false
                
                // ✅ 모드 전환 시 Shift 상태 초기화
                if (isShifted && !isCapsLock) {
                    isShifted = false
                }
                
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
                updateShiftButtonUI()
                return
            }

            R.id.key_h_symbol_change, R.id.key_e_symbol_change -> {
                ic.finishComposingText()   // 🔥 추가
                isSymbolMode = true
                symbolPage = 1
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
                return
            }

            R.id.key_s1_symbol_change -> {
                ic.finishComposingText()   // 🔥 추가
                symbolPage = 2
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
                return
            }

            R.id.key_s2_symbol_change -> {
                ic.finishComposingText()   // 🔥 추가
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
                return
            }

            R.id.key_h_period, R.id.key_e_period, R.id.key_s1_period, R.id.key_s2_period -> {
                commitRemaining()
                ic.commitText(".", 1)
                return
            }

            R.id.key_s1_hangul_keyboard, R.id.key_s2_hangul_keyboard -> {
                ic.finishComposingText()    // 🔥 추가
                isHangulMode = true
                isSymbolMode = false
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
                return
            }

            R.id.btn_correct -> {
            //    Log.d("IME_CHECK", "ENTERED btn_correct branch")
                val composing = combiner.getComposingText()
                if (composing.isNotEmpty()) {
                    ic.commitText(composing, 1)
                    sentenceBuffer.append(composing)
                    combiner.resetJaso()
                }
                ic.finishComposingText()

                // ✅ 교정 버튼 클릭 시 버퍼 동기화
                syncSentenceBufferFromEditor()
                val originalSentence = sentenceBuffer.toString()

             //   Log.d("IME_CHECK", "SentenceBuffer='$originalSentence'")

                if (originalSentence.isBlank()) {
                    Log.d("IME_CORRECTION", "Empty text - skipping correction")
                    return
                }

                requestCorrectionFromServer(originalSentence) { corrected ->

                  //  Log.d("IME_CHECK", "Corrected result='$corrected'")

                    ic.deleteSurroundingText(originalSentence.length, 0)
                    ic.commitText(corrected, 1)

                    sentenceBuffer.clear()
                    sentenceBuffer.append(corrected)
                }

                return
            }



            else -> {
                handleCharacter(text, ic)
            }
        }
    }

    /* =====================================================
   ✅ 개선된 서버 교정 요청 함수
   ===================================================== */

    private fun requestCorrectionFromServer(
        originalText: String,
        onResult: (String) -> Unit
    ) {
        val json = JSONObject()
        json.put("text", originalText)

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        // ✅ BuildConfig를 통한 동적 서버 주소 설정
        // 현재는 하드코드된 주소를 사용하지만, 실제 프로덕션에서는 BuildConfig 사용
        val serverUrl = "http://115.23.150.161:8000/correct"  // TODO: BuildConfig.API_BASE_URL로 변경

        val request = Request.Builder()
            .url(serverUrl)
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                // ✅ 네트워크 오류 상세 처리
                val errorMsg = when (e) {
                    is SocketTimeoutException -> {
                        Log.w("IME_CORRECTION", "Socket timeout - using original text")
                        "timeout"
                    }
                    else -> {
                        Log.e("IME_CORRECTION", "Network error: ${e.message}", e)
                        "network_error"
                    }
                }

                mainHandler.post {
                    onResult(originalText)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                // ✅ HTTP 상태 코드 확인
                if (!response.isSuccessful) {
                    Log.e("IME_CORRECTION", "Server error: ${response.code}")
                    mainHandler.post { onResult(originalText) }
                    return
                }

                val responseBody = response.body?.string()
                
                // ✅ 안전한 JSON 파싱
                val corrected = try {
                    if (responseBody.isNullOrEmpty()) {
                        Log.w("IME_CORRECTION", "Empty response body")
                        originalText
                    } else {
                        val json = JSONObject(responseBody)
                        json.optString("corrected", originalText)  // optString 사용으로 안전성 강화
                    }
                } catch (e: JSONException) {
                    Log.e("IME_CORRECTION", "JSON parse error: ${e.message}", e)
                    originalText
                } catch (e: Exception) {
                    Log.e("IME_CORRECTION", "Unexpected error: ${e.message}", e)
                    originalText
                }

                mainHandler.post {
                    onResult(corrected)
                }
            }
        })
    }


    // -----------------------------
    // 기능 키 처리
    // -----------------------------
    private fun handleDelete(ic: InputConnection) {
        val composing = combiner.getComposingText()
        if (composing.isNotEmpty()) {
            combiner.handleBackspace()
            ic.setComposingText(combiner.getComposingText(), 1)
            return
        }
        ic.deleteSurroundingText(1, 0)

        // 🔥 STEP 3
        if (sentenceBuffer.isNotEmpty()) {
            sentenceBuffer.deleteCharAt(sentenceBuffer.length - 1)
        }
    }

    private fun handleSpace(ic: InputConnection) {
        commitRemaining()
        ic.commitText(" ", 1)

        // 🔥 STEP 3
        sentenceBuffer.append(" ")
    }

    private fun handleEnter(ic: InputConnection) {
        commitRemaining()
        ic.commitText("\n", 1)

        // 🔥 STEP 3
        sentenceBuffer.append("\n")
    }


    // -----------------------------
    // 조합 문자 처리
    // -----------------------------
    private fun handleCharacter(text: String, ic: InputConnection) {
        var input = text

        // 1️⃣ Shift 또는 Caps Lock이 켜져 있으면 문자 변형
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

            // 🔥 Shift 1회용 자동 해제 (Caps Lock 아닐 때)
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

            // 🔥 STEP 3 (조건 안으로 이동)
            sentenceBuffer.append(result.commit)
        }
        val composingText = result.composing
        if (composingText.isNotEmpty()) ic.setComposingText(composingText, 1)
        else ic.finishComposingText()

        // 3️⃣ Shift 1회용 자동 해제 (Caps Lock이 아닐 때만)
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

           // 🔥 STEP 3
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



    // -----------------------------
    // 버튼 텍스트 업데이트
    // -----------------------------
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