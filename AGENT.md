# 🤖 Chatbotchichi Scripting Guide

이 문서는 `Chatbotchichi` 봇 엔진을 위한 스크립트 작성 규격 및 API 명세를 설명합니다.

## 📝 진입점 함수

모든 봇 스크립트는 반드시 아래 형태의 `responseFix` 함수를 포함해야 합니다.

```javascript
function responseFix(room, msg, sender, isGroupChat, replier, imageDB, packageName, userId) {
    // 봇 로직 작성
}
```

### 파라미터 상세
- `room`: (String) 메시지가 온 채팅방 이름
- `msg`: (String) 메시지 내용
- `sender`: (String) 보낸 사람 이름
- `isGroupChat`: (Boolean) 단톡방 여부
- `replier`: (Object) 답장 기능을 수행하는 Java 객체
- `imageDB`: (Object) 프로필 이미지 접근 객체
- `packageName`: (String) 알림이 발생한 패키지 이름
- `userId`: (Number) 사용자 고유 ID (현재는 임시 0)

## ⚡ Replier API

`replier` 객체를 통해 다음과 같은 기능을 수행할 수 있습니다.

### 1. `reply(message)`
메시지가 온 해당 방으로 즉시 답장을 보냅니다.
```javascript
replier.reply("반가워요!");
```

### 2. `replyToRoom(targetRoom, message)`
특정 이름의 채팅방으로 메시지를 보냅니다. (이전에 알림을 받아 세션이 확보된 상태여야 함)
```javascript
replier.replyToRoom("공지방", "새로운 메시지가 왔습니다: " + msg);
```

### 3. `executeWorkflow(actionType, data)`
N8N 등 외부 웹훅으로 데이터를 전송합니다.
```javascript
replier.executeWorkflow("order", { "item": "apple", "count": 1 });
```

## 🌐 외부 통신 (HTTP)

엔진 내부에서 Java의 표준 라이브러리를 직접 사용할 수 있습니다.

```javascript
var URL = java.net.URL;
var conn = new URL("https://api.example.com").openConnection();
// ... 표준 자바 코드와 동일하게 사용 가능
```

## 🛠 디버깅 팁

- 스크립트에서 발생한 에러는 앱 메인 화면의 **[실시간 로그]** 창에 즉시 출력됩니다.
- 문법 에러가 있을 경우 해당 봇은 실행되지 않으며 로그에 `❌ ... 에러` 라고 뜹니다.
- `String(msg)` 등으로 타입을 강제 변환하여 사용하는 것이 안전합니다.
