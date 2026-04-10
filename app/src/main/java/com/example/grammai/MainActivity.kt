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

// 이 Activity는 앱을 실행했을 때 나타나며, 사용자에게 키보드를 활성화하도록 안내합니다.
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 [추가] 앱 실행 시 ONNX 모델 1회 복사
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
     * ✅ 개선: 원자적 연산으로 동시성 제어 추가
     */
    private fun copyOnnxOnce() {
        val modelFile = File(filesDir, "kot5_spellcheck_int8.onnx")

        // 이미 존재하면 조기 종료
        if (modelFile.exists()) {
            Log.d("MainActivity", "ONNX model already exists")
            return
        }

        Thread {
            try {
                // 임시 파일에 먼저 쓰고 원자적으로 이동
                val tempFile = File(filesDir, ".${modelFile.name}.tmp")

                assets.open("kot5_spellcheck_int8.onnx").use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // ✅ 원자적 이동 (파일 시스템 지원 시)
                val renamed = tempFile.renameTo(modelFile)
                if (renamed) {
                    Log.d("MainActivity", "ONNX model copied successfully")
                } else {
                    Log.e("MainActivity", "Failed to rename temp file to model file")
                    tempFile.delete()
                }

            } catch (e: IOException) {
                Log.e("MainActivity", "Failed to copy ONNX model: ${e.message}", e)
                val tempFile = File(filesDir, ".kot5_spellcheck_int8.onnx.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Unexpected error during ONNX copy: ${e.message}", e)
            }
        }.start()
    }
}