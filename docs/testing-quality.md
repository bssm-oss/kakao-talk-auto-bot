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

친구방/팀방/학교방/친구방 짧은 echo 방지/근거 있는 질문의 generic ack 방지/학교방 장황한 부연 방지/애매한 말/낮은 신호/맥락 있는 낮은 신호의 짧은 반응/모르는 사실/방 메모 질문/수동 예시 우선순위 충돌의 고정 답변 품질 리포트는 아래 명령으로 생성합니다.

```bash
scripts/generate-reply-quality-report.sh
```

리포트는 `app/build/reports/reply-quality/report.md` 에 생성됩니다. `Expected Reply Examples` 는 각 시나리오의 기대 예문, trait, 점수, 최소 기준, 통과 여부를 표로 남기고, 수동 예시 우선순위 충돌 시나리오는 존댓말 이력이 있더라도 직접 적은 예시와 가까운 답장이 통과하는지 확인합니다. 친구방 짧은 echo 방지 시나리오는 모델이 `오늘 뭐함` 같은 짧은 수신 문장을 그대로 따라 쓰지 않고 사용자 예시와 가까운 실제 답장을 선택하는지 확인합니다. 친구방 업무체 ACK 방지 시나리오는 `확인했습니다!` 같은 단독 업무 답장 대신 `아무것도 없긴해` 같은 직접 예시 답장을 고르는지 확인합니다. 친구방 서비스봇 사과/안내 방지 시나리오는 `죄송합니다`, `안내드리겠습니다`, `도움이 되셨길` 같은 상담원식 문장 대신 직접 예시와 가까운 짧은 반말을 고르는지 확인합니다. 근거 있는 질문의 generic ack 방지 시나리오는 방 메모나 최근 대화에 답이 있는데도 `네 확인했습니다`처럼 넘기지 않고 바로 사실을 답하는지 확인합니다. 학교방 장황한 부연 방지 시나리오는 `추가로 필요한 내용이 있으면 말씀해 주세요` 같은 도우미식 부연 대신 짧은 실제 답장을 선택하는지 확인합니다. 맥락 있는 낮은 신호 시나리오는 `ㅇㅋ` 같은 리액션에 `필요하면 추가로 알려줘`처럼 길게 덧붙이지 않고 짧은 확인 반응만 고르는지 확인합니다. `Engine Baseline Without LLM` 은 모델 호출 없이 실제 엔진이 처리할 수 있는 `pre_model_skip`, `ambiguous_clarify`, `unknown_guard`, `deadline_fact` 경로의 source/coverage/pass 결과를 남깁니다. 맥락 없는 낮은 신호는 `pre_model_skip` 으로 빠져야 하지만, 최근 대화 맥락이 있는 낮은 신호는 `requires_llm` 로 남겨 사람처럼 짧게 반응할 수 있어야 합니다. 친구방/팀방/학교방과 수동 예시 우선순위처럼 모델 생성이 필요한 말투 응답은 `requires_llm` 으로 표시해 실기기 LLM 검증과 분리합니다.

ARM64 실기기에서 Gemma 기본 모델과 실제 런타임 경로를 확인할 때는 아래 스크립트를 사용합니다.

```bash
scripts/verify-real-device-e2e.sh
```

