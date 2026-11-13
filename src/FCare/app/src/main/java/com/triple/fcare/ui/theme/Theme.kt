package com.triple.fcare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.triple.fcare.engine.Grade

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCEFEA),
    background = Surface,
    surface = Surface,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Blue95,
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue10,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Teal80,
)

/** 브랜드 색을 항상 쓰도록 동적 색상(배경화면 색 따라가기)은 쓰지 않는다. */
@Composable
fun FCareTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
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
