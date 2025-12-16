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
🧠 한글 입력 조합 엔진 (HangulCombiner)

 Grammai 키보드는 Android 기본 IME의 내부 구현에 의존하지 않고,
 한글 조합 로직을 직접 구현한 커스텀 한글 입력 엔진을 사용합니다.

이 엔진은 초성·중성·종성 상태를 직접 관리하며,
유니코드 한글 조합 규칙을 기반으로 **실시간 입력, 분해, 재조합, 확정(commit)**을 처리합니다.

✨ 설계 목표

HangulCombiner는 다음과 같은 문제를 해결하기 위해 설계되었습니다.

Android IME 환경에서 발생하는 한글 입력 예외 상황 처리

겹받침 / 이중 모음 입력 시 자연스러운 분해 및 재조합

백스페이스 입력 시 조합 단계별 정확한 되돌리기

서버 기반 AI 교정과 연동 가능한 명확한 composing / commit 분리 구조

🧩 내부 상태 모델

한글 조합기는 3개의 상태 값으로 현재 입력 상태를 관리합니다.

choIndex  : 초성 인덱스 (19개)
jungIndex : 중성 인덱스 (21개)
jongIndex : 종성 인덱스 (28개, 0은 받침 없음)


이 상태를 기반으로 다음 유니코드 공식을 사용해 완성형 한글을 계산합니다.

U+AC00 + (초성 × 21 × 28) + (중성 × 28) + 종성


이를 통해 모든 한글 음절을 수학적으로 정확하게 조합합니다.

🔤 자모 맵핑 구조

한글 자모는 유니코드 표준 순서에 맞게 직접 정의되어 있습니다.

CHO_MAP : 초성 19자

JUNG_MAP : 중성 21자

JONG_MAP : 종성 28자 (공백 포함)

이 방식은:

인덱스 기반 계산이 가능

조합/분해 시 예외 처리 단순화

IME 외부 로직(AI 서버 등)과 연동 시 구조적 일관성 유지

라는 장점이 있습니다.

🔀 겹받침 처리 로직
✅ 겹받침 생성

다음과 같은 겹받침 입력을 지원합니다.

ㄱ + ㅅ → ㄳ

ㄴ + ㅈ → ㄵ

ㄹ + ㅁ → ㄻ

ㅂ + ㅅ → ㅄ
등

이를 위해 2단계 맵 구조를 사용합니다.

(기존 종성) + (새 종성) → (겹받침 종성)


이 구조는 확장성과 가독성을 동시에 확보합니다.

✅ 겹받침 분리 (가장 어려운 부분)

겹받침 뒤에 모음이 입력될 경우,
다음과 같은 분리 로직을 수행합니다.

예시
앉 + ㅣ  →  안 + 지
읽 + 어  →  일 + 거


이를 위해:

첫 번째 받침 인덱스

두 번째 받침 인덱스
를 명확히 분리하는 전용 맵(JONG_FIRST_MAP, JONG_SPLIT_MAP)을 사용합니다.

👉 이 부분은 한글 IME 구현에서 가장 오류가 많이 발생하는 구간이며,
Grammai에서는 이를 명시적 인덱스 기반 로직으로 해결했습니다.

🔗 이중 모음 처리

다음과 같은 이중 모음 조합을 지원합니다.

ㅗ + ㅏ → ㅘ

ㅗ + ㅣ → ㅚ

ㅜ + ㅓ → ㅝ

ㅡ + ㅣ → ㅢ

조합(COMPOSED_JUNG_MAP)과 분리(COMPOSED_JUNG_SPLIT)를 모두 구현하여
입력과 백스페이스 양쪽 모두 자연스럽게 동작합니다.

⌫ 백스페이스 처리 전략

handleBackspace()는 단순 삭제가 아니라,
조합 단계별로 정확하게 되돌리는 방식을 사용합니다.

처리 우선순위:

겹받침 → 홑받침 분리

홑받침 제거

이중 모음 → 단일 모음 분리

중성 제거

초성 제거

이로 인해 사용자는 실제 스마트폰 키보드와 동일한 체감 동작을 경험할 수 있습니다.

🔄 composing / commit 분리 설계

HangulInputResult는 다음 두 값을 명확히 분리합니다.

data class HangulInputResult(
    val composing: String, // 아직 확정되지 않은 글자
    val commit: String     // 확정되어 입력창에 반영될 글자
)


이 구조 덕분에:

Android IME와 자연스럽게 연동 가능

교정 버튼 클릭 시 현재 입력 중 문장만 추출 가능

서버 기반 AI 교정 구조로 확장 용이

🚀 설계의 의의

이 한글 조합 엔진은 단순한 키보드 구현을 넘어,

✔ 한글 입력 규칙에 대한 깊은 이해

✔ 상태 기반 문자열 처리 능력

✔ 모바일 환경 제약을 고려한 설계

✔ AI 기능 확장을 고려한 구조적 분리

를 모두 반영한 핵심 컴포넌트입니다.

🔥 한 줄 요약 (README용 강조 문장)

Grammai의 한글 입력 엔진은 유니코드 조합 규칙을 직접 구현한 상태 기반 한글 조합기이며,
복잡한 겹받침·이중 모음·백스페이스 처리를 안정적으로 지원하도록 설계되었습니다.

</details> <br/>
🤖 AI 맞춤법 교정 서버
<details> <summary><b>🔧 FastAPI 기반 서버 구조</b></summary> <br/>
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

</details> <br/>
📄 라이선스

This project is licensed under the MIT License.

<br/>
🙋‍♂️ 개발자

한준서
Android IME · AI 응용 프로젝트

<br/>
