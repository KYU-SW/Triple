package com.triple.fcare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.triple.fcare.R

/*
 * 글자 — DESIGN.md "## Type"(잠금).
 * Pretendard 한 가족만, 굵기마다 실제 글꼴 파일(가짜 굵기 금지). 글자는 모두 sp.
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),    // 400
    Font(R.font.pretendard_medium, FontWeight.Medium),     // 500
    Font(R.font.pretendard_semibold, FontWeight.SemiBold), // 600
    Font(R.font.pretendard_bold, FontWeight.Bold),         // 700
)

/** 숫자 줄은 위아래 여백을 잘라 블록 높이 예산을 지킨다 (DESIGN.md 줄 높이 규칙) */
private val TrimNumber = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/** 크기 체계 --text-xs … --text-4xl (크기 / 줄 높이 / 굵기) */
object FcText {
    val xs = TextStyle(fontFamily = Pretendard, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val sm = TextStyle(fontFamily = Pretendard, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val base = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val lg = TextStyle(fontFamily = Pretendard, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val xl = TextStyle(fontFamily = Pretendard, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    /** 지표 숫자(걸음·수면·부하 비율) — 고정폭 숫자 */
    val x2l = TextStyle(fontFamily = Pretendard, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum", lineHeightStyle = TrimNumber)
    /** 화면 큰 제목(분석·기록·프로필 등) */
    val x3l = TextStyle(fontFamily = Pretendard, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.02).em)
    /** 회복 점수 숫자 — 화면에서 가장 큼 */
    val x4l = TextStyle(fontFamily = Pretendard, fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em, fontFeatureSettings = "tnum", lineHeightStyle = TrimNumber)
}

/**
 * Material 기본 글자 스타일도 같은 체계로 맞춰, FcText를 직접 쓰지 않는 Material 부품
 * (대화상자·버튼·날짜 선택 등)도 Pretendard와 같은 크기 단계를 쓰게 한다.
 */
val Typography = Typography(
    displayMedium = FcText.x4l,
    headlineLarge = FcText.x3l,
    headlineSmall = FcText.xl,
    titleLarge = FcText.xl,
    titleMedium = FcText.lg,
    titleSmall = FcText.sm.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = FcText.base,
    bodyMedium = FcText.sm,
    bodySmall = FcText.xs.copy(fontWeight = FontWeight.Normal),
    labelLarge = FcText.sm.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = FcText.xs,
    labelSmall = FcText.xs,
)
