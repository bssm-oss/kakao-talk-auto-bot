# CI / 릴리즈 정책

## CI

- 단위 테스트 실행
- 디버그 APK 빌드
- 아티팩트 업로드
- Maestro 스모크 테스트 실행

## 릴리즈

- `v*` 태그 푸시 시 GitHub Release 생성
- 기본적으로 디버그 APK를 릴리즈 자산에 첨부
- 서명용 시크릿이 있으면 release APK도 함께 생성 가능하도록 확장 여지 유지

## JDK

이 저장소의 Gradle/Kotlin 조합은 JDK 21 기준 검증을 권장합니다.
