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
                    Log.e("MainActivity", "Failed to open settings", e)
                    Toast.makeText(this@MainActivity, "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
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
     * 🔥 앱 프로세스에서 단 1번만 ONNX 모델 복사 (스레드 안전성 강화)
     * IME에서는 절대 복사하면 안 됨
     * 
     * 개선사항:
     * 1. AtomicBoolean을 사용한 스레드 안전한 플래그 관리
     * 2. 중복 복사 방지 (이미 진행 중이면 대기)
     * 3. 예외 처리 강화 및 로깅 추가
     * 4. 파일 존재 여부 재확인으로 안전성 증대
     */
    private fun copyOnnxOnce() {
        val modelFile = File(filesDir, "kot5_spellcheck_int8.onnx")

        // 이미 복사 완료된 경우 즉시 반환
        if (modelCopyCompleted.get()) {
            Log.d("MainActivity", "ONNX model already copied")
            return
        }

        // 이미 파일이 존재하는 경우 완료 표시
        if (modelFile.exists()) {
            modelCopyCompleted.set(true)
            Log.d("MainActivity", "ONNX model file already exists")
            return
        }

        // 이미 복사 진행 중인 경우 중복 시작 방지
        if (!modelCopyInProgress.compareAndSet(false, true)) {
            Log.d("MainActivity", "ONNX model copy already in progress")
            return
        }

        Thread {
            try {
                // 파일 존재 여부 재확인 (다른 스레드가 먼저 복사했을 수 있음)
                if (modelFile.exists()) {
                    Log.d("MainActivity", "ONNX model file already exists (double-check)")
                    modelCopyCompleted.set(true)
                    return@Thread
                }

                // 임시 파일에 먼저 쓰기 (원자성 보장)
                val tempFile = File(filesDir, "kot5_spellcheck_int8.onnx.tmp")
                
                assets.open("kot5_spellcheck_int8.onnx").use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // 임시 파일을 최종 파일로 이름 변경
                if (tempFile.renameTo(modelFile)) {
                    Log.d("MainActivity", "ONNX model copied successfully: ${modelFile.absolutePath}")
                    modelCopyCompleted.set(true)
                } else {
                    Log.e("MainActivity", "Failed to rename temp file to model file")
                    tempFile.delete()
                }

            } catch (e: SecurityException) {
                Log.e("MainActivity", "Security exception while copying ONNX model", e)
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "Illegal state while copying ONNX model", e)
            } catch (e: Exception) {
                Log.e("MainActivity", "Unexpected error while copying ONNX model", e)
            } finally {
                modelCopyInProgress.set(false)
            }
        }.apply {
            name = "OnnxModelCopyThread"
            isDaemon = true
        }.start()
    }
}