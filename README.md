# 잔소리 차단기

안드로이드 백그라운드 녹음과 전체 스크립트 저장 MVP입니다.

## 구현 범위

- 기능 1: 앱이 백그라운드에 있어도 접근성 서비스가 볼륨 버튼 더블클릭을 감지해 녹음을 시작/종료합니다.
- 기능 2: 녹음 종료 후 `.m4a` 파일을 전사하고, 전체 스크립트를 앱 화면에 저장/표시합니다.
- 녹음 기록 삭제: 저장된 녹음 파일과 스크립트 기록을 함께 삭제합니다.
- 기능 3/4: 다른 작업자가 붙이기 쉽도록 요약/분석 계층을 `ai` 패키지와 세션 저장 모델로 분리했습니다.

## 로컬 설정

1. Android Studio로 프로젝트를 엽니다.
2. 앱을 설치한 뒤 처음 실행하면 마이크/알림 권한 팝업을 허용합니다.
3. 앱의 `설정` 탭에서 본인이 사용할 API 제공자의 키를 저장합니다.
4. 앱의 `설정` 탭에서 `접근성` 버튼을 눌러 `잔소리 차단기 볼륨 버튼 감지` 서비스를 켭니다.

```properties
GROQ_TRANSCRIPTION_MODEL=whisper-large-v3-turbo
OPENAI_SUMMARY_MODEL=gpt-5.4-mini
```

## 주요 구조

- `MainActivity`: 녹음/스크립트, 요약, 꼰대력 측정, 설정 탭 UI
- `accessibility/VolumeKeyAccessibilityService`: 볼륨 버튼 더블클릭 감지
- `recording/RecordingService`: 포그라운드 녹음, 전사 처리
- `audio/AudioRecorder`: `MediaRecorder` 기반 `.m4a` 녹음
- `ai/GroqClient`: Groq Whisper 전사 API 호출
- `ai/OpenAiClient`: 요약 API 메서드는 기능 3 확장을 위해 남겨둠
- `data/RecordingRepository`: 앱 내부 저장소에 세션 JSON 저장
- `settings/ApiKeyStore`: 사용자가 앱 설정 탭에서 입력한 API 키와 전사 공급자 저장
- `summary/SummaryMode`: 업무용 요약/마음 보호용 해석 모드
- `analysis/NaggingAnalysis`: 꼰대력 분석 결과 모델

## 팀원 작업 포인트

기능 3 담당자는 저장된 `RecordingSession.transcript`를 바탕으로 요약 생성 여부를 묻는 UI를 추가하고, `businessSummary`와 `comfortInterpretation` 필드를 채우면 됩니다.

기능 4 담당자는 `RecordingSession.transcript`를 입력으로 받아 `NaggingAnalysis`를 생성하고, 결과를 `naggingAnalysis` 필드나 별도 분석 저장소로 연결하면 됩니다.

## GitHub 업로드 전 확인

- `local.properties`, `.gradle`, `.kotlin`, `build` 산출물은 커밋하지 않습니다.
- API 키는 앱의 `설정` 탭에서 사용자별로 입력합니다.
- 기능 3/4 담당자는 `summary`와 `analysis` 패키지의 기본 모델을 확장하면 됩니다.

## 주의사항

백그라운드 볼륨키 감지는 일반 앱 권한만으로는 안정적으로 받을 수 없어 Android 접근성 서비스가 필요합니다. 앱 스토어 배포 시 접근성 사용 목적 고지와 정책 검토가 필요합니다.

MVP에서는 사용자가 앱 설정 탭에서 API 키를 직접 저장합니다. 현재 녹음 파일 전사는 Groq와 OpenAI 키를 지원하고, Gemini/Claude/기타 키는 요약과 분석 담당자가 확장할 수 있도록 저장 구조만 먼저 열어두었습니다. 실제 배포 버전에서는 모바일 앱에 API 키를 넣지 말고 서버 프록시를 두는 구성이 안전합니다.
