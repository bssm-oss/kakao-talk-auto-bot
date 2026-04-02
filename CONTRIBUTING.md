# CONTRIBUTING

`kakao-talk-auto-bot` 기여 가이드입니다.

## 기본 원칙

- 한 번에 너무 큰 변경을 올리지 않습니다.
- 브랜치를 나눠 작업하고, 작은 커밋과 작은 PR을 선호합니다.
- 코드 변경에는 관련 문서(`docs/`) 업데이트를 함께 포함합니다.
- CI, 테스트, README가 깨진 상태로 PR을 열지 않습니다.

## 권장 흐름

1. 이슈 또는 작업 범위 정리
2. 전용 브랜치 생성
3. 작은 단위로 구현
4. 단위 테스트 / 빌드 / Maestro 확인
5. 문서 갱신
6. PR 생성

## 필수 확인

- `./gradlew testDebugUnitTest assembleDebug`
- 관련 Maestro 시나리오
- README / docs / AGENTS.md 반영 여부

세부 운영 문서는 `docs/contributing-guide.md` 를 참고하세요.
