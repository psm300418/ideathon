# 진.해.분

진상 해석 분석기. 안드로이드 백그라운드 녹음, 전체 스크립트 저장, 알바생 관점의 진상력 유형 분석 MVP입니다.

## 구현 범위

- 기능 1: 앱이 백그라운드에 있어도 접근성 서비스가 볼륨 버튼 더블클릭을 감지해 녹음을 시작/종료합니다.
- 기능 2: 녹음 종료 후 `.m4a` 파일을 전사하고, 전체 스크립트를 앱 화면에 저장/표시합니다.
- 녹음 기록 삭제: 저장된 녹음 파일과 스크립트 기록을 함께 삭제합니다.
- 기능 4: 저장된 스크립트를 로컬 규칙 기반으로 분석해 부당한 말, 갑질, 무리한 요구, 감정노동 신호와 진상력 점수를 제공합니다.
- 기능 3: 다른 작업자가 붙이기 쉽도록 요약 계층을 `summary` 패키지와 세션 저장 모델로 분리했습니다.

## 로컬 설정

1. Android Studio로 프로젝트를 엽니다.
2. 앱을 설치한 뒤 처음 실행하면 마이크/알림 권한 팝업을 허용합니다.
3. 앱의 `설정` 탭에서 OpenAI 또는 Groq API 키를 저장합니다. 두 키가 모두 있으면 OpenAI를 먼저 사용하고, 없거나 실패하면 Groq로 시도합니다.
4. 앱의 `설정` 탭에서 `접근성` 버튼을 눌러 `진.해.분 볼륨 버튼 감지` 서비스를 켭니다.

```properties
OPENAI_TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe
OPENAI_SUMMARY_MODEL=gpt-5.4-mini
GROQ_TRANSCRIPTION_MODEL=whisper-large-v3-turbo
GROQ_EMPATHY_MODEL=llama-3.1-8b-instant
```

## 주요 구조

- `MainActivity`: 녹음/스크립트, 요약, 진상력 측정, 설정 탭 UI
- `accessibility/VolumeKeyAccessibilityService`: 볼륨 버튼 더블클릭 감지
- `recording/RecordingService`: 포그라운드 녹음, 전사 처리
- `audio/AudioRecorder`: `MediaRecorder` 기반 `.m4a` 녹음
- `ai/OpenAiClient`: OpenAI 전사, 요약, 진상력 공감문 생성
- `ai/GroqClient`: Groq 전사, 요약, 진상력 공감문 생성 fallback
- `ai/AiPromptTemplates`: OpenAI와 Groq가 공유하는 요약/공감 프롬프트
- `data/RecordingRepository`: 앱 내부 저장소에 세션 JSON 저장
- `settings/ApiKeyStore`: 사용자가 앱 설정 탭에서 입력한 OpenAI/Groq API 키 저장
- `summary/SummaryMode`: 업무용 요약/마음 보호용 해석 모드
- `analysis/JinsangAnalyzer`: 무료 로컬 규칙 기반 진상력 분석기
- `analysis/JinsangAnalysis`: 진상력 분석 결과 모델
- `analysis/JinsangEmpathyContext`: API 공감문 생성을 위해 핵심 진상 발화만 3개 이하로 추리는 전처리기

## 팀원 작업 포인트

요약 탭은 저장된 `RecordingSession.transcript`를 바탕으로 `businessSummary`와 `comfortInterpretation`을 생성합니다.

진상력 분석 결과는 기존 세션 호환성을 위해 `RecordingSession.naggingAnalysis`에 JSON 문자열로 저장됩니다. `다시 측정`을 누르면 기존 결과를 덮어씁니다.

## GitHub 업로드 전 확인

- `local.properties`, `.gradle`, `.kotlin`, `build` 산출물은 커밋하지 않습니다.
- API 키는 앱의 `설정` 탭에서 사용자별로 입력합니다.
- 기능 3 담당자는 `summary` 패키지의 기본 모델을 확장하면 됩니다.

## 주의사항

백그라운드 볼륨키 감지는 일반 앱 권한만으로는 안정적으로 받을 수 없어 Android 접근성 서비스가 필요합니다. 앱 스토어 배포 시 접근성 사용 목적 고지와 정책 검토가 필요합니다.

MVP에서는 사용자가 앱 설정 탭에서 API 키를 직접 저장합니다. 현재 API가 필요한 전사, 요약, 공감 생성은 OpenAI 키를 우선 사용하고, OpenAI 키가 없거나 호출에 실패하면 Groq 키로 fallback합니다. 실제 배포 버전에서는 모바일 앱에 API 키를 넣지 말고 서버 프록시를 두는 구성이 안전합니다.
