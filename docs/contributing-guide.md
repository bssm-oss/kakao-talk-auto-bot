# 기여 운영 가이드

## 브랜치 전략

- 기능 하나당 브랜치 하나
- 리팩터링/문서/CI 변경도 가능하면 분리
- 권장 브랜치 접두사: `feat/`, `fix/`, `docs/`, `ci/`, `test/`, `refactor/`

## 커밋 전략

- 원자적 커밋
- 되돌리기 쉬운 크기
- 한 커밋에 한 목적
- 브랜치 안에서도 기능/문서/CI가 섞이면 커밋을 더 잘게 나눈다.

## PR 전략

- 변경 이유와 영향 범위 설명
- 테스트 결과 첨부
- 문서 반영 여부 체크
- 여러 목적이 섞인 PR보다 작은 PR 여러 개를 우선한다.

## 문서 매핑

- UI/사용자 흐름 변경: `docs/user-flow.md`
- 저장 구조 변경: `docs/storage-and-memory.md`
- 공급자/정책 변경: `docs/providers.md`
- 테스트/CI/릴리즈 변경: `docs/ci-release.md`, `docs/testing-quality.md`
- 오픈소스 운영 규칙 변경: `docs/open-source-maintenance.md`
- AI 작업 지침 변경: `docs/agent-workflow.md`

## 권장 체크리스트

- [ ] 단위 테스트 통과
- [ ] lint 통과
- [ ] 디버그 APK 빌드 성공
- [ ] 릴리즈 APK 빌드 성공
- [ ] Maestro 시나리오 갱신 또는 확인
- [ ] README / docs / AGENTS.md 갱신
