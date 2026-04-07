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
- 릴리즈 전에 JVM 테스트, lint, debug/release APK 빌드 검증이 통과해야 합니다.
- Maestro는 현재 수동 검증 자산으로 유지하고, UI 변경 시 로컬 또는 수동 워크플로 결과를 함께 확인합니다.
- GitHub Release에는 GitHub Secrets로 서명한 release APK 1개만 최종 사용자용 자산으로 첨부합니다.
- debug/unsigned APK는 검증 산출물일 수 있지만 일반 배포 자산으로 안내하지 않습니다.

## 문서 운영

- README는 사용자용 한국어 문서로 유지합니다.
- `docs/` 는 구현/운영/정책 문서로 유지합니다.
- 변경 유형에 맞는 문서를 같이 고치는 것을 기본 규칙으로 합니다.
- 로컬 AI 경로나 릴리즈 배포 정책을 바꾸면 상태 문서까지 같이 갱신합니다.
  - 로컬 AI 전환 상태: `docs/local-ai-migration-status.md`
  - 릴리즈 배포 상태: `docs/release-distribution-status.md`

## 유지보수 메모

- 보안 제보용 공식 연락 채널은 별도 확정 시 `SECURITY.md` 와 함께 갱신합니다.
- 장기 지원 브랜치 운영 여부는 필요 시 별도 문서로 결정합니다.
