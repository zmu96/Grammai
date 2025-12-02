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

// 이 Activity는 앱을 실행했을 때 나타나며, 사용자에게 키보드를 활성화하도록 안내합니다.
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            // 💡 [수정] 버튼 생성 시, LayoutParams를 명시적으로 생성하여 할당합니다.
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // 💡 이제 안전하게 topMargin을 설정할 수 있습니다.
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
}