# 카톡 자동답장 도우미

카카오톡 알림을 로컬에서 수집하고, 선택한 방에만 AI 자동응답을 연결하는 안드로이드 앱입니다. 모든 데이터를 기기 안에 저장하고, 방별 메모리와 트리거 조건을 분리해 과도한 자동응답을 막는 데 초점을 맞춥니다.

## 한눈에 보기

| 항목 | 내용 |
| --- | --- |
| 응답 대상 | 모든 방이 아니라 선택한 방만 응답 |
| 저장 방식 | 대화/설정/로그를 기기 로컬에 저장 |
| AI 공급자 | OpenAI / Claude(Anthropic) / Gemini API Key |
| 응답 조건 | AI 판단, 호출어/멘션, 질문/명령, 키워드, 고정 답장 |
| 운영 제어 | OFF 상태에서도 메시지 저장 유지, 답장만 중지 |
| UI | 메인 화면에서 상태, 대상 방, 설정, 로그, 라이트/다크 모드 제어 |

## 핵심 기능

- 카카오톡 알림을 세션으로 묶어 방별 최근 대화를 저장합니다.
- 자동응답할 방만 별도로 등록하고 방별 메모리를 관리합니다.
- CSV/텍스트 이력을 가져와 방 컨텍스트를 보강할 수 있습니다.
- 내 이름, 페르소나, 공급자, API Key 보관 방식, 기본 트리거 성향을 저장합니다.
- 메인 화면에서 AI 자동 답장을 즉시 ON/OFF 할 수 있습니다.
- 라이트/다크/시스템 테마를 앱 안에서 전환하고 유지합니다.
- 최근 대화 기준으로 방별 자동 메모리를 생성해 프롬프트에 함께 반영합니다.
- 최근 내 발화 기준으로 자동 페르소나 힌트를 생성해 말투를 보정합니다.

## 지원 범위

- 지원: API Key 기반 OpenAI / Claude / Gemini 호출
- 지원: AI 판단형 응답, 멘션 반응, 질문/명령형 반응, 고정 답장
- 미지원: 소비자용 ChatGPT Plus/Pro, Gemini 유료 플랜을 제3자 앱 로그인으로 그대로 연결하는 OAuth 패스스루

공급자 정책과 안정성 문제 때문에 OAuth 패스스루 대신 공식적으로 검증 가능한 API Key 경로만 유지합니다.

## 빠른 시작

1. 앱 설치 후 알림 접근 권한을 허용합니다.
2. `응답 설정 편집`에서 내 이름, 페르소나, 공급자, API Key를 저장합니다.
3. `대상 방 관리`에서 자동응답할 방을 추가합니다.
4. 필요하면 방별 CSV 이력과 메모리를 불러옵니다.
5. 메인 화면에서 `AI 자동 답장`을 켭니다.
6. 고정 답장 모드에서 `@허동운` 같은 멘션/키워드 트리거에 특정 문구를 즉시 응답할 수 있습니다.

## 개발

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

- 기본 검증 JDK는 21입니다.
- 셸 기본 `java`가 25 이상이면 `gradlew`가 호환 가능한 JDK 21을 먼저 찾고, 없으면 17, 11 순으로 fallback 합니다.
- 자동 탐색이 막힌 환경이면 `JAVA_HOME`을 JDK 21로 명시하는 편이 가장 안정적입니다.

## CI / 릴리즈

- `Android CI`: JVM 테스트, lint, debug/release APK 빌드, 리포트 업로드
- `Maestro UI Test`: 현재는 GitHub-hosted 에뮬레이터 불안정으로 `workflow_dispatch` 수동 실행 전용
- `Release APK`: `v*` 태그 푸시 시 검증을 다시 수행한 뒤 GitHub Release에 APK 첨부

## APK 다운로드

- 가장 쉬운 설치 경로는 GitHub `Releases`의 최신 버전에서 APK를 받는 방식입니다.
- 릴리즈 자산 이름은 아래처럼 고정됩니다.
  - `kakao-auto-reply-vX.Y.Z-debug.apk`
  - `kakao-auto-reply-vX.Y.Z-release-unsigned.apk`
- `debug.apk`는 바로 설치 테스트용, `release-unsigned.apk`는 릴리즈 후보 검증용입니다.

세부 운영 절차는 `docs/ci-release.md`, `docs/testing-quality.md`, `docs/open-source-maintenance.md`를 참고하세요.

## 문서

- `docs/product-overview.md`
- `docs/user-flow.md`
- `docs/storage-and-memory.md`
- `docs/providers.md`
- `docs/ci-release.md`
- `docs/testing-quality.md`
- `docs/open-source-maintenance.md`
- `docs/contributing-guide.md`
- `docs/agent-workflow.md`

## 기여 안내

- 기여 전에 `CONTRIBUTING.md`, `AGENTS.md`, `docs/contributing-guide.md`를 먼저 읽어 주세요.
- 이 저장소는 작은 브랜치, 작은 커밋, 작은 PR, 문서 동반 변경을 기본 원칙으로 합니다.
- UI/플로우/테스트/CI/릴리즈를 바꾸면 관련 `docs/` 문서도 반드시 함께 갱신해야 합니다.

## 보안과 커뮤니티

- 커뮤니티 참여 규칙은 `CODE_OF_CONDUCT.md`
- 보안 제보 절차는 `SECURITY.md`
- 이슈와 PR 작성 형식은 `.github/ISSUE_TEMPLATE`, `.github/pull_request_template.md`

## 라이선스

이 프로젝트는 [MIT License](/Users/Projects/bssm-oss/kakao-talk-auto-bot/LICENSE)를 따릅니다.
