# 로컬 AI 전환 상태

## 이번 단계 완료 범위

- `AiProviderClient`를 원격 API 호출 대신 로컬 Kotlin 검색/답장 엔진으로 교체
- 최근 방 이력, 방 메모리, 자동 메모리, CSV 기반 맥락을 재사용하도록 연결
- `ai_judge` / `smart` 모드에서 저확신 시 답장 보류 허용
- 설정 UI를 **Gemma 4 로컬 전용** 흐름으로 정리하고 예전 OpenAI 설정은 읽을 때 자동 정규화
- 로컬 답장 실행 경로를 raw `Thread` 대신 coroutine 기반 백그라운드 실행으로 정리
- 더 이상 쓰지 않는 `SharedHttpClient` 및 OkHttp 의존성 제거
- primary/compact/emergency 프롬프트 모두에서 페르소나, 방 메모, 최근 대화 맥락 유지 강화
- grounding을 우회하던 small-talk fast-path 제거

## 유지한 범위

- OFF 상태에서도 메시지 저장 유지, 답장만 중단
- 방별 선택 응답 / 허용·차단 발화자 / 트리거 규칙
- 고정 답장(`canned`) 모드

## 후속 메모

- 현재 제품 경로는 Gemma 4 로컬 전용입니다.
- Gemma 4 LiteRT-LM 최종 검증은 실기기 ARM64 Android가 필요하며, 에뮬레이터는 지원하지 않습니다.
- Gemma 4 완료 판정은 아래 증거가 모두 있어야 합니다: `adb devices -l` 에서 실기기 ARM64 확인, 앱 내 모델 다운로드 완료, readiness/load 성공, 실제 프롬프트 1회 이상 생성 성공.
- 위 증거가 없으면 상태는 "코드 준비 완료" 이며, "Gemma 4 동작 검증 완료" 로 간주하지 않습니다.
- 더 높은 정답률이 필요하면 저장 구조는 유지한 채 검색 점수 규칙과 응답 템플릿을 보강하는 방향으로 확장할 수 있습니다.
