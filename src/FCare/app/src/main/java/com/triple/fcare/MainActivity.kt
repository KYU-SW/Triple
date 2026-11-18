package com.triple.fcare

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
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
        // 앱은 항상 밝은 화면이므로 상단 상태 표시줄·하단 탐색 줄 아이콘도 항상 어두운 색으로 (11-18 개선 4)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // 백그라운드 작업 예약 (1시간 확인, 12:00 마감). 이미 예약돼 있으면 그대로 둔다.
        WorkScheduler.scheduleAll(this)
        // 점수 알림을 눌러 들어온 경우 분석 탭을 연다 (개선사항 4)
        val openFeedback = intent?.getStringExtra(Notifier.EXTRA_OPEN_TAB) == Notifier.TAB_FEEDBACK
        setContent {
            FCareTheme {
                Surface(Modifier.fillMaxSize()) { FCareApp(openFeedback = openFeedback) }
            }
        }
    }
}
