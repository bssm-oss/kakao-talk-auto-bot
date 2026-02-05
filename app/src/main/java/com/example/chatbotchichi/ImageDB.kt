package com.example.chatbotchichi

import android.graphics.Bitmap

/**
 * 스크립트에서 프로필 이미지 등에 접근하기 위한 클래스 (Messenger Bot R 호환용 Stub)
 */
class ImageDB(
    val profileBitmap: Bitmap?,
    val roomBitmap: Bitmap?
) {
    /**
     * 프로필 이미지를 Base64 문자열로 반환 (구현 필요)
     */
    fun getProfileImage(): String? {
        // 실제 구현에서는 Bitmap을 Base64로 변환하여 반환
        return null 
    }
}
