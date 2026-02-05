package com.example.chatbotchichi

import android.content.Context
import java.io.File

object BotManager {
    private const val PREFS_NAME = "BotPrefs"
    private const val DIR_NAME = "bots"

    fun getBots(context: Context): List<BotInfo> {
        try {
            val botDir = File(context.filesDir, DIR_NAME)
            if (!botDir.exists()) botDir.mkdirs()

            // 샘플 봇 생성 (없을 경우 상세한 예제로 생성)
            val sampleFile = File(botDir, "sample.js")
            if (!sampleFile.exists()) {
                sampleFile.writeText(
                    """
                    /**
                     * 챗봇 메인 로직 스크립트
                     */
                    function responseFix(room, msg, sender, isGroupChat, replier, imageDB, packageName) {
                        // 안전한 문자열 변환
                        msg = String(msg);
                        sender = String(sender);
                        room = String(room);

                        // 1. 트레이딩 봇 (indexOf 사용)
                        if (room == "트레이딩-봇") {
                            if (msg.indexOf("매수") !== -1) {
                                replier.reply("✅ 매수 주문 실행 중...");
                                var data = { "symbol": extractSymbol(msg), "type": "buy" };
                                replier.executeWorkflow("buy_order", data);
                            }
                        }
                    
                        // 2. 모니터링 봇
                        if (room == "서버-모니터링") {
                            if (msg.indexOf("CPU") !== -1 || msg.indexOf("메모리") !== -1) {
                                replier.reply("📊 데이터 수신했습니다: " + formatDateTime());
                            }
                        }
                    
                        // 3. 기본 에코 (테스트방)
                        if (room == "테스트") {
                            replier.reply("봇이 받았습니다: " + msg);
                        }
                        
                        // 4. 전체 테스트 명령어
                        if (msg.trim() == "/test") {
                            replier.reply("봇 작동 중! " + formatDateTime());
                        }
                        
                        if (msg.trim() == "/hello") {
                            replier.reply("안녕하세요, " + sender + "님! " + room + "에서 대화 중이군요.");
                        }
                    }
                    
                    // === 유틸리티 함수 ===
                    
                    function extractSymbol(text) {
                        if (text.indexOf("삼성전자") !== -1) return "005930";
                        if (text.indexOf("비트코인") !== -1) return "BTC";
                        return "UNKNOWN";
                    }
                    """.trimIndent()
                )
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val files = botDir.listFiles { _, name -> name.endsWith(".js") } ?: return emptyList()

            return files.map { file ->
                val name = file.name.removeSuffix(".js")
                val isEnabled = prefs.getBoolean("bot_$name", true)
                BotInfo(name, file.absolutePath, isEnabled)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun saveBot(context: Context, name: String, code: String) {
        val botDir = File(context.filesDir, DIR_NAME)
        if (!botDir.exists()) botDir.mkdirs()
        
        val file = File(botDir, "$name.js")
        file.writeText(code)
    }

    fun deleteBot(context: Context, name: String) {
        val botDir = File(context.filesDir, DIR_NAME)
        val file = File(botDir, "$name.js")
        if (file.exists()) file.delete()
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("bot_$name").apply()
    }

    fun setBotEnabled(context: Context, name: String, isEnabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bot_$name", isEnabled).apply()
    }

    fun getBotCode(context: Context, name: String): String {
        val botDir = File(context.filesDir, DIR_NAME)
        val file = File(botDir, "$name.js")
        return if (file.exists()) file.readText() else ""
    }
}
