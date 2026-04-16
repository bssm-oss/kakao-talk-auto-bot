package com.example.kakaotalkautobot

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.util.Log
import kotlinx.coroutines.runBlocking

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private val tag = "ExampleInstrumentedTest"

    private fun ensureDefaultModelReady(appContext: android.content.Context) {
        if (LlmModelManager.hasModel(appContext, LlmModelManager.DEFAULT_MODEL)) return
        val download = runBlocking {
            LlmModelManager.downloadModel(appContext, LlmModelManager.DEFAULT_MODEL)
        }
        assertTrue("Gemma 4 model should download successfully for device test", download.isSuccess)
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.kakaotalkautobot", appContext.packageName)
    }

    @Test
    fun testLocalLlmGeneration() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        if (!LlmModelManager.hasModel(appContext, LlmModelManager.TEST_MODEL)) {
            val download = runBlocking {
                LlmModelManager.downloadModel(appContext, LlmModelManager.TEST_MODEL)
            }
            assertTrue("Default model should download successfully for device generation test", download.isSuccess)
        }

        val loaded = LlmEngine.loadModel(
            appContext,
            LlmEngine.LlmConfig(
                maxTokens = 16,
                temperature = 0.2f,
                topP = 0.9f,
                topK = 20,
                contextSize = 2048
            ),
            source = LlmModelManager.TEST_MODEL
        )

        assertTrue("Model should load when present", loaded)

        val prompt = buildString {
            append("<|im_start|>system\n")
            append("너는 짧고 자연스럽게 한국어로 답하는 도우미다.\n")
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append("한 문장으로 짧게 인사해 줘.\n")
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }

        val startedAt = System.currentTimeMillis()
        val reply = try {
            LlmEngine.generate(prompt, maxTokens = 16)
        } finally {
            LlmEngine.free()
        }
        val elapsedMs = System.currentTimeMillis() - startedAt

        Log.i(tag, "LLM_PROMPT=한 문장으로 짧게 인사해 줘.")
        Log.i(tag, "LLM_REPLY=$reply")
        Log.i(tag, "LLM_ELAPSED_MS=$elapsedMs")

        assertTrue("Generated reply should not be blank", reply.isNotBlank())
        assertTrue("Generated reply should include Korean text", reply.any { it in '\uAC00'..'\uD7A3' })
    }

    @Test
    fun testLocalLlmHandlesDialogueContextPrompt() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val config = AutoReplyJson.defaultConfig("테스트방")
        val history = listOf(
            RoomHistoryMessage("철수", "오늘 회의 몇 시야?", true, 1L),
            RoomHistoryMessage("나", "오늘 회의는 3시에 시작해.", false, 2L)
        )

        val startedAt = System.currentTimeMillis()
        val result = AiProviderClient.generate(
            context = appContext,
            config = config,
            room = "테스트방",
            sender = "영희",
            message = "회의 몇 시에 시작이야?",
            history = history
        )
        val elapsedMs = System.currentTimeMillis() - startedAt
        val reply = result.reply.orEmpty()

        Log.i(tag, "LLM_CONTEXT_PROMPT=회의 몇 시에 시작이야?")
        Log.i(tag, "LLM_CONTEXT_REPLY=$reply")
        Log.i(tag, "LLM_CONTEXT_ELAPSED_MS=$elapsedMs")

        assertTrue("Context reply should not be blank", reply.isNotBlank())
        assertTrue("Context reply should mention the 3시 fact", reply.contains("3"))
        assertTrue("Context shortcut should finish quickly", elapsedMs < 1000)
    }

    @Test
    fun testLocalLlmDoesNotReturnEmptyForShortReply() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val config = AutoReplyJson.defaultConfig("도훈").copy(
            persona = "너는 카카오톡 자동응답 도우미다. 짧고 자연스럽게 답해.",
            roomMemory = "자동 메모리 요약\n- 참여자 성향: 도훈(질문이 많음)"
        )
        val history = listOf(
            RoomHistoryMessage("도훈", "오늘 좀 피곤하다", true, 1L),
            RoomHistoryMessage("나", "오늘도 고생했어", false, 2L),
            RoomHistoryMessage("도훈", "잘자", true, 3L)
        )

        if (!LlmModelManager.hasModel(appContext, LlmModelManager.TEST_MODEL)) {
            val download = runBlocking {
                LlmModelManager.downloadModel(appContext, LlmModelManager.TEST_MODEL)
            }
            assertTrue("Default model should download successfully for short reply test", download.isSuccess)
        }

        val startedAt = System.currentTimeMillis()
        val result = AiProviderClient.generate(
            context = appContext,
            config = config,
            room = "도훈",
            sender = "도훈",
            message = "잘자",
            history = history
        )
        val elapsedMs = System.currentTimeMillis() - startedAt

        Log.i(tag, "LLM_SHORT_PROMPT=잘자")
        Log.i(tag, "LLM_SHORT_REPLY=${result.reply}")
        Log.i(tag, "LLM_SHORT_FAILURE=${result.failureReason}")
        Log.i(tag, "LLM_SHORT_ELAPSED_MS=$elapsedMs")

        assertTrue(
            "Short reply path should not end in empty AI response",
            result.reply?.isNotBlank() == true
        )
    }

    @Test
    fun testLocalLlmProjectDeadlinePrompt() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val config = AutoReplyJson.defaultConfig("프로젝트방").copy(
            persona = "너는 카카오톡 자동응답 도우미다. 짧고 자연스럽게 한국어로만 답해.",
            roomMemory = ""
        )
        val history = emptyList<RoomHistoryMessage>()

        if (!LlmModelManager.hasModel(appContext, LlmModelManager.TEST_MODEL)) {
            val download = runBlocking {
                LlmModelManager.downloadModel(appContext, LlmModelManager.TEST_MODEL)
            }
            assertTrue("Default model should download successfully for deadline prompt test", download.isSuccess)
        }

        val startedAt = System.currentTimeMillis()
        val result = AiProviderClient.generate(
            context = appContext,
            config = config,
            room = "프로젝트방",
            sender = "사용자",
            message = "아녕하세요 프로젝트 언제까지 되나요?",
            history = history
        )
        val elapsedMs = System.currentTimeMillis() - startedAt

        Log.i(tag, "LLM_DEADLINE_PROMPT=아녕하세요 프로젝트 언제까지 되나요?")
        Log.i(tag, "LLM_DEADLINE_REPLY=${result.reply}")
        Log.i(tag, "LLM_DEADLINE_FAILURE=${result.failureReason}")
        Log.i(tag, "LLM_DEADLINE_ELAPSED_MS=$elapsedMs")

        assertTrue(
            "Deadline prompt should produce either a reply or an explicit failure",
            result.reply?.isNotBlank() == true || result.failureReason?.isNotBlank() == true
        )
    }

    @Test
    fun testLocalLlmQuestionPromptDoesNotCollapseToEmptyReply() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val config = AutoReplyJson.defaultConfig("김민재").copy(
            persona = "너는 카카오톡 자동응답 도우미다. 짧고 자연스럽게 답하고, 질문에는 바로 핵심만 답해.",
            roomMemory = "자동 메모리 요약\n- 참여자 성향: 김민재(질문이 많음)\n- 최근 대화는 짧게 이어지고 있다"
        )
        val history = listOf(
            RoomHistoryMessage("김민재", "하이하이", true, 1L),
            RoomHistoryMessage("나", "하이! 😊", false, 2L),
            RoomHistoryMessage("김민재", "너 모델이 뭐야?", true, 3L)
        )

        ensureDefaultModelReady(appContext)

        val startedAt = System.currentTimeMillis()
        val result = AiProviderClient.generate(
            context = appContext,
            config = config,
            room = "김민재",
            sender = "김민재",
            message = "너 모델이 뭐야?",
            history = history
        )
        val elapsedMs = System.currentTimeMillis() - startedAt

        Log.i(tag, "LLM_QUESTION_PROMPT=너 모델이 뭐야?")
        Log.i(tag, "LLM_QUESTION_REPLY=${result.reply}")
        Log.i(tag, "LLM_QUESTION_FAILURE=${result.failureReason}")
        Log.i(tag, "LLM_QUESTION_ELAPSED_MS=$elapsedMs")

        assertTrue(
            "Question prompt should not collapse into an empty AI response",
            result.reply?.isNotBlank() == true
        )
    }

    @Test
    fun testLocalLlmRepeatedQuestionPromptsAvoidEmptyReplies() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val config = AutoReplyJson.defaultConfig("김민재").copy(
            persona = "너는 카카오톡 자동응답 도우미다. 짧고 자연스럽게 답하고, 질문에는 바로 핵심만 답해.",
            roomMemory = "자동 메모리 요약\n- 참여자 성향: 김민재(질문이 많음)\n- 최근 대화는 짧게 이어지고 있다"
        )

        ensureDefaultModelReady(appContext)

        val prompts = listOf("너 모델이 뭐야?", "모델이 뭐야?", "너 이름이 뭐야?", "하이")
        prompts.forEachIndexed { index, prompt ->
            val history = buildList {
                add(RoomHistoryMessage("김민재", "하이하이", true, index.toLong() * 10 + 1))
                add(RoomHistoryMessage("나", "하이! 😊", false, index.toLong() * 10 + 2))
                add(RoomHistoryMessage("김민재", prompt, true, index.toLong() * 10 + 3))
            }

            val result = AiProviderClient.generate(
                context = appContext,
                config = config,
                room = "김민재",
                sender = "김민재",
                message = prompt,
                history = history
            )

            Log.i(tag, "LLM_REPEAT_PROMPT=$prompt")
            Log.i(tag, "LLM_REPEAT_REPLY=${result.reply}")
            Log.i(tag, "LLM_REPEAT_FAILURE=${result.failureReason}")

            assertTrue(
                "Repeated question prompt '$prompt' should not collapse into an empty AI response",
                result.reply?.isNotBlank() == true
            )
        }
    }

    @Test
    fun testLocalLlmUsesRoomMemoryForConcreteFact() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        ensureDefaultModelReady(appContext)
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            persona = "너는 카카오톡 자동응답 도우미다. 짧고 자연스럽게 한국어로만 답해.",
            roomMemory = "방 메모: 이번 모임 장소는 2층 회의실이다. 이 사실을 우선해서 답해라."
        )

        val result = AiProviderClient.generate(
            context = appContext,
            config = config,
            room = "테스트방",
            sender = "민수",
            message = "이번 모임 어디서 해?",
            history = emptyList()
        )

        Log.i(tag, "LLM_ROOM_MEMORY_REPLY=${result.reply}")
        Log.i(tag, "LLM_ROOM_MEMORY_FAILURE=${result.failureReason}")

        assertTrue("Room memory reply should not be blank", result.reply?.isNotBlank() == true)
        assertTrue("Room memory fact should be reflected in reply", result.reply!!.contains("2층") || result.reply!!.contains("회의실"))
    }

    @Test
    fun testLocalLlmUsesRecentHistoryForFollowUpFact() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        ensureDefaultModelReady(appContext)
        val config = AutoReplyJson.defaultConfig("테스트방").copy(
            persona = "너는 카카오톡 자동응답 도우미다. 최근 대화의 사실을 우선해서 짧게 답해.",
            roomMemory = ""
        )
        val history = listOf(
            RoomHistoryMessage("민수", "내일 발표 장소 기억나?", true, 1L),
            RoomHistoryMessage("나", "응, 2층 과학실이야.", false, 2L)
        )

        val result = AiProviderClient.generate(
            context = appContext,
            config = config,
            room = "테스트방",
            sender = "민수",
            message = "그럼 발표 장소 어디야?",
            history = history
        )

        Log.i(tag, "LLM_HISTORY_REPLY=${result.reply}")
        Log.i(tag, "LLM_HISTORY_FAILURE=${result.failureReason}")

        assertTrue("History-grounded reply should not be blank", result.reply?.isNotBlank() == true)
        assertTrue("History fact should be reflected in reply", result.reply!!.contains("2층") || result.reply!!.contains("과학실"))
    }

    @Test
    fun testLocalLlmPersonaChangesReplyStyle() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        ensureDefaultModelReady(appContext)
        val politeConfig = AutoReplyJson.defaultConfig("말투방").copy(
            persona = "항상 존댓말로 답하고 문장 끝을 요로 마무리해.",
            roomMemory = ""
        )
        val bluntConfig = AutoReplyJson.defaultConfig("말투방").copy(
            persona = "항상 반말로 짧게 답하고 요를 쓰지 마.",
            roomMemory = ""
        )

        val polite = AiProviderClient.generate(
            context = appContext,
            config = politeConfig,
            room = "말투방",
            sender = "민수",
            message = "알겠어?",
            history = emptyList()
        )
        val blunt = AiProviderClient.generate(
            context = appContext,
            config = bluntConfig,
            room = "말투방",
            sender = "민수",
            message = "알겠어?",
            history = emptyList()
        )

        Log.i(tag, "LLM_PERSONA_POLITE=${polite.reply}")
        Log.i(tag, "LLM_PERSONA_BLUNT=${blunt.reply}")

        assertTrue("Polite persona reply should not be blank", polite.reply?.isNotBlank() == true)
        assertTrue("Blunt persona reply should not be blank", blunt.reply?.isNotBlank() == true)
        assertTrue(
            "Polite persona should use a polite ending",
            polite.reply!!.contains("요") || polite.reply!!.contains("습니다") || polite.reply!!.contains("입니다")
        )
        assertTrue("Blunt persona should avoid polite ending", !blunt.reply!!.contains("요"))
        assertTrue("Blunt persona should differ from the polite reply", polite.reply != blunt.reply)
    }

}
