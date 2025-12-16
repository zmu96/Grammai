⌨️ Grammai
AI 기반 한국어 맞춤법 교정 Android 키보드 (IME)
<br/>
📖 프로젝트 개요

Grammai는 Android 플랫폼에서 동작하는 커스텀 IME(키보드) 앱입니다.
한글·영문·기호 입력을 직접 구현했으며,
서버 기반 AI 맞춤법 교정 기능으로 확장 가능한 구조를 갖고 있습니다.

<br/>
🚀 주요 기능

✨ 한글 두벌식 입력 및 조합 처리

✨ 영문 QWERTY 입력

✨ 기호 키보드 (2 페이지 구성)

✨ 키보드 모드 전환 지원

🔄 서버 연동형 AI 맞춤법 교정 구조

<br/>
🧠 기술 스택
구분	기술
Language	Kotlin
Platform	Android IME
AI Server	FastAPI, PyTorch
Build Tool	Gradle
IDE	Android Studio
<br/>
📦 실행 방법
git clone https://github.com/zmu96/Grammai.git


1️⃣ Android Studio로 프로젝트 열기
2️⃣ Gradle Sync
3️⃣ 설정 → 키보드/입력기 → Grammai 활성화

<br/>
🧠 핵심 기술 요약
✅ 한 줄 핵심

Grammai는 유니코드 조합 규칙을 직접 구현한 상태 기반 한글 입력 엔진과,
서버 기반 AI 맞춤법 교정 구조를 결합한 커스텀 Android IME 프로젝트입니다.

<br/>
🔽 한글 입력 조합 엔진 (상세)
<details> <summary><b>📌 HangulCombiner 설계 및 동작 원리</b></summary> <br/>
✨ 설계 개요

초성 / 중성 / 종성 상태 기반 한글 조합기

유니코드 공식 기반 완성형 계산

겹받침·이중 모음·백스페이스 단계별 처리

<br/>
🔤 주요 처리 예시

앉 + ㅣ → 안 + 지

읽 + 어 → 일 + 거

ㅗ + ㅏ → ㅘ

<br/>
🧩 구조적 장점

Android 기본 IME 의존성 제거

composing / commit 명확 분리

AI 교정 기능과 자연스러운 연동 가능

</details> <br/>
🤖 AI 맞춤법 교정 서버
<details> <summary><b>🔧 FastAPI 기반 서버 구조</b></summary> <br/>
🧠 사용 모델

Model: T5 기반 한국어 맞춤법 교정 모델

Framework: PyTorch + Transformers

Serving: FastAPI + Uvicorn

<br/>
📡 API 예시
POST /correct
{
  "text": "오늘 날씨가 조타"
}

{
  "original": "오늘 날씨가 조타",
  "corrected": "오늘 날씨가 좋다"
}

<br/>
✅ 서버 구조 장점

모바일 메모리 문제 해결

모델 교체 및 업데이트 용이

Android / Web 확장 가능

</details> <br/>
📄 라이선스

This project is licensed under the MIT License.

<br/>
🙋‍♂️ 개발자

한준서
Android IME · AI 응용 프로젝트

<br/>
