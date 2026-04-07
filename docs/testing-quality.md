# 테스트 / 품질 전략

## 기본 원칙

- 빠른 회귀 방지는 JVM 단위 테스트가 맡습니다.
- 정적 품질 검사는 `lintDebug` 를 기본 게이트로 사용합니다.
- 실제 사용자 흐름 검증은 Maestro 에뮬레이터 테스트가 맡습니다.
- 릴리즈 태그는 테스트, lint, APK 빌드 검증이 끝난 뒤에만 signed release APK를 게시합니다.
- GitHub-hosted 에뮬레이터가 불안정할 때는 Maestro를 수동 실행으로 두고, CI 게이트는 JVM 테스트/lint/APK 빌드 중심으로 유지합니다.

## 로컬 기본 검증

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

UI 흐름을 바꿨다면 아래도 같이 확인합니다.

```bash
maestro test .maestro
```

## 단위 테스트 범위

- JSON 직렬화/역직렬화
- 방 이력 파싱
- 로컬 AI 응답 정규화
- 로컬 검색/트리거 판단 가드 로직

새 기능이 순수 함수로 분리 가능하면 가능한 한 JVM 테스트를 먼저 추가합니다.

## Maestro 범위

- 메인 화면 기본 진입
- 응답 설정 진입과 저장
- 대상 방 추가와 방 메모리 편집
- 테마 전환 기본 상호작용

UI 문구를 바꾸면 관련 Maestro 흐름도 같이 고쳐야 합니다.
현재 GitHub Actions에서는 수동 실행만 사용합니다.

## CI 아티팩트

- unit test reports
- lint HTML / text reports
- debug APK / release APK
- Maestro JUnit 결과
- Maestro debug output
- Maestro screenshots / test output
- emulator logcat

## 릴리즈 전 확인

- lint 에러 0개
- JVM 테스트 통과
- debug/release APK 생성 성공
- Maestro는 로컬 또는 수동 워크플로에서 확인
- 태그 릴리즈라면 GitHub Secrets 기반 signed release APK 게시 경로도 함께 확인
- README와 `docs/` 문서 정합성 확인
