⌨️ Grammai

AI 기반 한국어 맞춤법 교정 Android 키보드 (IME)

📖 프로젝트 개요

Grammai는 Android 플랫폼에서 동작하는 커스텀 IME(키보드) 앱입니다.
한글·영문·기호 입력을 직접 구현했으며,
서버 기반 AI 맞춤법 교정 기능으로 확장 가능한 구조를 갖고 있습니다.

🚀 주요 기능

한글 두벌식 입력 및 조합 처리

영문 QWERTY 입력

기호 키보드 (2 페이지)

키보드 모드 전환

서버 연동형 AI 맞춤법 교정 구조

🧠 기술 스택
영역	기술
Language	Kotlin
Platform	Android IME
Server	FastAPI, PyTorch
Build	Gradle
📦 실행 방법
git clone https://github.com/zmu96/Grammai.git


Android Studio로 프로젝트 열기

Gradle Sync

키보드 설정 → Grammai 활성화

🧠 핵심 기술 요약 (한 줄)

Grammai는 유니코드 조합 규칙을 직접 구현한 상태 기반 한글 입력 엔진과,
서버 기반 AI 맞춤법 교정 구조를 결합한 커스텀 Android IME 프로젝트입니다.

🔽 한글 입력 조합 엔진 상세 (접기)
<details> <summary><b>📌 HangulCombiner 설계 및 동작 원리</b></summary>
✔ 설계 개요

초성/중성/종성 상태 기반 한글 조합기

유니코드 공식 기반 완성형 계산

겹받침, 이중 모음, 백스페이스 단계별 처리

✔ 주요 처리 기능

겹받침 생성 및 분리 (앉 + ㅣ → 안 + 지)

이중 모음 조합 (ㅗ + ㅏ → ㅘ)

composing / commit 분리 구조

✔ 설계 의의

Android 기본 IME 의존성 제거

AI 교정 기능과 자연스러운 연동 가능

</details>
🔽 AI 맞춤법 교정 서버 (접기)
<details> <summary><b>🤖 FastAPI 기반 AI 교정 서버</b></summary>
✔ 서버 구조

Model: T5 기반 한국어 맞춤법 교정 모델

Framework: PyTorch + Transformers

Serving: FastAPI + Uvicorn

✔ API 예시
POST /correct
{
  "text": "오늘 날씨가 조타"
}

{
  "original": "오늘 날씨가 조타",
  "corrected": "오늘 날씨가 좋다"
}

✔ 장점

IME 메모리 문제 해결

모델 교체/업데이트 용이

모바일·웹 확장 가능

</details>
📄 라이선스

This project is licensed under the MIT License.

🙋‍♂️ 개발자

한준서
Android IME · AI 응용 프로젝트