이 스크립트는 한 대의 ARM64 실기기 연결을 요구하고, debug APK와 androidTest APK 설치, Gemma 계측 테스트 실행, 모델 파일 크기/SHA-256 확인, 로그캣 저장까지 수행합니다. ADB에 에뮬레이터가 같이 떠 있으면 에뮬레이터는 진단에만 남기고 ARM64 실기기만 정확히 1대 선택합니다. 요약 파일에는 연결 기기 수, 실기기/에뮬레이터/비 ARM64 기기 수, 기기 브랜드/모델/Android 버전, 알림 리스너 권한 상태(`notification_listener_enabled`), 계측 테스트 상태(`instrumentation_status`), 모델 파일 증거, 모델 로드 로그 감지(`model_load_log_detected`), 생성 응답 로그 감지(`model_generation_log_detected`), 생성/후보 로그 카운트, 로그캣 경로, 앱 내부 로그 경로(`app_log_file`)가 함께 남습니다. 카카오톡 알림 수신과 실제 자동 답장 표시는 계정/알림 권한이 필요한 수동 E2E 단계로 남기며, 스크립트가 생성하는 `outputs/real-device-e2e/*-summary.txt` 에 `manual_kakao_*` 증거 필드와 `manual_kakao_pending_reason` 을 남깁니다. `MANUAL_KAKAO_TEST_ROOM` 과 `MANUAL_KAKAO_TEST_SENDER` 를 넘기면 앱 내부 `files/logs/app.log` 를 `outputs/real-device-e2e/*-app-log.txt` 로 복사하고, 해당 방의 `IN`, 최신 `OUT` 또는 최신 `OUT_FAIL` 로그와 실패 reason 을 자동 감지해 `manual_kakao_auto_*` 필드에 기록합니다. 오래된 `OUT_FAIL` 뒤에 최신 `OUT` 성공이 있으면 최신 성공을 기준으로 실패 reason 을 비웁니다. 실제 카카오톡 대화창에 답장이 보였는지는 여전히 사람이 확인한 뒤 `MANUAL_KAKAO_REPLY_VISIBLE_IN_KAKAOTALK=true` 로 넘겨야 합니다. 연결된 ARM64 실기기가 없거나 여러 대라서 중단되어도 `status=blocked`, `blocker_reason`, 기기 분류 카운트가 요약 파일에 남습니다.

실기기 없이 수동 증거 필드 형식만 확인하려면 다음을 실행합니다.

```bash
scripts/verify-real-device-e2e.sh --print-manual-template
```

실기기 없이 앱 로그 자동 감지 로직만 검증하려면 다음을 실행합니다.

```bash
scripts/verify-real-device-e2e.sh --self-test-log-detection
```

실제 카카오톡 수신/전송까지 확인했다면 실행 시점에 증거 값을 함께 넘깁니다.

```bash
MANUAL_KAKAO_NOTIFICATION_ACCESS_CONFIRMED=true \
MANUAL_KAKAO_TEST_ROOM="테스트방" \
MANUAL_KAKAO_TEST_SENDER="보조계정" \
MANUAL_KAKAO_IN_LOG_CONFIRMED=true \
MANUAL_KAKAO_OUT_LOG_CONFIRMED=true \
MANUAL_KAKAO_REPLY_VISIBLE_IN_KAKAOTALK=true \
MANUAL_KAKAO_EVIDENCE_NOTE="IN/OUT 로그와 카카오톡 대화창 답장 표시 확인" \
scripts/verify-real-device-e2e.sh
```

위 값이 모두 충족되고 `MANUAL_KAKAO_REMOTEINPUT_FAILURE_REASON` 이 비어 있으면 요약 파일의 `manual_kakao_complete=true` 와 `manual_kakao_pending_reason=none` 으로 기록되고 최종 `status=complete` 가 됩니다. `MANUAL_KAKAO_TEST_ROOM` 과 `MANUAL_KAKAO_TEST_SENDER` 를 넘긴 상태에서 앱 내부 로그에 해당 방의 `IN`/`OUT` 또는 `OUT_FAIL` 이 있으면 `MANUAL_KAKAO_IN_LOG_CONFIRMED` 와 `MANUAL_KAKAO_OUT_LOG_CONFIRMED` 를 직접 넘기지 않아도 자동 증거로 인정합니다. 모델 다운로드/해시/계측 테스트만 통과했지만 실제 카카오톡 수신/전송 증거가 빠졌거나 RemoteInput 실패 reason 이 남아 있으면 `status=manual_kakao_pending` 으로 남고, `manual_kakao_pending_reason` 에 `remoteinput_failed`, `notification_access_unconfirmed`, `missing_test_room`, `missing_test_sender`, `in_log_unconfirmed`, `out_log_unconfirmed`, `reply_not_visible` 중 하나가 기록됩니다. `notification_listener_enabled=true` 는 기기 설정의 알림 접근 권한 상태를 자동으로 읽은 값이고, `manual_kakao_notification_access_confirmed=true` 는 사람이 실제 카카오톡 수신/전송 검증 흐름에서 권한 상태를 확인했다는 별도 증거입니다.

