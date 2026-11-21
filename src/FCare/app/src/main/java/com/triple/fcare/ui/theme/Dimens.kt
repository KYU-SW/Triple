package com.triple.fcare.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/*
 * 간격 · 모서리 · 크기 토큰 — DESIGN.md "## Space, shape, depth"와 :root 크기 토큰(11-20 사용자 승인).
 * 깊이 체계는 없음: 그림자·테두리·그라데이션 0. 카드와 바탕은 색 면 차이로만 구분한다.
 */
object Space {
    val s1 = 4.dp    // --space-1: 한 덩어리 안(라벨↔숫자)
    val s2 = 8.dp    // --space-2: 홈 블록 사이
    val s3 = 12.dp   // --space-3: 다른 화면 카드 사이, 홈 카드 안 여백
    val s4 = 16.dp   // --space-4: 화면 양옆, 글 많은 카드 안 여백
    val s6 = 24.dp   // --space-6: 섹션 사이
    val s8 = 32.dp   // --space-8
    val s10 = 40.dp  // --space-10
}

object Radius {
    val card = RoundedCornerShape(14.dp)     // --radius-card: 흰 카드 전부
    val control = RoundedCornerShape(12.dp)  // --radius-control: 버튼·입력 칸
    val chip = RoundedCornerShape(6.dp)      // --radius-chip: 칩 (알약 모양 아님)
    val sheet = RoundedCornerShape(24.dp)    // --radius-sheet: 대화상자
    val bar = RoundedCornerShape(3.dp)       // --radius-bar: 막대 모서리
    val full = RoundedCornerShape(50)        // --radius-full: 점·원
}

/** 크기 토큰 (--size-*). 이름이 Compose의 geometry.Size와 겹치지 않게 Dim으로 둔다. */
object Dim {
    val hairline = 1.dp       // 구분선
    val stroke = 2.dp         // 포커스 링·진한 윤곽
    val strokeBold = 3.dp     // 진행 호
    val touchMin = 48.dp      // 누르는 영역 최소
    val button = 52.dp        // 주 버튼·입력 칸 높이
    val tabbar = 68.dp        // 하단 탭 (사용자 결정 11-20: 80 → 68)
    val rowMenu = 56.dp       // 메뉴·스위치 줄
    val chip = 28.dp          // 등급 칩(점수 카드)·상태 칩
    val chipSm = 24.dp        // 등급 칩(목록)
    val chipSlot = 64.dp      // 목록 등급 칩 자리 폭
    val chipGap = 6.dp
    val dot = 8.dp            // 등급 점(칩 28)
    val dotSm = 6.dp          // 등급 점(칩 24)
    val bar = 6.dp            // 막대 두께(점수·부하)
    val marker = 16.dp        // 부하 값 마커 지름·평소 눈금 높이
    val markerRing = 24.dp    // 부하 막대 줄 높이
    val icon = 24.dp
    val iconSm = 20.dp        // 카드 라벨·하단 탭 아이콘
    val iconXs = 16.dp        // › 와 보조 줄 아이콘
    val tile = 100.dp         // 지표 타일 최소 높이
    val dialogMax = 312.dp
    val scoreCard = 176.dp    // 점수 카드 일반 높이(불러오는 중 자리 유지)
    val loadCard = 108.dp     // 부하 카드 일반 높이
}
