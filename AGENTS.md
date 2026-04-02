# AGENTS.md

## 프로젝트 정체성

- 프로젝트 이름: `kakao-talk-auto-bot`
- 제품 성격: 카카오톡 알림 기반 **AI 자동응답 앱**
- 금지: JS 스크립팅 봇 엔진 회귀, n8n/webhook 의존 로직 추가, 외부 로그 전송 복구

## 작업 원칙

1. 사용자가 명시하지 않은 큰 기능 확장은 하지 않는다.
2. 모든 기능 변경은 가능한 한 작은 브랜치/작은 커밋/작은 PR 단위로 나눈다.
3. 코드 변경 시 관련 문서를 반드시 `docs/` 아래에 추가 또는 갱신한다.
4. README, CONTRIBUTING, CI 문서, 테스트 문서가 코드와 어긋나면 함께 고친다.
5. OFF 상태에서도 메시지 저장은 유지되고, 답장만 중단되어야 한다.
6. 답장은 모든 메시지에 보내지 않는다. 방 설정/발화자 조건/트리거/AI 판단을 따른다.

## 문서 의무

- 기능/구조/정책을 바꾸면 `docs/` 아래에 대응 문서를 추가 또는 갱신한다.
- 문서 파일명은 의미가 분명해야 한다.
- README는 사용자 관점, `docs/` 는 구현/운영 관점으로 유지한다.
- 변경 유형별 기본 문서 매핑을 따른다.
  - UI/사용자 흐름 변경: `docs/user-flow.md`
  - 저장 구조/상태 변경: `docs/storage-and-memory.md`
  - 공급자/정책 변경: `docs/providers.md`
  - 테스트/CI/릴리즈 변경: `docs/ci-release.md`, `docs/testing-quality.md`
  - 오픈소스 운영 규칙 변경: `docs/open-source-maintenance.md`, `docs/contributing-guide.md`
  - AI 작업 규칙 변경: `docs/agent-workflow.md`
- 문서를 고치지 않고 코드만 바꾸는 것을 기본적으로 금지한다.

## Git 규칙

- 가능한 한 원자적 커밋을 만든다.
- 한 커밋에는 한 가지 목적만 넣는다.
- 큰 변경은 브랜치를 나눠 PR로 검토 가능한 형태로 유지한다.
- 되돌리기 쉬운 히스토리를 우선한다.
- 브랜치명은 `feat/`, `fix/`, `docs/`, `ci/`, `test/`, `refactor/` 접두사를 우선 사용한다.
- 커밋 메시지는 목적이 드러나게 작성한다. 예: `ci: split release validation from tag publish`
- 하나의 브랜치에서 여러 목적이 섞이기 시작하면 즉시 브랜치를 다시 나누는 것을 우선한다.

## 테스트 규칙

- 최소한 단위 테스트 + 빌드 검증은 항상 통과해야 한다.
- UI 흐름이 바뀌면 Maestro 시나리오를 함께 갱신한다.
- CI가 깨지면 기능 추가보다 먼저 CI를 복구한다.
- PR 전 기본 검증은 `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` 를 기준으로 한다.
- 릴리즈/CI를 건드리면 관련 GitHub Actions와 `docs/ci-release.md` 를 함께 갱신한다.
- 테스트가 부족한 기능을 만질 때는 가능한 한 단위 테스트를 추가하고, 사용자 흐름이면 Maestro를 추가한다.

## README 규칙

- README는 항상 한국어 사용자 문서로 유지한다.
- 배경 설명보다 현재 지원 범위, 빠른 시작, 검증 방법, 기여 진입점이 먼저 보여야 한다.
- 구현 세부사항은 README가 아니라 `docs/` 로 보낸다.

## 공급자 정책

- 기본 경로는 API Key 기반 OpenAI / Claude / Gemini 지원이다.
- 소비자용 유료 플랜 계정을 제3자 앱에서 그대로 쓰는 OAuth 패스스루는 공식 지원 여부가 불명확하므로 함부로 구현하지 않는다.
