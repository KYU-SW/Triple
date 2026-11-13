package com.triple.fcare.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.triple.fcare.MainActivity
import com.triple.fcare.R
import kotlin.math.roundToInt

/**
 * 점수 확정 알림 (개선사항 4: 점수 첨부 + 점수 기반 활동 추천).
 * 백그라운드에서 점수가 확정됐을 때만 보낸다 (앱을 보고 있을 때는 화면에 이미 나오므로 생략).
 * 알림을 누르면 피드백 탭이 열린다.
 */
object Notifier {
    private const val CHANNEL_ID = "score"
    private const val NOTIFICATION_ID = 1001
    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_FEEDBACK = "feedback"

    fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "오늘의 회복 점수", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "어젯밤 수면이 들어와 회복 점수가 확정되면 알려 줘요."
                }
            )
        }
    }

    /**
     * @param line 조언 한 줄 (Gemini 문장 또는 규칙 문장)
     * @param activity 오늘의 활동 추천
     */
    fun showScore(context: Context, score: Double, gradeLabel: String, line: String, activity: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_TAB, TAB_FEEDBACK)
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = "$line\n오늘 추천: $activity"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("오늘의 회복 점수 ${score.roundToInt()}점 · $gradeLabel")
            .setContentText("오늘 추천: $activity")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 권한이 방금 취소된 경우 — 알림만 건너뛴다
        }
    }
}
