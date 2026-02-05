/**
 * 챗봇 메인 로직 스크립트 (Messenger Bot R 호환)
 * 
 * @param {String} room - 채팅방 이름
 * @param {String} msg - 메시지 내용
 * @param {String} sender - 보낸 사람
 * @param {Boolean} isGroupChat - 단체방 여부
 * @param {Object} replier - 답장 객체
 * @param {Object} imageDB - 프로필 이미지 객체
 * @param {String} packageName - 앱 패키지명
 * @param {Number} userId - 사용자 ID 해시 (임시 0)
 */
function responseFix(room, msg, sender, isGroupChat, replier, imageDB, packageName, userId) {
    
    // 디버깅용: 받은 메시지 내용을 로그캣에 출력하고 싶을 때 (Java의 Log.d 사용 불가하므로 무시)
    // 대신 replier.executeLocalScript("log", msg) 형태로 구현 가능하지만 지금은 생략

    // 1. 모든 방에서 작동하는 테스트 (테스트가 끝나면 주석 처리하세요)
    if (msg.startsWith("/테스트")) {
        replier.reply("봇 작동 중입니다! (방: " + room + ", 보낸이: " + sender + ")");
    }

    // 2. 특정 방 테스트
    if (room == "테스트") {
        replier.reply("받은 메시지: " + msg);
    }

    // 3. 트레이딩 봇
    if (room == "트레이딩-봇" && msg.includes("매수")) {
        replier.reply("✅ 매수 주문 실행 중...");
        // N8N 호출 예시
        // replier.executeWorkflow("buy", { "symbol": "BTC" });
    }
}