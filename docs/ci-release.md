# CI / 릴리즈 정책

## CI

- `Android CI`
  - `testDebugUnitTest`
  - `lintDebug`
  - `assembleDebug`
  - `assembleRelease`
  - 테스트/린트/APK 아티팩트 업로드
- `Maestro UI Test`
  - 에뮬레이터에서 `.maestro/` 전체 흐름 실행
  - JUnit 결과, debug output, 테스트 아티팩트, logcat 업로드

## 릴리즈 게이트

- 태그 릴리즈 전에는 JVM 테스트, lint, debug/release APK 빌드가 모두 통과해야 한다.
- 태그 릴리즈 전에는 Maestro UI 흐름이 통과해야 한다.
- 릴리즈 워크플로는 검증 잡이 성공한 뒤에만 GitHub Release를 생성한다.

## 릴리즈

- `v*` 태그 푸시 시 GitHub Release 생성
- debug APK와 release APK를 릴리즈 자산에 첨부
- 릴리즈 노트는 GitHub 자동 생성 노트를 기본으로 사용
- 릴리즈 자산은 사용자가 바로 구분할 수 있게 아래 이름으로 재패키징합니다.
  - `kakao-auto-reply-vX.Y.Z-debug.apk`
  - `kakao-auto-reply-vX.Y.Z-release-unsigned.apk`
- 서명 전략이 추가되면 이 문서를 먼저 갱신한다

## JDK

- 이 저장소의 Gradle/Kotlin 조합은 **JDK 21 기준**으로 검증합니다.
- 기본 `java` 가 JDK 25 이상이면 Gradle Kotlin DSL 초기화 단계에서 실패할 수 있습니다.
- `gradlew` bootstrap 단계에서 호환 가능한 JDK 21을 먼저 찾고, 없으면 JDK 17, JDK 11 순으로 fallback 합니다.
- JDK 기준을 바꾸면 `gradlew` 의 bootstrap 탐색 순서와 README 안내를 함께 갱신해야 합니다.
- CI와 로컬 개발 모두 가능하면 `JAVA_HOME` 을 JDK 21로 고정하는 것을 권장합니다.
