# CODEX.md

이 저장소에서 Codex 계열 에이전트는 아래 기준만 빠르게 확인하면 됩니다.

## 작업 범위

- 요청된 범위만 수정합니다.
- 코드, 문서, CI 정책이 바뀌면 관련 `docs/` 문서를 함께 갱신합니다.
- 큰 변경은 나누고, 한 커밋에는 한 목적만 담습니다.

## 제품 기준

- 현재 기본 AI 경로는 **로컬 LiteRT-LM 답장 엔진**입니다.
- Gemma 4 LiteRT-LM 검증은 실기기 ARM64 Android를 기준으로 하며, 에뮬레이터는 지원하지 않습니다.
- 저장된 레거시 OpenAI 설정이 있더라도 런타임에서는 **로컬 Gemma 경로만** 사용합니다.
- OFF 상태에서도 메시지 저장은 유지되고, 답장만 중단되어야 합니다.
- 답장은 모든 메시지에 보내지 않고 방 설정, 발화자 조건, 트리거, AI 판단을 따릅니다.

## 문서 기준

- README는 한국어 사용자 문서로 유지합니다.
- 구현, 운영, 정책 설명은 `docs/` 아래에서 관리합니다.
- UI/플로우 변경 시 `docs/user-flow.md` 를 같이 고칩니다.
- 저장 구조나 상태 변경 시 `docs/storage-and-memory.md` 를 같이 고칩니다.
- AI 경로나 정책 변경 시 `docs/providers.md`, `docs/local-ai-migration-status.md` 를 같이 고칩니다.
- 릴리즈/CI 변경 시 `docs/ci-release.md`, `docs/testing-quality.md`, `docs/release-distribution-status.md` 를 같이 고칩니다.

## 릴리즈 기준

- 태그 릴리즈는 signed release APK 게시 경로를 기준으로 합니다.
- GitHub Release에는 최종 사용자용 signed APK 1개만 안내합니다.
- debug/unsigned APK를 일반 사용자 설치 경로로 문서화하지 않습니다.

## 금지

- JS 스크립팅 봇 엔진 회귀
- n8n/webhook 의존 로직 추가
- 외부 로그 전송 복구