UI 흐름을 바꿨다면 아래도 같이 확인합니다.

```bash
maestro test .maestro
```

## 단위 테스트 범위

- JSON 직렬화/역직렬화
- 방 이력 파싱
- 로컬 LLM 프롬프트 구성과 응답 정규화
- 말투 프로필 우선순위, CSV/방 이력 기반 말투 추출, 방별 말투 판별
- 학습 말투 UI가 전역 내 말투와 방별 말투 모두에서 신뢰도 라벨, 0-100 점수, 샘플 수, 낮은 신뢰도 보조 힌트와 reset 안내를 표시하고, 수동 수정값이 있을 때만 수정 초기화를 활성화하는 가드
- 레거시 OpenAI/로컬 공급자 설정의 로컬 Gemma 정규화
- 로컬 검색/트리거 판단 가드 로직
- `AI가 판단` 낮은 신호 메시지가 모델 로드 없이 스킵되는 가드 로직
- 같은 방/발화자/메시지 반복 알림의 짧은 TTL 중복 방지
- 와일드카드 방 패턴의 정규식 특수문자 escape
- 모델 파일 크기/SHA-256 검증과 `.part` 다운로드 후 교체
- 손상된 방 상태 JSON 파싱 실패 시 빈 목록으로 복구하는 가드
- 트리거 섹션이 없거나 빈 값인 레거시 설정이 모든 메시지 모드로 승격되지 않는 JSON 파싱 가드
- 알림 수집과 답장 시도 분리 가드 로직
- OFF 상태 또는 방별 답장 비활성화일 때도 메시지 수집은 유지되고 답장만 막히는 가드 로직
- 전송 실패와 스킵 reason 이 `no session`, `no remoteInput`, `pendingIntent null`, `pendingIntent send failed`, `global reply off`, `room reply off`, `no room config`, `low signal`, `model not loaded`, `ai quality rejected`, `ai generation exception`, `canned reply empty` 같은 표준 카테고리로 집계되고 최근 실패/스킵 reason, 실패/스킵별 다음 확인 지점, 마지막 전송/실패/스킵 이벤트 시각이 남는 통계 가드
- 후보 응답 source 별 생성/선택/빈 응답/저품질/latency 집계가 대화 원문 없이 계산되는 가드
- source 별 후보 통계 보정점이 가까운 후보의 tie-breaker 로만 작동하고 명백히 나쁜 답변을 이기지 못하는 선택 가드
- 여러 candidate lane 이 같은 답장을 반복하면 중복 후보가 `duplicate_reply` 로 감점되어 선택을 왜곡하지 않는 가드
- primary/style_rewrite/human_style/compact 후보 lane 이 순차 실행으로 퇴행하지 않고 병렬 예약/오케스트레이션되는지 확인하는 가드
- primary/style_rewrite/human_style/compact/emergency 후보 중 AI 메타 문구, 챗봇식 상투어, 자기 지칭 업무체, 친구방 단독 업무체 ACK, 프롬프트 반복, 과한 추측을 피하고 방 말투/근거에 맞는 답장을 고르는 품질 게이트
- 친구방, 팀방, 학교방, 친구방 짧은 echo 방지, 친구방 서비스봇 사과/안내 방지, 근거 있는 질문의 generic ack 방지, 학교방 장황한 부연 방지, 낮은 신호, 맥락 있는 낮은 신호의 과잉 답장 방지, 모르는 사실, 방 메모리 사실 확인, 수동 예시 우선순위 충돌을 포함한 기본 응답 품질 시나리오
- 애매한 지시에는 아는 척하지 않고 방 말투에 맞춰 반말/존댓말 확인 질문을 하는 응답 품질 시나리오
- 기본 응답 품질 시나리오의 Markdown 리포트 생성과 비-LLM 엔진 baseline 경로 검증
- 로그 복사와 답장 통계 reason 저장 시 방 이름, 발화자, 메시지 본문, 느슨한 카톡식 줄, `room=`/`room:` 같은 key/value 디버그 줄, JSON 디버그 필드, 비정형 줄의 전화번호/이메일/URL을 가리는 개인정보 보호 처리
- 로컬 로그 파일이 최근 100줄과 7일 보관 기간을 넘지 않도록 정리되는 가드
- 학습된 말투의 샘플 수 기반 신뢰도 계산
- 기기에 LiteRT-LM 모델이 있을 때의 실제 로컬 LLM 생성 계측 테스트
- 실제 대화 맥락을 넣었을 때의 로컬 LLM 문맥 응답 계측 테스트

