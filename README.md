---

## 📖 프로젝트 개요

**Grammai**는 Android 플랫폼에서 동작하는 **커스텀 IME(키보드)** 앱입니다.  
한글, 영어, 기호 입력을 직접 구현하고, 이후 AI 기반 **맞춤법·문장 교정** 기능을 서버 및 클라우드 AI와 연동할 수 있도록 설계된 프로젝트입니다.

---

## 🚀 주요 기능

✨ **한글 입력 및 조합 처리**  
✨ **영문 QWERTY 입력**  
✨ **기호 키보드 입력 (2 페이지)**  
✨ **다중 키보드 모드 전환 지원**  
🔄 **AI 맞춤법 교정 가능 구조 설계**

---

## 🧠 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 | Kotlin |
| 플랫폼 | Android (IME Input Method) |
| 빌드 도구 | Gradle |
| IDE | Android Studio |

---

## 🗂 파일/디렉터리 구조

Grammai/
┣ app/
┃ ┣ build/
┃ ┣ src/
┃ ┃ ┣ androidTest/
┃ ┃ ┗ main/
┃ ┃ ┣ assets/
┃ ┃ ┣ java/
┃ ┃ ┃ ┗ com.example.grammai/
┃ ┃ ┃ ┣ ai/
┃ ┃ ┃ ┣ service/
┃ ┃ ┃ ┃ ┗ CorrectionImeService.kt
┃ ┃ ┃ ┣ HangulCombiner.kt
┃ ┃ ┃ ┗ MainActivity.kt
┃ ┃ ┗ res/
┃ ┃ ┣ anim/
┃ ┃ ┣ drawable/
┃ ┃ ┗ layout/
┣ gradle/
┣ .gitignore
┣ build.gradle.kts
┣ gradle.properties
┣ settings.gradle.kts
┗ README.md

yaml
코드 복사

---

## 📌 핵심 코드 설명

### 🛠 `CorrectionImeService.kt`
- IME 기반 키보드의 **입력/출력 흐름 제어**
- 키보드 레이아웃 전환 및 입력 이벤트 처리

### 🔤 `HangulCombiner.kt`
- 한글 **자모 분해/조합 처리 로직**
- 두벌식 한글 입력을 위한 핵심 알고리즘

### 📐 `layout/*.xml`
- 여러 **키보드 레이아웃** 정의 (한글/영문/기호)

---

## 📱 스크린샷

> 필요하다면 저장소 내부 또는 설명에 따라 직접 이미지 추가 가능 🙆‍♂️

---

## 📦 설치 및 실행

```bash
git clone https://github.com/zmu96/Grammai.git
cd Grammai
Android Studio로 프로젝트 열기

Gradle Sync 완료

기기 또는 에뮬레이터에서 빌드/실행

Android 설정 → 키보드/입력기 → Grammai 활성화
```
🛠️ 개발/확장 계획
✔ 사용자 설정 UI 추가
✔ 사용자 통계 및 입력 히스토리(or 캡처보드) 기능

--------------------------------------------------------------------------------------------

🤖 AI 맞춤법 교정 서버 (FastAPI)

Grammai는 모바일 기기에서 대용량 AI 모델을 직접 실행하는 대신,
외부 서버에서 AI 모델을 실행하고 HTTP API로 교정 결과를 받아오는 구조를 사용합니다.

이를 통해 IME 앱의 안정성과 성능 문제를 해결했습니다.

🧠 사용 모델

Model: T5 기반 한국어 맞춤법 교정 모델

Framework: PyTorch + Transformers

Serving: FastAPI + Uvicorn

모델은 서버 시작 시 1회 로딩되며, 이후 모든 교정 요청에 재사용됩니다.

🔄 AI 모델 로딩 중...
✅ AI 모델 로딩 완료! 이제 실제 교정이 가능합니다.

⚙️ 서버 실행 방법
1️⃣ 가상환경 활성화
python -m venv venv
venv\Scripts\activate   # Windows

2️⃣ 의존성 설치
pip install -r requirements.txt

3️⃣ FastAPI 서버 실행
python -m uvicorn main:app --host 0.0.0.0 --port 8000


서버 실행 후 다음과 같은 로그가 출력되면 정상 동작입니다.

INFO: Uvicorn running on http://0.0.0.0:8000
INFO: Application startup complete.

📡 API 엔드포인트
🔹 맞춤법 교정 요청

URL: /correct

Method: POST

Content-Type: application/json

요청 예시
{
  "text": "오늘 날씨가 조타"
}

응답 예시
{
  "original": "오늘 날씨가 조타",
  "corrected": "오늘 날씨가 좋다"
}

🔗 Android IME 연동 구조
[Grammai 키보드]
        ↓ (HTTP POST)
[FastAPI 서버]
        ↓
[T5 맞춤법 교정 모델]
        ↓
[교정 결과 반환]


IME에서 교정 버튼을 누르면,
현재 입력 중인 문장을 서버로 전송하여 교정 결과를 받아옵니다.

📈 서버 동작 확인

FastAPI에서 제공하는 Swagger UI를 통해
브라우저에서 직접 테스트할 수 있습니다.

http://localhost:8000/docs

✅ 서버 기반 구조의 장점

✔ 모바일 앱 크기 및 메모리 사용량 감소

✔ IME 프로세스 강제 종료 문제 해결

✔ 모델 교체 및 업데이트 용이

✔ 다양한 클라이언트(Android, Web 등) 확장 가능

🔮 향후 개선 방향

🔹 응답 속도 최적화 (ONNX / TorchScript)

🔹 서버 인증 및 보안 강화

🔹 교정 결과 캐싱

🔹 문장 단위·문맥 기반 교정 고도화
📄 라이선스
This project is licensed under the MIT License.

🙋‍♂️ 개발자
한준서 – Android IME/AI 응용 프로젝트
