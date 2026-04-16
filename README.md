# 카톡 자동답장 도우미

카카오톡 알림을 로컬에서 수집하고, 선택한 방에만 **Gemma 4 로컬 모델** 기반 AI 자동응답을 연결하는 안드로이드 앱입니다. 모든 데이터를 기기 안에 저장하고, 인터넷 없이도 AI 답장이 작동합니다.

## 한눈에 보기

| 항목 | 내용 |
| --- | --- |
| 응답 대상 | 선택한 방 또는 모든 방 (토글 가능) |
| AI 엔진 | Gemma 4 로컬 |
| 기본 로컬 모델 | Gemma 4 E2B LiteRT-LM (한국어 품질 우선, ~2.58GB) |
| 저장 방식 | 대화/설정/로그를 기기 로컬에 저장 |
| 응답 조건 | AI 판단, 호출어/멘션, 질문/명령, 키워드, 고정 답장 |
| 운영 제어 | OFF 상태에서도 메시지 저장 유지, 답장만 중지 |
| UI | 라이트/다크 모드, 키보드 자동 스크롤 |
| 다운로드 | [최신 GitHub Release](https://github.com/bssm-oss/kakao-talk-auto-bot/releases/latest) / [전체 Releases 목록](https://github.com/bssm-oss/kakao-talk-auto-bot/releases) |

## 핵심 기능

- **Gemma 4 로컬 답장**: LiteRT-LM 기반 Gemma 4 `.litertlm` 모델로 인터넷 없이 AI 답장 생성
- **모델 다운로드**: 로컬 경로에서는 앱 안에서 Gemma 4 E2B LiteRT-LM 모델 다운로드 (~2.58GB)
- **카카오톡 알림 기반**: 알림 리스너로 메시지를 감지하고 PendingIntent로 답장
- **방별 메모**: 각 채팅방별 컨텍스트를 메모로 저장, AI가 답변 시 참고
- **모든 방 모드**: 토글 하나로 모든 채팅방에서 AI 답장 활성화
- **페르소나 설정**: 내 이름, 말투, 답변 스타일을 커스터마이징
- **자동 대화 요약**: 최근 대화를 자동으로 분석해 메모에 반영
- **강화된 로컬 그라운딩**: primary/compact/emergency 모든 프롬프트에서 페르소나, 방 메모, 최근 대화 맥락을 최대한 유지
- **트리거 조건**: AI 판단, 멘션, 질문, 키워드 등 다양한 반응 조건

## 지원 범위

- 지원: Gemma 4 로컬 모델 (`.litertlm`, LiteRT-LM 런타임, CPU 우선)
- 지원: LiteRT-LM 호환 Gemma/Qwen 계열 모델
- 지원: AI 판단형 응답, 멘션 반응, 질문/명령형 반응, 고정 답장
- 미지원: 원격 OpenAI/Codex API 경로, 소비자용 ChatGPT/Pro 로그인 재사용, OAuth 패스스루
- 미지원: GPU 가속 (현재 CPU 전용)

## 설치 APK 다운로드

- **APK 받으러 바로 가기**: [최신 GitHub Release](https://github.com/bssm-oss/kakao-talk-auto-bot/releases/latest)
- **지난 버전 릴리즈 보기**: [전체 Releases 목록](https://github.com/bssm-oss/kakao-talk-auto-bot/releases)

최신 릴리즈에서 첨부된 APK 설치 파일을 바로 다운로드할 수 있습니다.

## 빠른 시작

1. 앱 설치 후 알림 접근 권한을 허용합니다.
2. `AI 답장 설정`에서 내 이름과 페르소나를 저장합니다.
3. 기본 Gemma 4 로컬 경로를 쓸 경우 **Gemma 4 다운로드** 버튼으로 모델을 받습니다 (~2.58GB).
4. `대상 방 관리`에서 자동응답할 방을 추가하거나, `모든 방에서 답장`을 켭니다.
5. 방별 `메모`에서 AI가 참고할 컨텍스트를 적습니다.
6. 메인 화면에서 `AI 자동 답장`을 켭니다.

## 모델

앱은 **Gemma-4-E2B-it-LiteRT-LM** 로컬 모델만 사용합니다. 이전 버전에서 저장된 OpenAI 설정이 남아 있어도 실행 시 자동으로 로컬 Gemma 설정으로 정리됩니다.

| 모델 | 크기 | 한국어 | 비고 |
| --- | --- | --- | --- |
| Gemma 4 E2B LiteRT-LM | ~2.58GB | 좋음 | 기본값, Gemma 4 경로 |
| Qwen3 0.6B LiteRT | ~614MB | 기본 수준 | 공개 테스트/회귀 검증용 |
| Gemma 3 1B LiteRT | ~555MB | 양호 | Gemma 계열 테스트 대안 |

모델은 앱 내 다운로드 기능으로 받을 수 있으며, LiteRT-LM 호환 `.litertlm` 파일을 직접 추가할 수도 있습니다.

## 성능 가이드

- 기본 Gemma 4 E2B 모델은 저장 공간보다 **한국어 품질과 답변 자연스러움**을 우선합니다.
- compact/emergency 재시도에서도 페르소나, 방 메모, 최근 대화를 일부라도 유지해 답장 맥락 손실을 줄입니다.
- 테스트와 회귀 검증에는 더 작은 LiteRT-LM 공개 모델을 사용해 다운로드 시간과 저장공간 부담을 줄입니다.
- 실제 기기 성능은 기기 RAM/CPU와 모델 크기에 따라 크게 달라집니다.
- Gemma 4 LiteRT-LM 경로는 **실제 ARM64 Android 기기**에서만 검증 대상으로 취급하며, 현재 에뮬레이터는 지원하지 않습니다.
- 더 큰 모델일수록 한국어 품질은 좋아질 수 있지만 첫 로딩과 답장 생성은 더 느려집니다.
- 최소 2GB RAM 권장
- 에뮬레이터보다 실제 기기에서 속도가 더 현실적으로 나옵니다.

## 개발

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

- NDK 27 이상 필요 (앱 내 네이티브 컴포넌트 빌드용)
- CMake 3.22 이상 필요
- LiteRT-LM Android SDK를 사용합니다
- 로컬 Hugging Face 토큰이 필요하면 프로젝트 루트 `.env` 에 `HF_TOKEN=...` 형식으로 넣습니다 (`.env` 는 gitignore 처리됨)

## CI / 릴리즈

- `Android CI`: JVM 테스트, lint, debug/release APK 빌드
- `Release APK`: `v*` 태그 푸시 시 서명된 release APK를 GitHub Release에 첨부

## 보안과 커뮤니티

- 커뮤니티 참여 규칙은 `CODE_OF_CONDUCT.md`
- 보안 제보 절차는 `SECURITY.md`
- 기여 전에 `CONTRIBUTING.md`, `AGENTS.md`를 읽어 주세요.

## 라이선스

이 프로젝트는 [MIT License](LICENSE)를 따릅니다.

llama.cpp는 [MIT License](https://github.com/ggml-org/llama.cpp)를 따릅니다.
