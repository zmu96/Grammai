package com.example.grammai

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 앱 진입점 Activity
 * ONNX 모델을 1회 복사하고 사용자에게 IME 활성화를 안내합니다.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val MODEL_FILENAME = "kot5_spellcheck_int8.onnx"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "✅ MainActivity onCreate")

        // ONNX 모델 1회 복사
        copyOnnxModelOnce()

        // UI 구성
        setupUI()

        Toast.makeText(this, "키보드 설정을 완료해야 앱이 작동합니다.", Toast.LENGTH_LONG).show()
    }

    /**
     * 메인 UI 레이아웃 설정
     */
    private fun setupUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1단계: 키보드 활성화
        val enableButton = Button(this).apply {
            text = "1단계: 설정에서 [한글 교정 키보드] 활성화"
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    Log.d(TAG, "📱 IME Settings opened")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to open settings", e)
                    Toast.makeText(
                        this@MainActivity,
                        "설정 화면을 열 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        mainLayout.addView(enableButton)

        // 2단계: 기본 키보드 선택
        val selectButton = Button(this).apply {
            text = "2단계: 기본 키보드로 [한글 교정 키보드] 선택"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 30
            }
            setOnClickListener {
                try {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                    Log.d(TAG, "🎹 IME Picker shown")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to show IME picker", e)
                    Toast.makeText(
                        this@MainActivity,
                        "IME 선택 화면을 열 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        mainLayout.addView(selectButton)

        setContentView(mainLayout)
    }

    /**
     * 앱 프로세스에서 단 1회만 ONNX 모델을 내부 저장소로 복사
     * 
     * ⚠️ IME 서비스에서는 절대 복사하면 안 됨 (시스템 성능 저하)
     */
    private fun copyOnnxModelOnce() {
        val modelFile = File(filesDir, MODEL_FILENAME)

        // 이미 존재하면 반환
        if (modelFile.exists()) {
            Log.d(TAG, "✅ ONNX model already exists: ${modelFile.absolutePath}")
            return
        }

        try {
            Log.i(TAG, "📥 Starting ONNX model copy from assets...")

            // 동기로 복사 (UI 스레드 블로킹 방지를 위해 필요시 백그라운드 스레드 사용)
            val copiedBytes = assets.open(MODEL_FILENAME).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 검증
            validateModelFile(modelFile, copiedBytes)

            Log.i(TAG, "✅ ONNX model successfully copied: $copiedBytes bytes")

        } catch (e: IOException) {
            Log.e(TAG, "❌ Failed to copy ONNX model", e)
            showModelLoadErrorDialog(e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error while copying ONNX model", e)
            showModelLoadErrorDialog(e)
        }
    }

    /**
     * 복사된 모델 파일 검증
     */
    private fun validateModelFile(modelFile: File, expectedSize: Long) {
        if (!modelFile.exists()) {
            throw IOException("❌ Model file was not created")
        }
        if (modelFile.length() == 0L) {
            throw IOException("❌ Model file is empty (0 bytes)")
        }
        if (modelFile.length() != expectedSize) {
            Log.w(TAG, "⚠️ Model file size mismatch. Expected: $expectedSize, Got: ${modelFile.length()}")
        }
    }

    /**
     * 모델 로드 실패 시 사용자에게 알림
     */
    private fun showModelLoadErrorDialog(e: Exception) {
        Toast.makeText(
            this,
            "모델 로드 실패: ${e.message ?: "Unknown error"}\n앱을 재설치하세요.",
            Toast.LENGTH_LONG
        ).show()
    }
}