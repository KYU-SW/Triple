package com.triple.fcare.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 색 토큰 — DESIGN.md "## Color tokens / 최종 시맨틱 토큰"(2026-11-20 잠금)을 그대로 옮김.
 * 방향 "평소선 기록지": 연회색 종이 위 흰 카드, 숫자는 먹색, 색은 등급·그래프에만 쓴다.
 * 이름은 CSS 토큰 이름(--background → Background)을 그대로 따른다.
 */

// ---- 바탕 · 글자 · 강조 ----
val Background = Color(0xFFE6E8EC)      // --background: 화면 바탕(연회색 종이)
val SurfaceWhite = Color(0xFFFFFFFF)    // --surface: 흰 카드·하단 탭·대화상자
val Separator = Color(0xFFBFC4CC)       // --separator: 목록 줄 사이 1dp 선 (카드 테두리로는 쓰지 않음)
val FillTrack = Color(0xFFE6E8EC)       // --fill-track: 막대의 빈 부분
val TextPrimary = Color(0xFF2C2E31)     // --text: 숫자·제목·본문 (흰 카드 13.62:1)
val TextSecondary = Color(0xFF494C52)   // --text-secondary: 카드 라벨·보조 줄
val TextTertiary = Color(0xFF60636A)    // --text-tertiary: 단위·축 라벨·꺼진 탭 글자
val AccentSolid = Color(0xFF2563C9)     // --accent-solid: 주 버튼 바탕 (브랜드 블루, 고정)
val AccentOnSolid = Color(0xFFFFFFFF)   // --accent-on-solid
val AccentText = Color(0xFF2563C9)      // --accent-text: "분석 보기" 같은 이동 글자
val AccentSubtle = Color(0xFFE8F1FF)    // --accent-subtle: 눌림 바탕·선택 날짜 바탕

// ---- 등급 (고정색은 점·막대에만, 글자는 -Text, 칩 바탕은 -Subtle) ----
val GradeTired = Color(0xFFE5534B)
val GradeTiredSubtle = Color(0xFFFFEBE9)
val GradeTiredText = Color(0xFFA93530)
val GradeNormal = Color(0xFFF2A33A)
val GradeNormalSubtle = Color(0xFFFFEDDA)
val GradeNormalText = Color(0xFF885711)
val GradeComfort = Color(0xFF34A853)
val GradeComfortSubtle = Color(0xFFD8FBDD)
val GradeComfortText = Color(0xFF00782F)
val GradeLight = Color(0xFF2563C9)
val GradeLightSubtle = Color(0xFFE8F1FF)
val GradeLightText = Color(0xFF2563C9)

// ---- 기능색 ----
val ErrorSubtle = Color(0xFFFFEBE9)
val ErrorText = Color(0xFF974843)
val SuccessText = Color(0xFF337437)

// ---- 하단 탭 (사용자 결정 11-20: 4칸, 선택 칸은 옅은 회색 면) ----
val TabBg = Color(0xFFFFFFFF)          // 선택 안 된 칸
val TabFg = Color(0xFF60636A)          // 선택 안 된 칸 아이콘·글자
val TabSelectedBg = Color(0xFFD8DBE0)  // 선택 칸 전체 면 (흰 칸과 1.39:1 — 사용자 결정)
val TabSelectedFg = Color(0xFF2C2E31)  // 선택 칸 아이콘(채움)·글자(굵게)
val TabDivider = Color(0xFFBFC4CC)     // 칸 사이 세로 1dp 선

// ---- 그래프 (11-20 새 팔레트, 등급 4색·강조 블루는 그대로) ----
val ChartScoreLine = Color(0xFF717882) // 점수 선 (점은 등급색)
val ChartSleep = Color(0xFF2563C9)     // 총 수면 시간 선
val ChartHr = Color(0xFF007460)        // 수면 중 최저 심박 선
val ChartLoad = Color(0xFFCD6F42)      // 운동 부하 선·마커 (선 전용, 글자 금지)
val ChartLoadText = Color(0xFF8B5339)  // 운동 부하 범례·값 글자
val ChartRefMin = Color(0xFF964750)    // 최소 6시간 점선
val ChartUsual = Color(0xFF2C2E31)     // 평소선(점수 60·부하 1.0)·기간 평균 점선
val ChartGrid = Color(0xFFCED2D9)      // 장식용 격자
const val CHART_ZONE_ALPHA = 0.08f     // 등급 구간 띠 = 등급색 × 이 알파

// ---- 상태 알파 (이름만 붙인 기존 값) ----
const val STATE_PRESSED_ALPHA = 0.12f  // 눌림 리플
const val SCRIM_ALPHA = 0.32f          // 대화상자 뒤 가림
