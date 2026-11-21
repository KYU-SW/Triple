package com.triple.fcare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.triple.fcare.engine.Grade

/**
 * 밝은 테마 하나만 쓴다(11-18 결정: 다크 모드·동적 색상 무시).
 * Material 색 이름을 DESIGN.md 토큰에 연결해, Material 부품(대화상자·버튼·스위치 등)도
 * 같은 톤을 쓰게 한다. surfaceTint를 투명으로 두어 Material 3의 보라빛 겹침을 끈다.
 */
private val LightColors = lightColorScheme(
    primary = AccentSolid,
    onPrimary = AccentOnSolid,
    primaryContainer = AccentSubtle,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,              // 옛 청록 대신 중립 회색 — 색은 데이터에만
    onSecondary = SurfaceWhite,
    secondaryContainer = TabSelectedBg,
    onSecondaryContainer = TextPrimary,
    tertiary = ChartHr,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = FillTrack,             // 막대 빈 부분
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = SurfaceWhite,
    surfaceContainer = SurfaceWhite,        // 카드 = 흰색 (바탕이 연회색이라 그림자 없이 구분됨)
    surfaceContainerHigh = SurfaceWhite,    // 대화상자·날짜 선택
    surfaceContainerHighest = FillTrack,
    outline = TextTertiary,                 // 입력 칸·꺼진 스위치 윤곽 (사용자 결정 11-20: 테두리 유지)
    outlineVariant = Separator,             // 구분선
    error = ErrorText,
    onError = SurfaceWhite,
    errorContainer = ErrorSubtle,
    onErrorContainer = ErrorText,
    scrim = TextPrimary,
)

@Composable
fun FCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}

/** 등급 고정색 — 점·막대에만 쓴다(보통·편안함은 글자로 쓰면 대비 미달). */
fun Grade.color(): Color = when (this) {
    Grade.TIRED -> GradeTired
    Grade.NORMAL -> GradeNormal
    Grade.COMFORT -> GradeComfort
    Grade.LIGHT -> GradeLight
}

/** 등급 글자색 (AA 통과) */
fun Grade.textColor(): Color = when (this) {
    Grade.TIRED -> GradeTiredText
    Grade.NORMAL -> GradeNormalText
    Grade.COMFORT -> GradeComfortText
    Grade.LIGHT -> GradeLightText
}

/** 등급 칩 바탕 */
fun Grade.subtleColor(): Color = when (this) {
    Grade.TIRED -> GradeTiredSubtle
    Grade.NORMAL -> GradeNormalSubtle
    Grade.COMFORT -> GradeComfortSubtle
    Grade.LIGHT -> GradeLightSubtle
}
