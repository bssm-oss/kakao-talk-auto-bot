# Release distribution status

## 2026-04-05

- 현재 공개된 `v0.1.0` GitHub Release 자산은 `kakao-auto-reply-v0.1.0-debug.apk` 와 `kakao-auto-reply-v0.1.0-release-unsigned.apk` 뿐이며, 이것이 모바일 설치 경로를 불안정하게 만든 직접 원인이다.
- GitHub Release 게시 경로를 debug/unsigned APK 동봉 방식에서 signed release APK 단일 배포 방식으로 전환했다.
- `.github/workflows/release-apk.yml` 은 태그 릴리즈 시 GitHub Secrets 기반 서명값이 없으면 명확한 에러와 함께 실패하도록 수정했다.
- `app/build.gradle.kts` 는 서명 환경 변수가 있을 때만 release signingConfig 를 적용해, 로컬/일반 CI의 `assembleRelease` 검증 흐름은 유지한다.
- `README.md`, `docs/ci-release.md` 에서 debug/unsigned APK를 일반 사용자 설치 대상으로 안내하던 내용을 제거했다.
- 따라서 실제 공개 다운로드 경로는 다음 signed tag 릴리즈부터 정상화된다. 기존 `v0.1.0` 자산 자체가 자동으로 교체되지는 않는다.
