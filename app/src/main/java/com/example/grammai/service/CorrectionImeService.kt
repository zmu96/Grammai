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
import org.json.JSONException
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


sealed class ApiException(message: String, cause: Throwable? = null) : 
    Exception(message, cause) {
    class NetworkError(message: String, cause: Throwable) : 
        ApiException(message, cause)
    class ServerError(val code: Int, message: String) : 
        ApiException(message)
    class ParseError(message: String, cause: Throwable) : 
        ApiException(message, cause)
    class TimeoutError(message: String, cause: Throwable) : 
        ApiException(message, cause)
}

sealed class ApiResult {
    data class Success(val corrected: String) : ApiResult()
    data class Error(val exception: ApiException) : ApiResult()
}

data class ImeMode(
    val isHangul: Boolean = true,
    val isSymbol: Boolean = false,
    val symbolPage: Int = 1
)


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
    private val bufferLock = ReentrantReadWriteLock()

    private val combiner = HangulCombiner()

    private var currentMode = ImeMode()

    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftTapTime = 0L

    private companion object {
        private const val DOUBLE_TAP_THRESHOLD_MS = 400L
        private const val SERVER_CONNECT_TIMEOUT_SEC = 10L
        private const val SERVER_READ_TIMEOUT_SEC = 30L
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        combiner.resetJaso()
        currentInputConnection?.finishComposingText()
    }

    override fun onFinishInput() {
        super.onFinishInput()

        currentInputConnection?.finishComposingText()
        combiner.resetJaso()

        clearBuffer()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        currentInputConnection?.finishComposingText()
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
                    view.tag = if (!currentMode.isHangul && text.length == 1 && text[0].isLetter()) {
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

    private fun appendToBuffer(text: String) {
        bufferLock.write {
            sentenceBuffer.append(text)
        }
    }

    private fun deleteFromBuffer() {
        bufferLock.write {
            if (sentenceBuffer.isNotEmpty()) {
                sentenceBuffer.deleteCharAt(sentenceBuffer.length - 1)
            }
        }
    }

    private fun getBufferContent(): String {
        return bufferLock.read {
            sentenceBuffer.toString()
        }
    }

    private fun clearBuffer() {
        bufferLock.write {
            sentenceBuffer.clear()
        }
    }

    private fun syncSentenceBufferWithEditor() {
        val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest(),
            0
        ) ?: return

        val currentText = extracted.text?.toString() ?: ""

        bufferLock.write {
            sentenceBuffer.clear()
            if (currentText.isNotEmpty()) {
                sentenceBuffer.append(currentText)
            }
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

        memoEditText.setText(getBufferContent())
        memoEditText.setSelection(memoEditText.text.length)
    }

    private fun hideMemo() {
        isMemoMode = false

        memoLayout.visibility = View.GONE

        updateLayoutVisibility()
    }

    private fun updateLayoutVisibility() {
        hangulLayout.visibility = if (currentMode.isHangul && !currentMode.isSymbol) View.VISIBLE else View.GONE
        englishLayout.visibility = if (!currentMode.isHangul && !currentMode.isSymbol) View.VISIBLE else View.GONE

        symbolLayout1.visibility = if (currentMode.isSymbol && currentMode.symbolPage == 1) View.VISIBLE else View.GONE
        symbolLayout2.visibility = if (currentMode.isSymbol && currentMode.symbolPage == 2) View.VISIBLE else View.GONE
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

    private fun switchMode(newMode: ImeMode) {
        val composing = combiner.getComposingText()
        if (composing.isNotEmpty()) {
            currentInputConnection?.commitText(composing, 1)
            appendToBuffer(composing)
            combiner.resetJaso()
        }

        currentInputConnection?.finishComposingText()

        currentMode = newMode
        updateLayoutVisibility()
        inputView.let { updateButtonText(it) }
    }

    override fun onClick(v: View?) {

        val btn = v as? Button ?: return
        val text = btn.text.toString()
        val ic = currentInputConnection ?: return

        val modeChangeKeys = setOf(
            R.id.key_h_hangul_english, R.id.key_e_hangul_english,
            R.id.key_s1_hangul_english, R.id.key_s2_mode_change,
            R.id.key_h_symbol_change, R.id.key_e_symbol_change,
            R.id.key_s1_symbol_change, R.id.key_s2_symbol_change,
            R.id.key_s1_hangul_keyboard, R.id.key_s2_hangul_keyboard
        )

        if (btn.id in modeChangeKeys) {
            val composing = combiner.getComposingText()
            if (composing.isNotEmpty()) {
                ic.commitText(composing, 1)
                appendToBuffer(composing)
            }
            ic.finishComposingText()
            combiner.resetJaso()
        }

        when (btn.id) {

            R.id.key_h_shift, R.id.key_e_shift -> {

                val now = System.currentTimeMillis()

                when {
                    isCapsLock -> {
                        isCapsLock = false
                        isShifted = false
                        lastShiftTapTime = 0L
                    }
                    lastShiftTapTime != 0L && now - lastShiftTapTime < DOUBLE_TAP_THRESHOLD_MS -> {
                        isCapsLock = true
                        isShifted = true
                        lastShiftTapTime = 0L
                    }
                    isShifted -> {
                        isShifted = false
                        lastShiftTapTime = 0L
                    }
                    else -> {
                        isShifted = true
                        lastShiftTapTime = now
                    }
                }

                updateShiftButtonUI()
                updateButtonText(inputView)
                return
            }

            R.id.key_h_hangul_english, R.id.key_e_hangul_english,
            R.id.key_s1_hangul_english, R.id.key_s2_mode_change -> {
                switchMode(currentMode.copy(isHangul = !currentMode.isHangul, isSymbol = false))
                return
            }

            R.id.key_h_symbol_change, R.id.key_e_symbol_change -> {
                switchMode(currentMode.copy(isSymbol = true, symbolPage = 1))
                return
            }

            R.id.key_s1_symbol_change -> {
                switchMode(currentMode.copy(symbolPage = 2))
                return
            }

            R.id.key_s2_symbol_change -> {
                switchMode(currentMode.copy(symbolPage = 1))
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
                appendToBuffer(",")
                return
            }

            R.id.key_h_period, R.id.key_e_period, R.id.key_s1_period, R.id.key_s2_period -> {
                commitRemaining()
                ic.commitText(".", 1)
                appendToBuffer(".")
                return
            }

            R.id.key_s1_hangul_keyboard, R.id.key_s2_hangul_keyboard -> {
                switchMode(currentMode.copy(isHangul = true, isSymbol = false))
                return
            }

            R.id.btn_correct -> {
                val composing = combiner.getComposingText()
                if (composing.isNotEmpty()) {
                    ic.commitText(composing, 1)
                    appendToBuffer(composing)
                    combiner.resetJaso()
                }
                ic.finishComposingText()

                val originalSentence = getBufferContent()

                if (originalSentence.isBlank()) return

                requestCorrectionFromServer(originalSentence) { corrected ->
                    ic.deleteSurroundingText(originalSentence.length, 0)
                    ic.commitText(corrected, 1)

                    clearBuffer()
                    appendToBuffer(corrected)
                }

                return
            }

            else -> {
                handleCharacter(text, ic)
            }
        }
    }

    private val httpClient = createHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(SERVER_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(SERVER_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()
    }

    private fun getServerUrl(): String {
        val prefs = getSharedPreferences("grammai_config", android.content.Context.MODE_PRIVATE)
        return prefs.getString(
            "server_url",
            "https://api.grammai.com/v1"
        ) ?: "https://api.grammai.com/v1"
    }

    private fun requestCorrectionFromServer(
        originalText: String,
        onResult: (String) -> Unit
    ) {
        val json = JSONObject()
        json.put("text", originalText)

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${getServerUrl()}/correct")
            .addHeader("User-Agent", "GrammaI/1.0")
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("CorrectionAPI", "Network request failed: ${e.message}", e)

                val exception = when (e) {
                    is SocketTimeoutException -> ApiException.TimeoutError(
                        "Request timeout after ${SERVER_READ_TIMEOUT_SEC}s",
                        e
                    )
                    else -> ApiException.NetworkError(
                        "Network error: ${e.message}",
                        e
                    )
                }

                mainHandler.post {
                    onResult(originalText)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { resp ->
                        when {
                            !resp.isSuccessful -> {
                                Log.e("CorrectionAPI", "Server error: HTTP ${resp.code} ${resp.message}")
                                ApiResult.Error(
                                    ApiException.ServerError(
                                        resp.code,
                                        "Server returned ${resp.code}: ${resp.message}"
                                    )
                                )
                            }
                            resp.body == null -> {
                                Log.w("CorrectionAPI", "Empty response body")
                                ApiResult.Error(
                                    ApiException.ParseError(
                                        "Empty response body",
                                        NullPointerException()
                                    )
                                )
                            }
                            else -> {
                                try {
                                    val responseBody = resp.body!!.string()
                                    val corrected = JSONObject(responseBody)
                                        .optString("corrected", originalText)
                                    ApiResult.Success(corrected)
                                } catch (e: JSONException) {
                                    Log.e("CorrectionAPI", "JSON parsing failed: ${e.message}", e)
                                    ApiResult.Error(
                                        ApiException.ParseError(
                                            "Failed to parse JSON: ${e.message}",
                                            e
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    Log.e("CorrectionAPI", "Request timeout: ${e.message}", e)
                    ApiResult.Error(
                        ApiException.TimeoutError(
                            "Request timeout after ${SERVER_READ_TIMEOUT_SEC}s",
                            e
                        )
                    )
                } catch (e: IOException) {
                    Log.e("CorrectionAPI", "IO error: ${e.message}", e)
                    ApiResult.Error(
                        ApiException.NetworkError(
                            "Network error: ${e.message}",
                            e
                        )
                    )
                } catch (e: Exception) {
                    Log.e("CorrectionAPI", "Unexpected error: ${e.message}", e)
                    ApiResult.Error(
                        ApiException.NetworkError(
                            "Unexpected error: ${e.message}",
                            e
                        )
                    )
                }

                mainHandler.post {
                    when (result) {
                        is ApiResult.Success -> onResult(result.corrected)
                        is ApiResult.Error -> {
                            Log.e("CorrectionAPI", "API Error: ${result.exception.message}")
                            onResult(originalText)
                        }
                    }
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

        deleteFromBuffer()
    }

    private fun handleSpace(ic: InputConnection) {
        commitRemaining()
        ic.commitText(" ", 1)

        appendToBuffer(" ")
    }

    private fun handleEnter(ic: InputConnection) {
        commitRemaining()
        ic.commitText("\n", 1)

        appendToBuffer("\n")
    }

    private fun handleCharacter(text: String, ic: InputConnection) {
        var input = text

        if (isShifted || isCapsLock) {
            input = if (currentMode.isHangul)
                HangulCombiner.getShiftedHangulJaso(text)
            else
                text.uppercase()
        }

        if (!currentMode.isHangul || isSymbolKey(input)) {
            commitRemaining()
            ic.commitText(input, 1)
            appendToBuffer(input)

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
            appendToBuffer(result.commit)
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
            appendToBuffer(remain)
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

                    if (currentMode.isSymbol) {
                        when (child.id) {
                            R.id.key_s1_symbol_change -> child.text = "1/2"
                            R.id.key_s2_symbol_change -> child.text = "2/2"
                        }
                        continue
                    }

                    val baseText = child.tag as? String ?: child.text.toString()

                    val newText = when {
                        isShifted && currentMode.isHangul ->
                            HangulCombiner.getShiftedHangulJaso(baseText)

                        isShifted && !currentMode.isHangul ->
                            baseText.uppercase()

                        !isShifted && currentMode.isHangul ->
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