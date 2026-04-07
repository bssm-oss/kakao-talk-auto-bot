# CI / 릴리즈 정책

## CI

- `Android CI`
  - `testDebugUnitTest`
  - `lintDebug`
  - `assembleDebug`
  - `assembleRelease`
  - 테스트/린트/APK 아티팩트 업로드
- `Maestro UI Test`
  - 현재는 GitHub-hosted 에뮬레이터 인식 문제 때문에 `workflow_dispatch` 수동 실행 전용
  - 로컬 또는 별도 안정화 후 다시 PR/릴리즈 게이트에 편입
  - JUnit 결과, debug output, 테스트 아티팩트, logcat 업로드

## 릴리즈 게이트

- 태그 릴리즈 전에는 JVM 테스트, lint, debug/release APK 빌드가 모두 통과해야 한다.
- 릴리즈 워크플로는 `validate` 잡이 성공한 뒤에만 GitHub Release를 생성한다.
- Maestro는 로컬 또는 수동 워크플로 검증 자산으로 유지한다.
- `validate` 단계의 `assembleRelease` 는 서명 시크릿이 없어도 계속 실행 가능해야 하며, 이 경우 unsigned 결과물은 검증용으로만 취급한다.

## 릴리즈

- `v*` 태그 푸시 시 GitHub Release 생성
- GitHub Release에는 최종 사용자용 signed release APK 1개만 첨부
- 릴리즈 노트는 GitHub 자동 생성 노트를 기본으로 사용
- 릴리즈 자산 이름은 `kakao-auto-reply-vX.Y.Z.apk` 형식으로 재패키징한다
- debug APK와 unsigned release APK는 CI 검증 산출물일 수 있지만 GitHub Release의 일반 설치 자산으로 게시하지 않는다

## 릴리즈 서명 시크릿

- 태그 릴리즈 게시에는 아래 GitHub Secrets가 모두 필요하다.
  - `ANDROID_RELEASE_KEYSTORE_BASE64`
  - `ANDROID_RELEASE_STORE_PASSWORD`
  - `ANDROID_RELEASE_KEY_ALIAS`
  - `ANDROID_RELEASE_KEY_PASSWORD`
- 워크플로는 위 시크릿 중 하나라도 비어 있으면 릴리즈 잡에서 즉시 실패해야 한다.
- CI는 `ANDROID_RELEASE_KEYSTORE_BASE64` 를 임시 keystore 파일로 복호화한 뒤 환경 변수 `ANDROID_RELEASE_STORE_FILE` 로 Gradle에 전달한다.
- 로컬 개발과 일반 CI 검증에서는 위 환경 변수가 없어도 `assembleRelease` 자체는 유지되며, 이 경우 결과물은 서명되지 않은 검증용 APK다.

## JDK

- 이 저장소의 Gradle/Kotlin 조합은 **JDK 21 기준**으로 검증합니다.
- 기본 `java` 가 JDK 25 이상이면 Gradle Kotlin DSL 초기화 단계에서 실패할 수 있습니다.
- `gradlew` bootstrap 단계에서 호환 가능한 JDK 21을 먼저 찾고, 없으면 JDK 17, JDK 11 순으로 fallback 합니다.
- JDK 기준을 바꾸면 `gradlew` 의 bootstrap 탐색 순서와 README 안내를 함께 갱신해야 합니다.
- CI와 로컬 개발 모두 가능하면 `JAVA_HOME` 을 JDK 21로 고정하는 것을 권장합니다.