새 기능이 순수 함수로 분리 가능하면 가능한 한 JVM 테스트를 먼저 추가합니다.

## Maestro 범위

- 메인 화면 기본 진입
- 응답 설정 진입과 저장
- 대상 방 추가와 방 메모리 편집
- 내 말투 예시, 학습된 말투 스위치, 방별 수동/학습 말투 입력 노출
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

## 최종 검증 체크리스트

아래 항목은 "코드상 구현 완료" 와 "실사용 가능" 을 분리해서 판단하기 위한 체크리스트입니다. 한 항목이라도 빠지면 해당 범위는 완료가 아니라 미검증으로 기록합니다.

### 코드 / 회귀 테스트

- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` 통과
- [ ] `git diff --check` 통과
- [ ] `scripts/verify-real-device-e2e.sh` 가 ARM64 실기기에서 `status=complete` 로 끝남
- [ ] 실기기 조건 미충족 시 `outputs/real-device-e2e/*-summary.txt` 에 `status=blocked`, `blocker_reason`, `real_device_count`, `emulator_device_count`, `non_arm64_device_count` 가 남음
- [ ] 트리거 기본값이 빈 값이나 레거시 설정에서 `모든 메시지` 로 잘못 승격되지 않음
- [ ] `AI가 판단`, `모든 메시지`, `호출어/멘션만`, `질문/명령만` 모드가 각각 의도대로 동작함
- [ ] `AI가 판단` 모드에서 낮은 신호 메시지는 모델 파일이 없어도 실패가 아니라 skip으로 기록됨
- [ ] `AI가 판단` 모드에서도 최근 대화 맥락이 있는 낮은 신호 메시지는 무조건 skip하지 않고 실기기 LLM 검증 대상으로 남음
- [ ] 전역 AI 답장 OFF, 방별 답장 OFF, 응답 설정 없는 방은 각각 다른 skip 원인으로 기록됨
- [ ] 같은 방/발화자/메시지 반복 알림은 짧은 TTL 안에서 중복 답장하지 않음
- [ ] 전역 OFF 또는 방별 답장 OFF 상태에서도 메시지 저장과 세션 갱신은 유지되고 답장만 중단됨
- [ ] 보내기 전 승인 UI 없이 자동 전송 경로가 유지됨
- [ ] 외부 로그 전송, webhook/n8n 의존, JS 스크립팅 봇 엔진 회귀가 없음

### 말투 / 페르소나 품질

- [ ] 사용자 직접 입력 예시가 가장 높은 우선순위로 프롬프트에 들어감
- [ ] 방별 수동 말투가 학습된 사용자/방 말투보다 우선함
- [ ] 학습된 내 말투와 학습된 방 말투를 각각 켜기/끄기/수정/초기화할 수 있음
- [ ] 잘못 배운 방 말투를 다시 쌓을 수 있도록 방별 학습 원본 대화를 삭제할 수 있음
- [ ] 학습 말투 신뢰도 낮음은 프롬프트와 전역/방별 UI에서 확정 규칙이 아니라 보조 힌트로 처리되고, 신뢰도 점수, 샘플 수, 학습 원본 삭제 안내가 함께 들어감
- [ ] `수정 초기화` 는 수동 수정값이 있을 때만 활성화되고, 자동 추출값 재학습은 `학습 원본 삭제` 경로로 구분됨
- [ ] 친한 친구방 예시에서는 가벼운 반말 응답이 나옴
- [ ] 친구방 짧은 메시지를 그대로 따라 쓰는 후보는 `short_prompt_echo` 로 감점되고 사용자 예시에 가까운 답장이 선택됨
- [ ] 방 메모/최근 대화에 답이 있는 질문을 `네 확인했습니다`처럼 넘기는 후보는 `generic_ack_instead_of_known_fact` 로 감점되고 실제 근거 답변이 선택됨
- [ ] 짧게 답할 수 있는 학교/팀방 메시지에 도우미식 부연을 붙이는 후보는 `over_explained` 로 감점됨
- [ ] `ㅇㅋ`, `ㅋㅋ` 같은 낮은 신호 메시지에 맥락보다 긴 부연을 붙이는 후보는 `low_signal_overreply` 로 감점됨
- [ ] 팀 단톡 예시에서는 `별일없습니다!` 같은 단정한 존댓말 응답이 나옴
- [ ] 학교 단톡 예시에서는 `별일 없습니다.` 같은 단정한 존댓말 응답이 나옴
- [ ] `app/build/reports/reply-quality/report.md` 에 고정 시나리오별 score/min/pass 결과가 생성됨
- [ ] `app/build/reports/reply-quality/report.md` 의 `Engine Baseline Without LLM` 섹션에 `pre_model_skip`, `ambiguous_clarify`, `unknown_guard`, `deadline_fact` 경로가 covered/pass 로 기록됨
- [ ] 수동 방 말투와 반대되는 후보는 최종 선택에서 감점됨
- [ ] 애매한 말에는 확인 질문을 하고, 임의로 완료/불가를 단정하지 않음
- [ ] 애매한 말의 확인 질문 후보는 친구방/학교방 말투에 맞게 반말/존댓말을 구분함
- [ ] 방 메모리, 최근 대화, CSV 이력이 답장 근거로 함께 들어감
- [ ] 모르는 사실은 추측하지 않고 모른다고 짧게 답함
- [ ] AI 메타 문구, 챗봇식 상투어, 자기 지칭 업무체, 프롬프트 반복, 지나치게 긴 후보가 최종 답장으로 선택되지 않음
- [ ] `제가 확인해보겠습니다` 같은 자기 지칭 업무체 후보는 `self_referential_business_tone` 으로 감점됨
- [ ] 사용자 직접 예시와 가까운 답장은 `manual_example_match` 로 보상됨
- [ ] 존댓말 이력이 쌓인 방에서도 수동 친구방 예시가 있으면 수동 예시에 가까운 답장만 `manual_example_override` 시나리오를 통과함
- [ ] 친구방에서 `확인했습니다!` 같은 단독 업무체 ACK는 `generic_business_ack_in_casual_room` 으로 감점되고 직접 예시 답장이 우선됨
- [ ] 친구방에서 `죄송합니다`, `안내드리겠습니다`, `도움이 되셨길` 같은 서비스봇 문구는 `service_apology_boilerplate` 로 감점되고 직접 예시 답장이 우선됨
- [ ] primary/style_rewrite/human_style/compact candidate lane 이 같은 품질 게이트에서 비교되고, emergency lane 은 초기 후보가 약할 때만 추가됨
- [ ] primary/style_rewrite/human_style/compact candidate lane 은 코루틴에서 병렬 예약되어 순차 후보 생성으로 퇴행하지 않음
- [ ] primary/style_rewrite/human_style/compact/emergency 후보 평가 결과와 선택 이유가 로그로 남음
- [ ] 후보 source 별 생성/선택/빈 응답/저품질/latency 집계가 남고, 프롬프트/원문 응답/방 이름/발화자는 장기 저장하지 않음
- [ ] 후보 source 통계 보정점은 샘플이 충분할 때만 적용되고, 기본 품질 점수를 압도하지 않음
- [ ] 같은 답장을 반복한 후보는 `duplicate_reply` 로 감점되어 source prior 만으로 중복 후보가 최종 선택되지 않음
- [ ] 규칙 기반 후보와 Gemma 후보가 같은 품질 게이트에서 평가됨

### 에뮬레이터 검증

- [ ] `connectedDebugAndroidTest` 통과, 단 LiteRT-LM 생성 테스트 skip은 실기기 필요로 기록
- [ ] Maestro 메인/응답 설정/대상 방/방 메모리 흐름 통과
- [ ] 앱 실제 런처가 `com.example.kakaotalkautobot/.MainActivity` 로 확인됨
- [ ] 앱 UI 녹화 또는 스크린샷이 Android 설정 화면이나 다른 앱 화면과 섞이지 않음
- [ ] 빠른 맥락 답장 경로가 실제 문장을 반환함
- [ ] crash buffer에 앱 native/java crash가 없음
- [ ] 에뮬레이터에서 LiteRT-LM native 실행을 완료 기준으로 삼지 않음

### 실기기 Gemma 4 검증

- [ ] `adb devices -l` 에서 ARM64 Android 실기기 연결 확인
- [ ] `outputs/real-device-e2e/*-summary.txt` 에 기기 브랜드/모델/Android 버전이 기록됨
- [ ] `outputs/real-device-e2e/*-summary.txt` 에 `instrumentation_status=0` 과 `instrumentation_class=com.example.kakaotalkautobot.ExampleInstrumentedTest` 가 기록됨
- [ ] 앱 내 Gemma 4 E2B LiteRT-LM 다운로드 완료
- [ ] 모델 파일 크기가 기본 모델 기대치와 일치함
- [ ] 모델 파일 SHA-256 검증 또는 검증 완료 sidecar가 확인됨
- [ ] 모델 readiness UI가 준비됨으로 표시됨
- [ ] `LlmEngine.loadModel()` 성공 로그 확인
- [ ] `outputs/real-device-e2e/*-summary.txt` 에 `model_load_log_detected=true` 로 기록됨
- [ ] `outputs/real-device-e2e/*-summary.txt` 에 `model_generation_log_detected=true` 와 1개 이상의 `model_generation_reply_log_count` 가 기록됨
- [ ] 짧은 인사 프롬프트가 빈 응답 없이 생성됨
- [ ] 방 메모리의 구체 사실을 물었을 때 그 사실을 포함해 답함
- [ ] 최근 대화의 구체 사실을 물었을 때 그 사실을 포함해 답함
- [ ] 페르소나/말투 설정이 다른 두 방에서 서로 다른 답장 스타일로 반영됨
- [ ] 긴 프롬프트 실패 시 primary, style_rewrite, human_style, compact, emergency 후보 비교가 빈 응답을 줄이는지 확인
- [ ] 긴 프롬프트 실패 후 후보 source 별 빈 응답/저품질/latency 누적 통계가 튜닝 근거로 남는지 확인
- [ ] 생성 시간이 실사용 가능한 범위인지 기록

### 실제 카카오톡 end-to-end

- [ ] 알림 접근 권한 허용 후 `NotificationListener` 연결 상태 확인
- [ ] `outputs/real-device-e2e/*-summary.txt` 의 `notification_listener_enabled=true` 확인
- [ ] `MANUAL_KAKAO_TEST_ROOM` 과 `MANUAL_KAKAO_TEST_SENDER` 가 있으면 앱 내부 로그에서 `manual_kakao_auto_in_log_detected`, `manual_kakao_auto_out_log_detected`, `manual_kakao_remoteinput_failure_reason` 이 자동 감지됨
- [ ] 카카오톡 메시지 수신 시 방 이름, 발화자, 메시지가 로컬에 저장됨
- [ ] 대상 방으로 설정하지 않은 방에는 답장하지 않음
- [ ] 대상 방에서는 조건을 만족할 때 자동 답장이 전송됨
- [ ] 직접 채팅과 단톡방에서 각각 발화자/방 조건이 다르게 적용됨
- [ ] `AI가 판단` 모드에서 낮은 신호 메시지에는 불필요하게 끼어들지 않음
- [ ] `모든 메시지` 모드에서는 조건을 통과한 모든 수신 메시지에 답장함
- [ ] PendingIntent RemoteInput 전송 성공 로그와 실제 카카오톡 대화창 답장 표시가 일치함
- [ ] 전송 실패 시 `OUT_FAIL` 로그에 `no session`, `no remoteInput`, `pendingIntent null`, `pendingIntent send failed` 같은 원인이 남고 앱이 죽지 않음
- [ ] 전송 실패와 스킵 통계 요약에 전송 성공률, 상위 원인별 횟수, 최근 실패/스킵 reason, 최근 또는 최다 실패/스킵 원인에 대한 `확인 필요` 힌트, 마지막 전송/실패/스킵 이벤트 시각이 표준 카테고리로 표시됨
- [ ] AI 생성 실패, AI 품질 게이트 실패, 고정 답장 설정 실패가 각각 `ai generation exception`, `ai quality rejected`, `canned reply empty` 로 집계됨
- [ ] 실패가 없고 스킵만 있어도 `global reply off`, `room reply off`, `low signal`, `condition not met` 같은 스킵 reason 의 다음 확인 지점이 통계 상세에 표시됨
- [ ] AI 답장 생성 후 `replyToRoomDetailed` 이 실패하면 엔진 레벨에서도 같은 전송 원인을 포함한 `OUT_FAIL` 이 남음
- [ ] `outputs/real-device-e2e/*-summary.txt` 의 `manual_kakao_in_log_confirmed`, `manual_kakao_out_log_confirmed`, `manual_kakao_reply_visible_in_kakaotalk`, `manual_kakao_complete` 가 실제 확인 결과로 채워짐
- [ ] `manual_kakao_remoteinput_failure_reason` 이 비어 있지 않으면 다른 수동 증거가 true 여도 `manual_kakao_complete=false` 로 남음
- [ ] `manual_kakao_complete=false` 일 때 `manual_kakao_pending_reason` 이 빠진 증거 또는 RemoteInput 실패 원인을 표준 값으로 남김
- [ ] 모델 검증만 통과하고 수동 카카오톡 증거가 비어 있으면 최종 상태가 `manual_kakao_pending` 으로 남음
- [ ] OFF 상태 전환 직후 수신 메시지는 저장되지만 답장은 나가지 않음
- [ ] OFF 상태 전환 직후 수신 메시지는 `OUT_SKIP` 에 OFF 원인이 남음

### 안정성 / 개인정보

- [ ] 모델 다운로드 실패, 저장공간 부족, 네트워크 중단 시 사용자에게 원인이 표시됨
- [ ] 앱 재시작 후 설정, 방 메모리, 학습 말투, 대상 방 목록이 유지됨
- [ ] 로그 복사 기능에 외부 전송이 포함되지 않음
- [ ] 로그 복사 기본값은 방 이름, 발화자, 메시지 본문과 전화번호/이메일/URL을 가리고 전송 실패 reason 만 유지함
- [ ] 표준 카테고리로 매칭되지 않은 실패/스킵 reason 은 통계 저장 전에 전화번호/이메일/URL을 마스킹함
- [ ] 정형 prefix 가 깨진 `[방] 발화자: 메시지` 줄, `room=`, `sender=`, `msg=` 디버그 줄, `"room"`, `"sender"`, `"message"` JSON 디버그 필드도 복사본에서 가려짐
- [ ] 원본 로그 복사를 켜면 개인정보 포함 가능성 경고 후에만 복사됨
- [ ] 로컬 로그는 최근 100줄과 7일 보관 기간을 기준으로 정리됨
- [ ] 메인 화면에서 로컬 로그를 즉시 삭제할 수 있음
- [ ] 로컬 저장 파일에 불필요한 API 키나 외부 서비스 토큰이 저장되지 않음
- [ ] 릴리즈 APK 설치 후에도 debug 전용 동작이나 테스트 모델 의존이 남지 않음

## 최근 로컬 수동 계측 결과

회귀 테스트와 계측 테스트는 작은 테스트 모델(`Qwen3-0.6B.litertlm`) 기준으로 수행합니다.

기본 프로덕션 로컬 모델은 `Gemma-4-E2B-it-LiteRT-LM` 이지만, 테스트에서는 다운로드 시간/저장공간 때문에 더 작은 모델을 사용합니다.

프로덕션 기본 모델(`Gemma-4-E2B-it-LiteRT-LM`) 검증은 자동 계측 테스트가 아니라 수동 검증으로 수행합니다.

현재 Gemma 4 LiteRT-LM 경로는 **실기기 ARM64 Android**에서만 최종 검증 대상으로 취급하고, 에뮬레이터는 지원하지 않습니다.

`connectedDebugAndroidTest` 를 에뮬레이터에서 실행할 때는 LiteRT-LM 모델 생성 테스트를 JUnit assumption으로 skip합니다. 에뮬레이터에서는 앱 컨텍스트, 세션 저장, 빠른 컨텍스트/마감 단축 경로처럼 모델 런타임이 필요 없는 계측 테스트를 확인하고, 실제 모델 다운로드/로드/생성 품질 검증은 실기기 ARM64에서만 완료 기준으로 봅니다.

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
- 현재는 기본 컨텍스트 예산을 키우고, primary/style_rewrite/human_style/compact 후보를 같은 품질 게이트에서 비교합니다. human_style 후보는 사용자 직접 예시와 수동 방 말투를 더 강하게 따라 실제 사용자처럼 보낼 한 문장을 만들기 위한 경로입니다.
- 초기 후보가 모두 약하면 더 작은 예산의 **emergency prompt** 로 한 번 더 재시도하되, 최소 페르소나/방 메모/최근 대화는 유지합니다.
- 이 완화는 "모델 호출 실패가 그대로 빈 응답으로 보이는" 경로를 줄이기 위한 안정성 보강입니다.

## 최근 빠른 단축 경로 검증

- 일정 미확정 질문 `아녕하세요 프로젝트 언제까지 되나요?`
  - 출력: `아직 일정이 확정된 건 못 찾았어. 정리되면 바로 공유할게.`
  - 에뮬레이터 계측: 약 `23ms`
  - 경로: 로컬 LLM 생성 대신 빠른 deadline shortcut

해석:

- 현재 기본 로컬 경로는 **최근 사실 확인 질문**과 **일정 미확정 확인 질문**에 대해 근거가 분명할 때만 모델 생성 없이 빠르게 응답합니다.
- 생활형 small-talk 은 grounding 우회를 줄이기 위해 빠른 단축 경로에서 제거했고, 대신 로컬 프롬프트가 페르소나/방 메모를 보고 답하도록 유지합니다.

## 최근 말투 프로필 회귀 보강

- `AutoReplyJsonTest` 는 방별 `roomStyle` 직렬화/역직렬화 유지를 확인합니다.
- `StyleProfileStoreTest` 는 사용자 직접 예시가 수동 방 말투와 학습된 말투보다 앞서는지, CSV/방 이력에서 말투가 추출되는지 확인합니다.
- `AiProviderClientTest` 는 스타일 지침이 페르소나/방 메모보다 먼저 프롬프트에 들어가는지 확인합니다.
- Maestro 설정/방 관리 흐름은 새 입력 항목이 화면에 노출되는지 확인합니다.

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

`scripts/verify-real-device-e2e.sh` 는 위 1-4번 중 모델 다운로드/해시/로드/생성까지 자동 검증합니다. 실제 카카오톡 메시지 수신과 RemoteInput 자동 전송은 다른 카카오톡 계정에서 메시지를 보내고 앱 로그의 `IN`/`OUT` 과 카카오톡 대화창 표시가 일치하는지 별도로 확인해야 합니다.
