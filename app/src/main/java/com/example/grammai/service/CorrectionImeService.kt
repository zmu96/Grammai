package com.example.grammai.ime

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import com.example.grammai.R
import com.example.grammai.hangul.HangulCombiner

class CorrectionImeService : InputMethodService(), View.OnClickListener {

    private lateinit var inputView: View
    private lateinit var hangulLayout: LinearLayout
    private lateinit var englishLayout: LinearLayout
    private lateinit var symbolLayout1: LinearLayout
    private lateinit var symbolLayout2: LinearLayout

    private val combiner = HangulCombiner()

    private var isHangulMode = true
    private var isSymbolMode = false
    private var symbolPage = 1
    private var isShifted: Boolean = false


    // -----------------------------
    // 🔥 추가된 부분: 새 입력창 시작할 때 조합 완전 종료
    // -----------------------------
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        combiner.resetJaso()
    }

    // -----------------------------
    // 🔥 추가된 부분: 입력창 종료될 때 조합 완전 종료
    // -----------------------------
    override fun onFinishInput() {
        super.onFinishInput()

        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        combiner.resetJaso()
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

        bindButtons(hangulLayout)
        bindButtons(englishLayout)
        bindButtons(symbolLayout1)
        bindButtons(symbolLayout2)

        updateLayoutVisibility()
        updateButtonText(inputView)

        return inputView
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

        // 🔥 조합 강제 종료 추가
        if (btn.id in listOf(
                R.id.key_h_hangul_english, R.id.key_e_hangul_english,
                R.id.key_s1_hangul_english, R.id.key_s2_mode_change,
                R.id.key_h_symbol_change, R.id.key_e_symbol_change,
                R.id.key_s1_symbol_change, R.id.key_s2_symbol_change,
                R.id.key_s1_hangul_keyboard, R.id.key_s2_hangul_keyboard
            )) {

            ic.finishComposingText()           // 🔥 추가
            if (combiner.getComposingText().isNotEmpty()) {
                ic.commitText(combiner.getComposingText(), 1)
            }
            combiner.resetJaso()
        }

        when (btn.id) {

            R.id.key_h_shift, R.id.key_e_shift -> {
                isShifted = !isShifted
                inputView.let { updateButtonText(it) }
                return
            }

            R.id.key_h_hangul_english, R.id.key_e_hangul_english,
            R.id.key_s1_hangul_english, R.id.key_s2_mode_change -> {
                ic.finishComposingText()   // 🔥 추가
                isHangulMode = !isHangulMode
                isSymbolMode = false
                updateLayoutVisibility()
                inputView.let { updateButtonText(it) }
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

            else -> {
                handleCharacter(text, ic)
            }
        }
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
    }

    private fun handleSpace(ic: InputConnection) {
        commitRemaining()
        ic.commitText(" ", 1)
    }

    private fun handleEnter(ic: InputConnection) {
        commitRemaining()
        ic.commitText("\n", 1)
    }


    // -----------------------------
    // 조합 문자 처리
    // -----------------------------
    private fun handleCharacter(text: String, ic: InputConnection) {
        var input = text
        if (isShifted) {
            input = if (isHangulMode) HangulCombiner.getShiftedHangulJaso(text)
            else text.uppercase()

            isShifted = false
        }

        if (!isHangulMode || isSymbolKey(input)) {
            commitRemaining()
            ic.commitText(input, 1)
            return
        }

        val result = combiner.inputJaso(input)
        if (result.commit.isNotEmpty()) ic.commitText(result.commit, 1)

        val composingText = result.composing
        if (composingText.isNotEmpty()) ic.setComposingText(composingText, 1)
        else ic.finishComposingText()
    }

    private fun commitRemaining() {
        val ic = currentInputConnection ?: return
        val remain = combiner.finishComposing()

        // ❌ 잘못된 finishComposingText / setComposingText 제거

        if (remain != null) {
            ic.commitText(remain, 1)
        }

        combiner.resetJaso()
    }

    private fun isSymbolKey(text: String): Boolean {
        return text.length == 1 &&
                !Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
                    .equals(Character.UnicodeBlock.of(text[0]))
    }


    // -----------------------------
    // 버튼 텍스트 업데이트
    // -----------------------------
    private fun updateButtonText(view: View) {
        if (view is LinearLayout) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is LinearLayout) updateButtonText(child)
                else if (child is Button) {

                    if (child.id == R.id.key_h_shift || child.id == R.id.key_e_shift) {
                        child.isActivated = isShifted
                        continue
                    }

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

                    val originalText = child.text.toString()
                    val newText = when {
                        isShifted && isHangulMode -> HangulCombiner.getShiftedHangulJaso(originalText)
                        isShifted && !isHangulMode -> originalText.uppercase()
                        !isShifted && isHangulMode -> HangulCombiner.getUnshiftedHangulJaso(originalText) ?: originalText
                        else -> originalText.lowercase()
                    }
                    child.text = newText
                }
            }
        }
    }
}
