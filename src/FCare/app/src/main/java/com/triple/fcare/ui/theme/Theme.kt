package com.triple.fcare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.triple.fcare.engine.Grade

/**
 * 밝은(화이트) 테마 — 개선사항(11-18) 4: 휴대폰이 다크 모드여도 앱은 항상 이 테마를 쓴다.
 * 배경은 흰색, 카드·하단 탭은 아주 옅은 회색, 게이지·막대의 빈 부분은 surfaceVariant.
 */
private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCEFEA),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Gray92,               // 게이지·막대 빈 부분
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Gray97,             // 카드·하단 탭
    surfaceContainerHigh = Color.White,    // 대화상자·날짜 선택
    surfaceContainerHighest = Gray92,
)

/** 브랜드 색을 항상 쓰도록 동적 색상(배경화면 색 따라가기)은 쓰지 않고, 다크 모드도 따르지 않는다. */
@Composable
fun FCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}

/** 등급별 표시 색 */
fun Grade.color(): Color = when (this) {
    Grade.TIRED -> GradeTired
    Grade.NORMAL -> GradeNormal
    Grade.COMFORT -> GradeComfort
    Grade.LIGHT -> GradeLight
}
