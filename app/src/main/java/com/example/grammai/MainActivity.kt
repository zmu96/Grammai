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
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var loadingContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    
    private var isModelLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 컴포넌트 초기화
        loadingContainer = findViewById(R.id.loading_container)
        progressBar = findViewById(R.id.progress_bar)
        loadingText = findViewById(R.id.loading_text)

        // 모델 로딩 상태 확인
        if (isModelCached()) {
            isModelLoaded = true
            setupUI()
        } else {
            loadModelFromAssets()
        }

        Toast.makeText(this, "키보드 설정을 완료해야 앱이 작동합니다.", Toast.LENGTH_LONG).show()
    }

    /**
     * 모델이 캐시되어 있는지 확인
     */
    private fun isModelCached(): Boolean {
        val modelFile = File(getModelPath())
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * 모델 파일 경로 반환
     */
    private fun getModelPath(): String {
        return File(filesDir, "kot5_spellcheck_int8.onnx").absolutePath
    }

    /**
     * 백그라운드에서 모델 로딩 (코루틴 사용)
     */
    private fun loadModelFromAssets() {
        lifecycleScope.launch {
            try {
                showLoadingProgress()
                
                withContext(Dispatchers.IO) {
                    val modelFile = File(getModelPath())
                    if (!modelFile.exists()) {
                        copyModelFromAssets(modelFile)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    isModelLoaded = true
                    hideLoadingProgress()
                    setupUI()
                }
            } catch (e: Exception) {
                Log.e("ModelLoading", "Failed to load model: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showErrorDialog("모델 로딩 실패: ${e.message}")
                }
            }
        }
    }

    /**
     * Assets에서 모델 파일 복사 (IO 스레드에서 실행)
     */
    private suspend fun copyModelFromAssets(destination: File) {
        withContext(Dispatchers.IO) {
            try {
                assets.open("kot5_spellcheck_int8.onnx").use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)  // 8KB 버퍼 사용
                    }
                }
                Log.d("ModelLoading", "Model copied successfully to ${destination.absolutePath}")
            } catch (e: Exception) {
                Log.e("ModelLoading", "Failed to copy model from assets", e)
                throw e
            }
        }
    }

    /**
     * 로딩 진행률 UI 표시
     */
    private fun showLoadingProgress() {
        loadingContainer.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        loadingText.text = "모델 로딩 중..."
    }

    /**
     * 로딩 진행률 UI 숨김
     */
    private fun hideLoadingProgress() {
        loadingContainer.visibility = View.GONE
    }

    /**
     * 에러 다이얼로그 표시
     */
    private fun showErrorDialog(message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("오류")
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * UI 설정 (모델 로딩 후 실행)
     */
    private fun setupUI() {
        val mainLayout = findViewById<LinearLayout>(R.id.content)
        if (mainLayout.childCount > 0) return  // 이미 초기화됨

        // 1. 키보드 활성화 설정으로 이동 버튼
        val enableButton = Button(this).apply {
            text = "1단계: 설정에서 [한글 교정 키보드] 활성화"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 30
                bottomMargin = 30
            }
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "설정 화면을 열 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        mainLayout.addView(enableButton)

        // 2. 기본 키보드 선택 버튼
        val selectButton = Button(this).apply {
            text = "2단계: 기본 키보드로 [한글 교정 키보드] 선택"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10
                bottomMargin = 30
            }
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        mainLayout.addView(selectButton)
    }
}