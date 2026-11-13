package com.triple.fcare.hc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.triple.fcare.ui.theme.FCareTheme

/** 데이터별 사용 목적 문구 (PRM-04). 권한 화면과 이 화면에서 함께 쓴다. */
val PERMISSION_PURPOSES = listOf(
    "수면" to "총 수면 시간, 수면 효율, 잠든·깬 시각의 규칙성을 계산해요.",
    "심박수" to "수면 중 최저 심박과 운동 강도(TRIMP)를 계산해요.",
    "운동" to "운동 시간과 운동 부하를 계산해요.",
    "백그라운드 읽기" to "앱을 열지 않아도 아침에 회복 점수를 미리 계산해요.",
)

/**
 * Health Connect가 "이 앱이 데이터를 어디에 쓰나요?"를 보여 줄 때 여는 화면.
 * AndroidManifest에 선언돼 있어야 권한 요청이 가능하다.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FCareTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("FCare 건강 데이터 사용 목적", style = MaterialTheme.typography.titleLarge)
                        PERMISSION_PURPOSES.forEach { (name, purpose) -> Text("• $name: $purpose") }
                        Text("읽은 데이터는 회복 점수 계산에만 쓰고, 휴대폰 밖으로 보내지 않아요.")
                        Button(onClick = { finish() }) { Text("닫기") }
                    }
                }
            }
        }
    }
}
