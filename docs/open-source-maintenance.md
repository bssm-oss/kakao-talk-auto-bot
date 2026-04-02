# 오픈소스 운영 기준

## 저장소 기본 골격

- `LICENSE`: 오픈소스 사용 조건 명시
- `CONTRIBUTING.md`: 기여 진입점
- `CODE_OF_CONDUCT.md`: 커뮤니티 행동 기준
- `SECURITY.md`: 보안 제보 절차
- `.github/ISSUE_TEMPLATE/*`: 이슈 입력 품질 보장
- `.github/pull_request_template.md`: PR 품질 보장
- `AGENTS.md`, `docs/agent-workflow.md`: AI 작업 규칙

## 변경 단위 원칙

- 브랜치 하나에 목적 하나
- 커밋 하나에 목적 하나
- PR 하나에 검토 주제 하나
- 문서 없는 코드 변경 금지

## 릴리즈 운영

- 버전 태그는 `v*` 형식을 사용합니다.
- 태그 푸시 시 릴리즈 워크플로가 자동 실행됩니다.
- 릴리즈 전에 JVM 테스트, lint, Maestro가 모두 통과해야 합니다.
- APK는 GitHub Release 자산으로 첨부합니다.

## 문서 운영

- README는 사용자용 한국어 문서로 유지합니다.
- `docs/` 는 구현/운영/정책 문서로 유지합니다.
- 변경 유형에 맞는 문서를 같이 고치는 것을 기본 규칙으로 합니다.

## 아직 유지보수자가 결정해야 하는 항목

- 보안 제보용 공식 연락 채널
- 릴리즈 서명 전략
- 장기 지원 브랜치 운영 여부
