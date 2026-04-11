// MainActivity.kt
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
import java.util.concurrent.atomic.AtomicBoolean

// 이 Activity는 앱을 실행했을 때 나타나며, 사용자에게 키보드를 활성화하도록 안내합니다.
class MainActivity : AppCompatActivity() {

    companion object {
        private val modelCopyInProgress = AtomicBoolean(false)
        private val modelCopyCompleted = AtomicBoolean(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 [추가] 앱 실행 시 ONNX 모델 1회 복사 (스레드 안전성 강화)
        copyOnnxOnce()

        // 화면 구성을 위한 레이아웃 설정 (Compose 코드는 제거하고 View 시스템 사용)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // padding을 dp 대신 pixel로 지정하지만, 간단한 예시이므로 하드코딩
            setPadding(60, 60, 60, 60)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1. 키보드 활성화 설정으로 이동 버튼 (필수 1단계)
        val enableButton = Button(this).apply {
            text = "1단계: 설정에서 [한글 교정 키보드] 활성화"
            setOnClickListener {
                // 사용자를 안드로이드 설정 -> 언어 및 입력 -> 키보드 관리 화면으로 보냅니다.
                try {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "Failed to open settings", e)
                }
            }
        }
        mainLayout.addView(enableButton)

        // 2. 기본 키보드 선택 버튼 (필수 2단계)
        val selectButton = Button(this).apply {
            text = "2단계: 기본 키보드로 [한글 교정 키보드] 선택"

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 30
            }

            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        mainLayout.addView(selectButton)

        setContentView(mainLayout)

        Toast.makeText(this, "키보드 설정을 완료해야 앱이 작동합니다.", Toast.LENGTH_LONG).show()
    }

    /**
     * 🔥 앱 프로세스에서 단 1번만 ONNX 모델 복사
     * IME에서는 절대 복사하면 안 됨
     * 
     * 개선사항:
     * - AtomicBoolean을 사용한 스레드 안전성 강화
     * - 중복 복사 방지 (Double-Checked Locking 패턴)
     * - 예외 처리 및 로깅 추가
     * - 파일 존재 여부 확인 강화
     */
    private fun copyOnnxOnce() {
        // 이미 복사 완료된 경우 즉시 반환
        if (modelCopyCompleted.get()) {
            return
        }

        val modelFile = File(filesDir, "kot5_spellcheck_int8.onnx")

        // 파일이 이미 존재하고 유효한 크기인 경우
        if (modelFile.exists() && modelFile.length() > 0) {
            modelCopyCompleted.set(true)
            Log.d("MainActivity", "ONNX model already exists: ${modelFile.absolutePath}")
            return
        }

        // 이미 복사 중인 경우 중복 시작 방지
        if (!modelCopyInProgress.compareAndSet(false, true)) {
            Log.d("MainActivity", "ONNX model copy already in progress")
            return
        }

        Thread {
            try {
                // 다시 한 번 확인 (다른 스레드가 복사했을 수 있음)
                if (modelFile.exists() && modelFile.length() > 0) {
                    Log.d("MainActivity", "ONNX model already copied by another thread")
                    modelCopyCompleted.set(true)
                    return@Thread
                }

                Log.d("MainActivity", "Starting ONNX model copy...")
                val startTime = System.currentTimeMillis()

                assets.open("kot5_spellcheck_int8.onnx").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                Log.i("MainActivity", "ONNX model copied successfully in ${duration}ms to ${modelFile.absolutePath}")
                modelCopyCompleted.set(true)

            } catch (e: Exception) {
                Log.e("MainActivity", "ONNX model copy failed: ${e.message}", e)
                // 부분적으로 복사된 파일 삭제
                try {
                    if (modelFile.exists()) {
                        modelFile.delete()
                        Log.d("MainActivity", "Deleted incomplete model file")
                    }
                } catch (deleteException: Exception) {
                    Log.e("MainActivity", "Failed to delete incomplete model file", deleteException)
                }
            } finally {
                modelCopyInProgress.set(false)
            }
        }.apply {
            name = "OnnxModelCopyThread"
            isDaemon = false  // 앱 종료 전에 완료되도록 설정
        }.start()
    }
}