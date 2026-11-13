package com.triple.fcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.triple.fcare.ui.FCareApp
import com.triple.fcare.ui.theme.FCareTheme
import com.triple.fcare.work.Notifier
import com.triple.fcare.work.WorkScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 백그라운드 작업 예약 (1시간 확인, 12:00 마감). 이미 예약돼 있으면 그대로 둔다.
        WorkScheduler.scheduleAll(this)
        // 점수 알림을 눌러 들어온 경우 피드백 탭을 연다 (개선사항 4)
        val openFeedback = intent?.getStringExtra(Notifier.EXTRA_OPEN_TAB) == Notifier.TAB_FEEDBACK
        setContent {
            FCareTheme {
                Surface(Modifier.fillMaxSize()) { FCareApp(openFeedback = openFeedback) }
            }
        }
    }
}
