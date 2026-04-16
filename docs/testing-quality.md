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
- 로컬 LLM 프롬프트 구성과 응답 정규화
- 레거시 OpenAI/로컬 공급자 설정의 로컬 Gemma 정규화
- 로컬 검색/트리거 판단 가드 로직
- 알림 수집과 답장 시도 분리 가드 로직
- OFF 상태 또는 방별 답장 비활성화일 때도 메시지 수집은 유지되고 답장만 막히는 가드 로직
- 기기에 LiteRT-LM 모델이 있을 때의 실제 로컬 LLM 생성 계측 테스트
- 실제 대화 맥락을 넣었을 때의 로컬 LLM 문맥 응답 계측 테스트

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

## 최근 로컬 수동 계측 결과

회귀 테스트와 계측 테스트는 작은 테스트 모델(`Qwen3-0.6B.litertlm`) 기준으로 수행합니다.

기본 프로덕션 로컬 모델은 `Gemma-4-E2B-it-LiteRT-LM` 이지만, 테스트에서는 다운로드 시간/저장공간 때문에 더 작은 모델을 사용합니다.

프로덕션 기본 모델(`Gemma-4-E2B-it-LiteRT-LM`) 검증은 자동 계측 테스트가 아니라 수동 검증으로 수행합니다.

현재 Gemma 4 LiteRT-LM 경로는 **실기기 ARM64 Android**에서만 최종 검증 대상으로 취급하고, 에뮬레이터는 지원하지 않습니다.

- 간단 인사 프롬프트
  - 입력: `한 문장으로 짧게 인사해 줘.`
  - 출력: `짧게 인사해 줘`
  - 소요 시간: 약 `22.7s`
- 대화 맥락 프롬프트
  - 입력: 최근 대화에 `오늘 회의는 3시에 시작해.` 를 포함한 뒤 `회의 몇 시에 시작이야?` 질문
  - 출력: `이전 대화 ??`
  - 소요 시간: 약 `69.1s`

해석:

- 위 수치는 **작은 공개 테스트 모델(Qwen3-0.6B LiteRT)** 기준 계측입니다.
- 이 결과는 LiteRT-LM 런타임 경로가 동작한다는 근거이지만, **프로덕션 기본 모델(Gemma 4 E2B)** 의 실제 품질/속도 수치로 해석하면 안 됩니다.
- Gemma 4 E2B LiteRT-LM 경로는 현재도 **실기기 ARM64 Android 수동 검증**이 최종 기준입니다.

## 최근 빈 응답 대응

- 긴 프롬프트에서 로컬 모델이 **0글자 응답**을 반환하는 사례가 확인되었습니다.
- 로그캣 기준 근본 원인은 Gemma 4 LiteRT-LM이 긴 프롬프트에서 `LiteRtLmJniException (Status Code: 13, Failed to invoke the compiled model)` 를 내고 빈 문자열을 반환하던 경로였습니다.
- 현재는 기본 컨텍스트 예산을 키우고, 1차 긴 프롬프트 실패 시 더 작은 history/persona budget을 쓰는 **compact prompt** 로 재시도합니다.
- compact prompt도 실패하면 더 작은 예산의 **emergency prompt** 로 한 번 더 재시도하되, 최소 페르소나/방 메모/최근 대화는 유지합니다.
- 이 완화는 "모델 호출 실패가 그대로 빈 응답으로 보이는" 경로를 줄이기 위한 안정성 보강입니다.

## 최근 빠른 단축 경로 검증

- 일정 미확정 질문 `아녕하세요 프로젝트 언제까지 되나요?`
  - 출력: `아직 일정이 확정된 건 못 찾았어. 정리되면 바로 공유할게.`
  - 에뮬레이터 계측: 약 `23ms`
  - 경로: 로컬 LLM 생성 대신 빠른 deadline shortcut

해석:

- 현재 기본 로컬 경로는 **최근 사실 확인 질문**과 **일정 미확정 확인 질문**에 대해 근거가 분명할 때만 모델 생성 없이 빠르게 응답합니다.
- 생활형 small-talk 은 grounding 우회를 줄이기 위해 빠른 단축 경로에서 제거했고, 대신 로컬 프롬프트가 페르소나/방 메모를 보고 답하도록 유지합니다.

## 최근 구성 마이그레이션 / 수집 가드 회귀 보강

- 저장된 레거시 로컬 공급자 값 `local` / `local-gguf` / `local-litertlm` 는 현재 표준값 `llm` / `gemma-4-e2b-it-litertlm` 로 정규화합니다.
- 알림 처리 경로는 전역 OFF 또는 방별 `replyEnabled = false` 상태에서도 **메시지 수집은 유지하고 답장만 중단**하도록 JVM 테스트로 회귀를 막습니다.

## Gemma 4 실기기 완료 기준

- 아래 네 가지가 모두 확인되어야 Gemma 4 경로를 "완료" 로 봅니다.
  1. `adb devices -l` 에서 **실기기 ARM64 Android** 연결 확인
  2. 앱 내 Gemma 4 LiteRT-LM 모델 다운로드 완료
  3. 모델 readiness / load 성공 로그 또는 UI 상태 확인
  4. 실제 프롬프트에 대해 생성 결과 1회 이상 확인
- 위 네 가지 중 하나라도 빠지면 상태는 "빌드/테스트 통과, 런타임 미검증" 으로 기록합니다.
