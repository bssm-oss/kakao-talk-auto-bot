# kakao-talk-auto-bot

카카오톡 단톡방/개인방의 알림을 로컬에서 수집하고, 선택한 방에만 AI 자동응답을 붙일 수 있게 만든 안드로이드 앱입니다.

이 프로젝트는 더 이상 자유 스크립트형 봇 엔진이나 n8n 연동 앱이 아닙니다. 이제는 **방 선택 → 과거 대화 CSV 가져오기 → 내 페르소나/방 메모리 설정 → AI 공급자 API 키 입력 → ON/OFF 제어** 흐름에 맞춘 간단한 자동응답 앱을 목표로 합니다.

## 핵심 기능

- 카카오톡 알림을 기반으로 방별 세션을 확보하고 로컬에 대화 흐름 저장
- 특정 방만 골라 자동응답 대상에 추가
- CSV/텍스트 형태의 과거 대화 이력 가져오기
- 내 이름/페르소나, 방 메모리, 허용/차단 발화자, 트리거 조건 저장
- OpenAI / Claude(Anthropic) / Gemini API Key 기반 답장
- AI 답장 또는 고정 답장 모드 선택
- OFF 상태에서도 메시지 저장은 계속하고, 답장만 중지

## 현재 지원 범위

- **지원**: API Key 기반 OpenAI / Claude / Gemini 호출
- **지원**: AI 판단형 응답, 호출어/멘션 반응, 질문/명령형 반응, 고정 답장
- **미지원**: 소비자용 ChatGPT Plus/Pro, Gemini 유료 플랜을 제3자 앱 로그인으로 그대로 연결하는 OAuth 패스스루

위 OAuth 계열은 공급자 정책/공식 문서 기준으로 안전하게 보장되는 구현 경로가 아니어서 현재는 넣지 않았습니다. 대신 외부 관리 또는 직접 입력 방식의 API Key 경로를 기본으로 둡니다.

## 사용자 플로우

1. 앱 설치 후 **알림 접근 권한** 허용
2. **응답 설정 편집**에서 내 이름, 페르소나, 공급자, API Key 저장
3. **대상 방 관리**에서 자동응답할 방 추가
4. 방별 **CSV 이력 가져오기 / 방 메모리 / 허용·차단 발화자 / 트리거 / 고정 답장** 설정
5. 메인 화면에서 **AI 자동 답장** 스위치를 ON/OFF

## 저장 구조

- 방별 설정: 앱 내부 JSON 파일
- 방별 최근 대화: 앱 내부 JSON 파일
- 전역 설정/메타데이터: SharedPreferences
- 로그: 앱 내부 파일

모든 데이터는 기본적으로 기기 로컬에 저장되며, n8n 같은 외부 자동화 서버로 전송하지 않습니다.

## 개발 및 테스트

```bash
export JAVA_HOME="/Users/heodongun/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home"
./gradlew testDebugUnitTest assembleDebug
```

## 문서

- `docs/product-overview.md`
- `docs/user-flow.md`
- `docs/storage-and-memory.md`
- `docs/providers.md`
- `docs/ci-release.md`
- `docs/contributing-guide.md`
- `docs/agent-workflow.md`

## 오픈소스 기여

기여 전에는 반드시 `CONTRIBUTING.md` 와 `AGENTS.md` 를 먼저 읽어주세요. 이 저장소는 **작은 브랜치 / 작은 커밋 / PR 중심 작업 / docs 동반 변경**을 기본 규칙으로 삼습니다.
