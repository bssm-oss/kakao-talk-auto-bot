# AI 에이전트 작업 규칙

이 저장소에서 AI 에이전트는 다음을 지켜야 합니다.

1. n8n/webhook/외부 로그 전송을 다시 넣지 않는다.
2. 큰 변경은 작은 브랜치/작은 커밋/PR 단위로 쪼갠다.
3. 코드 변경 시 `docs/` 문서를 함께 갱신한다.
4. README는 사용자용, `docs/` 는 구현/운영용으로 구분한다.
5. 테스트/CI가 깨지면 기능 추가보다 복구를 우선한다.
6. 답장 로직은 무조건 응답이 아니라 선택적 응답 원칙을 따른다.
7. AI 경로는 로컬 Kotlin 검색/답장 엔진 기준으로 다루고, 원격 API 지원을 되살리는 설명이나 정책을 넣지 않는다.
8. UI/플로우 변경 시 `docs/user-flow.md` 와 Maestro 흐름을 같이 갱신한다.
9. CI/릴리즈 변경 시 `.github/workflows/*`, `docs/ci-release.md`, `docs/testing-quality.md` 를 같이 갱신한다.
10. signed release APK 게시 방식이나 로컬 AI 전환 상태가 바뀌면 상태 문서도 함께 갱신한다.
11. 오픈소스 운영 규칙 변경 시 `CONTRIBUTING.md`, `docs/contributing-guide.md`, `docs/open-source-maintenance.md` 를 같이 갱신한다.
12. README는 한국어 사용자 문서로 유지하고, 구현 세부사항을 과도하게 넣지 않는다.
13. 하나의 변경이 커지면 브랜치/커밋/PR을 다시 쪼개는 것을 우선한다.
