package com.example.kakaotalkautobot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderClientTest {

    @Test
    fun buildPrompt_includes_persona_and_memory() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            persona = "친절하고 간결한 도우미",
            roomMemory = "이 방은 개발팀 단톡방입니다."
        )

        val history = listOf(
            RoomHistoryMessage("철수", "안녕!", true, System.currentTimeMillis() - 1000)
        )

        val prompt = AiProviderClient.buildPrompt(config, "테스트방", "홍길동", "반갑습니다", history)

        assertTrue(prompt.contains("친절하고 간결한 도우미"))
        assertTrue(prompt.contains("이 방은 개발팀 단톡방입니다."))
        assertTrue(prompt.contains("철수"))
        assertTrue(prompt.contains("안녕!"))
        assertTrue(prompt.contains("홍길동"))
        assertTrue(prompt.contains("반갑습니다"))
    }

    @Test
    fun buildPrompt_places_style_guide_before_persona() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            persona = "친구처럼 답장",
            roomMemory = "오늘 별일 있는지 묻는 방"
        )
        val styleGuide = """
            말투/스타일 지침:
            사용자 직접 예시:
            아무것도 없긴해
            수동 방 스타일:
            친한친구방은 가볍게 반말
        """.trimIndent()

        val prompt = AiProviderClient.buildPrompt(
            config = config,
            room = "친구방",
            sender = "민수",
            message = "오늘 뭐 있어?",
            history = emptyList(),
            styleGuide = styleGuide
        )

        assertTrue(prompt.contains("아무것도 없긴해"))
        assertTrue(prompt.indexOf("사용자 직접 예시") < prompt.indexOf("페르소나:"))
        assertTrue(prompt.indexOf("수동 방 스타일") < prompt.indexOf("방 메모/기억:"))
    }

    @Test
    fun buildPrompt_uses_only_last_eight_history_messages() {
        val config = AutoReplyJson.defaultConfig("테스트방")
        val history = (1..25).map { index ->
            RoomHistoryMessage(
                sender = if (index % 2 == 0) "나" else "상대$index",
                message = "메시지-$index",
                incoming = index % 2 != 0,
                timestamp = index.toLong()
            )
        }

        val prompt = AiProviderClient.buildPrompt(config, "테스트방", "홍길동", "최신 메시지", history)

        assertTrue(!prompt.contains("메시지-1\n"))
        assertTrue(!prompt.contains("메시지-17\n"))
        assertTrue(prompt.contains("메시지-18\n"))
        assertTrue(prompt.contains("메시지-25\n"))
        assertTrue(prompt.contains("[상대방/상대19] 메시지-19"))
        assertTrue(prompt.contains("[나/나] 메시지-20"))
    }

    @Test
    fun defaultConfig_has_llm_provider() {
        val config = AutoReplyJson.defaultConfig("테스트")
        assertEquals("llm", config.provider.type)
        assertEquals("gemma-4-e2b-it-litertlm", config.provider.model)
    }

    @Test
    fun response_cleaning_removes_code_blocks() {
        val rawOutput = "```\n안녕하세요, 확인했습니다.\n```"
        val cleaned = AiProviderClient.cleanResponse(rawOutput)
        assertTrue(cleaned.contains("확인했습니다"))
        assertTrue(!cleaned.contains("```"))
    }

    @Test
    fun response_cleaning_removes_prefix_labels() {
        val rawOutput = "답장: 네, 알겠습니다."
        val cleaned = AiProviderClient.cleanResponse(rawOutput)
        assertEquals("네, 알겠습니다.", cleaned)
    }

    @Test
    fun response_truncation_works_for_long_output() {
        val longText = "A".repeat(300)
        val cleaned = AiProviderClient.cleanResponse(longText)
        assertTrue(cleaned.length <= 150)
    }

    @Test
    fun cleanResponse_removes_wrappers_prefix_and_truncates_to_first_sentence() {
        val firstSentence = "첫 문장은 충분히 길어서 백 글자를 넘기도록 반복합니다 ".repeat(4).trim() + "."
        val longTail = " 두 번째 문장도 길게 이어집니다.".repeat(10)
        val rawOutput = "<|im_start|>assistant\n답장: $firstSentence$longTail"

        val cleaned = AiProviderClient.cleanResponse(rawOutput)

        assertEquals(firstSentence, cleaned)
    }

    @Test
    fun buildCompactPrompt_keeps_persona_room_memory_and_recent_history() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            persona = "짧고 친절하게 답해",
            roomMemory = "이 방은 한국어로만 대화하고 일정은 방 메모를 먼저 본다"
        )
        val history = (1..6).map { index ->
            RoomHistoryMessage(
                sender = if (index % 2 == 0) "나" else "상대$index",
                message = "메시지-$index",
                incoming = index % 2 != 0,
                timestamp = index.toLong()
            )
        }

        val prompt = AiProviderClient.buildCompactPrompt(config, "테스트방", "도훈", "잘자", history)

        assertTrue(prompt.contains("짧고 친절하게 답해"))
        assertTrue(prompt.contains("이 방은 한국어로만 대화하고 일정은 방 메모를 먼저 본다"))
        assertTrue(!prompt.contains("메시지-2"))
        assertTrue(prompt.contains("[상대/상대5] 메시지-5"))
        assertTrue(prompt.contains("[나/나] 메시지-6"))
        assertTrue(!prompt.contains("메시지-1"))
        assertTrue(prompt.contains("방: 테스트방"))
        assertTrue(prompt.contains("[도훈] 잘자"))
    }

    @Test
    fun buildEmergencyPrompt_retains_minimal_grounding() {
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            persona = "차분하게 짧게 답해",
            roomMemory = "이 방에서는 반말보다 존댓말을 우선한다"
        )
        val history = listOf(
            RoomHistoryMessage("민수", "오늘 자료는 저녁에 공유할게", true, 1L),
            RoomHistoryMessage("나", "알겠어요", false, 2L),
            RoomHistoryMessage("민수", "혹시 기억 안 나면 방 메모도 봐줘", true, 3L)
        )

        val prompt = AiProviderClient.buildEmergencyPrompt(config, "테스트방", "도훈", "너 이름이 뭐야?", history)

        assertTrue(prompt.contains("차분하게 짧게 답해"))
        assertTrue(prompt.contains("이 방에서는 반말보다 존댓말을 우선한다"))
        assertTrue(prompt.contains("[나/나] 알겠어요"))
        assertTrue(prompt.contains("[상대/민수] 혹시 기억 안 나면 방 메모도 봐줘"))
        assertTrue(prompt.contains("방: 테스트방"))
        assertTrue(prompt.contains("[도훈] 너 이름이 뭐야?"))
    }

    @Test
    fun buildInitialCandidateSpecs_preparesPrimaryStyleRewriteAndCompactLanes() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            persona = "친구처럼 짧게 답해",
            roomStyle = "친한 친구방. 예시: 아무것도 없긴해",
            roomMemory = "오늘 별일 있는지 묻는 방"
        )
        val history = listOf(
            RoomHistoryMessage("민수", "오늘 뭐 있어?", true, 1L)
        )
        val primaryPrompt = AiProviderClient.buildPrompt(
            config = config,
            room = "친구방",
            sender = "민수",
            message = "오늘 뭐 있어?",
            history = history
        )

        val specs = AiProviderClient.buildInitialCandidateSpecs(
            config = config,
            room = "친구방",
            sender = "민수",
            message = "오늘 뭐 있어?",
            history = history,
            primaryPrompt = primaryPrompt,
            styleGuide = "사용자 직접 예시: 아무것도 없긴해"
        )

        assertEquals(listOf("primary", "style_rewrite", "compact"), specs.map { it.source })
        assertEquals(listOf(12, 18, 24), specs.map { it.maxTokens })
        assertTrue(specs.first().prompt.contains("이전 대화 맥락"))
        assertTrue(specs[1].prompt.contains("내가 보낼 답장"))
        assertTrue(specs[1].prompt.contains("아무것도 없긴해"))
        assertTrue(specs.last().prompt.contains("최근 대화"))
    }

    @Test
    fun buildStyleRewritePrompt_prioritizesManualExamplesAndUserLikeTone() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            persona = "사용자처럼 짧게 답장",
            roomStyle = "친한 친구방. 가볍게 반말. 예시: 아무것도 없긴해",
            roomMemory = "오늘은 별다른 일정이 없음"
        )
        val history = listOf(
            RoomHistoryMessage("민수", "오늘 뭐함", true, 1L),
            RoomHistoryMessage("나", "아무것도 없긴해", false, 2L)
        )

        val prompt = AiProviderClient.buildStyleRewritePrompt(
            config = config,
            room = "친구방",
            sender = "민수",
            message = "오늘 뭐해?",
            history = history,
            styleGuide = "사용자 직접 예시: 아무것도 없긴해"
        )

        assertTrue(prompt.contains("사용자 직접 예시"))
        assertTrue(prompt.contains("수동 방 말투"))
        assertTrue(prompt.contains("아무것도 없긴해"))
        assertTrue(prompt.contains("챗봇처럼 설명하지 말고"))
        assertTrue(prompt.contains("[나/나] 아무것도 없긴해"))
    }

    @Test
    fun buildEmergencyCandidateSpec_usesSeparateFallbackLane() {
        val config = AutoReplyJson.defaultConfig("학교방").copy(
            roomStyle = "학교 단톡. 존댓말로 짧게"
        )

        val spec = AiProviderClient.buildEmergencyCandidateSpec(
            config = config,
            room = "학교방",
            sender = "선생님",
            message = "내일 발표 몇 시야?",
            history = emptyList(),
            styleGuide = "수동 방 스타일: 존댓말"
        )

        assertEquals("emergency", spec.source)
        assertEquals(24, spec.maxTokens)
        assertTrue(spec.prompt.contains("한국어로 짧게 한 문장만 답해라"))
        assertTrue(spec.prompt.contains("[선생님] 내일 발표 몇 시야?"))
    }

    @Test
    fun isLowSignalMessage_detects_meaningless_messages() {
        assertTrue(AiProviderClient.isLowSignalMessage("ㅇㅋ"))
        assertTrue(AiProviderClient.isLowSignalMessage("ㅋㅋ"))
        assertTrue(AiProviderClient.isLowSignalMessage("ㅎㅎㅎ"))
        assertTrue(AiProviderClient.isLowSignalMessage("넵"))
        
        // Meaningful messages should be false
        assertTrue(!AiProviderClient.isLowSignalMessage("이거 어떻게 하는거야?"))
        assertTrue(!AiProviderClient.isLowSignalMessage("내일 점심 같이 먹을래?"))
    }

    @Test
    fun hasRecentContext_works_correctly() {
        val historyWithoutContext = listOf(
            RoomHistoryMessage("A", "ㅋㅋ", true, 0),
            RoomHistoryMessage("B", "ㅇㅇ", true, 0)
        )
        assertTrue(!AiProviderClient.hasRecentContext(historyWithoutContext, 3))

        val historyWithContext = listOf(
            RoomHistoryMessage("A", "오늘 회의 너무 길었어요", true, 0),
            RoomHistoryMessage("B", "ㅇㅇ", true, 0)
        )
        // Check last 2 messages for length > 10
        assertTrue(AiProviderClient.hasRecentContext(historyWithContext, 2))
    }

    @Test
    fun shouldSkipLowSignalBeforeModelLoad_skipsAiJudgeNoiseWithoutContext() {
        val config = AutoReplyJson.defaultConfig("친구방").copy(
            trigger = TriggerConfig(mode = "ai_judge", value = "")
        )

        val shouldSkip = AiProviderClient.shouldSkipLowSignalBeforeModelLoad(
            config = config,
            message = "ㅋㅋ",
            history = emptyList()
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun findFastContextReply_returns_recent_time_fact() {
        val history = listOf(
            RoomHistoryMessage("철수", "오늘 회의 몇 시야?", true, 1L),
            RoomHistoryMessage("나", "오늘 회의는 3시에 시작해.", false, 2L)
        )

        val reply = AiProviderClient.findFastContextReply("회의 몇 시에 시작이야?", history)

        assertEquals("3시에 시작해.", reply)
    }

    @Test
    fun findFastContextReply_returns_null_when_no_matching_fact() {
        val history = listOf(
            RoomHistoryMessage("철수", "오늘 점심 뭐 먹지?", true, 1L),
            RoomHistoryMessage("나", "김밥 어때?", false, 2L)
        )

        val reply = AiProviderClient.findFastContextReply("회의 몇 시에 시작이야?", history)

        assertEquals(null, reply)
    }

    @Test
    fun findFastDeadlineReply_returns_fallback_when_no_deadline_in_history() {
        val reply = AiProviderClient.findFastDeadlineReply("프로젝트 언제까지 되나요?", emptyList())

        assertEquals("아직 일정이 확정된 건 못 찾았어. 정리되면 바로 공유할게.", reply)
    }

    @Test
    fun findFastDeadlineReply_returns_explicit_deadline_from_history() {
        val history = listOf(
            RoomHistoryMessage("PM", "프로젝트는 4월 30일 마감이야", true, 1L)
        )

        val reply = AiProviderClient.findFastDeadlineReply("프로젝트 언제까지 되나요?", history)

        assertEquals("4월 30일까지로 알고 있어.", reply)
    }

    @Test
    fun buildDeterministicCandidates_adds_roomMemoryDeadlineCandidate() {
        val config = AutoReplyJson.defaultConfig("프로젝트방").copy(
            roomMemory = "최종 제출 마감은 6월 12일 18시입니다.",
            roomStyle = "팀 단톡. 존댓말로 짧게 답장"
        )

        val candidates = AiProviderClient.buildDeterministicCandidates(
            config = config,
            message = "최종 제출 언제까지야?",
            history = emptyList()
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)
        assertEquals("deadline_fact", best?.source)
        assertEquals("6월 12일 18시까지입니다.", best?.reply)
        assertTrue(best?.reasons?.contains("grounded_fact") == true)
    }

    @Test
    fun buildDeterministicCandidates_addsAmbiguousClarificationCandidate() {
        val config = AutoReplyJson.defaultConfig("프로젝트방").copy(
            roomStyle = "친한 팀 프로젝트방. 짧게 반말로 확인 질문"
        )
        val history = listOf(
            RoomHistoryMessage("지우", "문서 초안이랑 발표 자료 둘 다 남았어", true, 1L),
            RoomHistoryMessage("나", "일단 문서부터 볼게", false, 2L)
        )

        val candidates = AiProviderClient.buildDeterministicCandidates(
            config = config,
            message = "그거 됐어?",
            history = history
        )

        val best = ReplyQualityEvaluator.selectBest(candidates)
        assertEquals("ambiguous_clarify", best?.source)
        assertEquals("문서 초안 말하는 거야, 발표 자료 말하는 거야?", best?.reply)
    }

    @Test
    fun findAmbiguousClarificationReply_usesFormalToneForSchoolRoom() {
        val config = AutoReplyJson.defaultConfig("학교방").copy(
            roomStyle = "학교 단톡. 존댓말로 짧게 확인"
        )
        val history = listOf(
            RoomHistoryMessage("선생님", "회의 일정이랑 제출 마감 둘 다 확인해 주세요.", true, 1L)
        )

        val reply = AiProviderClient.findAmbiguousClarificationReply(
            config = config,
            message = "그건 어떻게 됐나요?",
            history = history
        )

        assertEquals("회의 일정 말씀하시는 건가요, 제출 마감 말씀하시는 건가요?", reply)
    }

    @Test
    fun findUnknownFactGuardReply_usesFormalToneForSchoolRoom() {
        val config = AutoReplyJson.defaultConfig("학교방").copy(
            roomStyle = "학교 단톡. 존댓말. 모르는 일정은 추측하지 않음"
        )

        val reply = AiProviderClient.findUnknownFactGuardReply(
            config = config,
            message = "내일 발표 몇 시야?",
            history = emptyList()
        )

        assertEquals("아직 확인된 내용은 못 찾았습니다.", reply)
    }

}
