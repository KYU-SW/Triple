package com.triple.fcare.work

import android.content.Context

/**
 * 앱 설정 (SharedPreferences). 계산과 무관한 사용자 선택만 저장한다.
 * - aiEnabled: Gemini 문장 다듬기 동의 여부 (기본 꺼짐 — 외부 전송이므로 사용자가 직접 켜야 함)
 * - notifyEnabled: 점수 확정 알림 (개선사항 4, 기본 켜짐)
 * - notifyAsked: 알림 권한을 한 번 물어봤는지
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fcare_settings", Context.MODE_PRIVATE)

    var aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI, false)
        set(v) = prefs.edit().putBoolean(KEY_AI, v).apply()

    var notifyEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(v) = prefs.edit().putBoolean(KEY_NOTIFY, v).apply()

    var notifyAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_ASKED, false)
        set(v) = prefs.edit().putBoolean(KEY_NOTIFY_ASKED, v).apply()

    private companion object {
        const val KEY_AI = "ai_enabled"
        const val KEY_NOTIFY = "notify_enabled"
        const val KEY_NOTIFY_ASKED = "notify_asked"
    }
}
