function responseFix(room, msg, sender, isGroupChat, replier, imageDB, packageName, userId) {
    if (msg == "/hello") {
        if (replier != null) {
            replier.reply("안녕하세요! 저는 별도의 파일(test_bot.js)에서 동작하는 테스트 봇입니다.");
        } else {
            // 디버깅 룸에서는 replier가 null일 수 있음
            // java.lang.System.out.println("가상 응답: 안녕하세요!");
        }
    }
}
