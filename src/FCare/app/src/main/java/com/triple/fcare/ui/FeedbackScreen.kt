package com.triple.fcare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.triple.fcare.engine.AxisPoints
import com.triple.fcare.engine.Params
import com.triple.fcare.ui.theme.ChartUsual
import com.triple.fcare.ui.theme.Dim
import com.triple.fcare.ui.theme.FcText
import com.triple.fcare.ui.theme.FillTrack
import com.triple.fcare.ui.theme.GradeComfort
import com.triple.fcare.ui.theme.GradeComfortText
import com.triple.fcare.ui.theme.GradeTired
import com.triple.fcare.ui.theme.GradeTiredText
import com.triple.fcare.ui.theme.Radius
import com.triple.fcare.ui.theme.Separator
import com.triple.fcare.ui.theme.Space
import com.triple.fcare.ui.theme.TextPrimary
import com.triple.fcare.ui.theme.TextSecondary
import com.triple.fcare.ui.theme.TextTertiary
import com.triple.fcare.work.DateRules
import com.triple.fcare.work.DayFeedback
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 분석 탭 (개선사항 2·6, 11-18 "피드백" → "분석"): 오늘 점수의 감점 요인과 건강 조언.
 * 감점 요인은 "평소(60점)보다 몇 점 낮아졌나/높아졌나"를 지표별로 보여 준다.
 */
@Composable
fun FeedbackScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val fb by vm.feedback.collectAsState()
    val loading by vm.feedbackLoading.collectAsState()
    val aiOn by vm.aiEnabled.collectAsState()
    LaunchedEffect(Unit) { vm.loadFeedback() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).padding(bottom = Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        ScreenTitle("오늘의 분석")   // 상단바 대신 큰 제목 (UI 개편 11-20)
        val f = fb
        when {
            f != null -> {
                // 오늘이 데이터 부족이면 가장 최근 점수 날의 분석을 보여 준다 (HOM-07, 상세 3.7)
                val d = DateRules.scoreDate(ZonedDateTime.now())
                if (f.date != d) AppCard {
                    Text("오늘은 수면 기록이 없어 ${untilLabel(f.date, d, withDate = true)}까지의 분석을 보여 드려요.",
                        style = FcText.sm, color = TextSecondary)
                }
                FeedbackBody(f, aiOn)
            }
            loading -> Box(Modifier.fillMaxWidth().padding(Space.s10), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TextSecondary)
            }
            else -> AppCard {
                Text("오늘 회복 점수가 확정되면 분석이 나와요.", style = FcText.base, color = TextPrimary)
                Text("어젯밤 수면 기록이 들어오면 점수와 함께 감점 요인·조언을 보여 드려요.",
                    style = FcText.sm, color = TextSecondary, modifier = Modifier.padding(top = Space.s1))
            }
        }
        // "의료기기 아님" 문구는 프로필 맨 아래 한 곳으로 옮김 (11-20)
    }
}

@Composable
private fun FeedbackBody(f: DayFeedback, aiOn: Boolean) {
    val r = f.result
    val i = f.input

    // ---- 점수 구성: 평소 60점 → 오늘 점수 ----
    AppCard {
        CardLabel("점수 구성")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.s1)) {
            Text("평소 ${Params.SCORE_BASE.roundToInt()}점  →  ", style = FcText.base, color = TextSecondary)
            NumberWithUnits(listOf("${i.score.roundToInt()}" to "점"))
            Spacer(Modifier.width(Space.s2))
            GradeChip(i.grade)
        }
        // 상한·잘림이 있으면 합계와 점수가 다른 이유를 알려 준다 (짧은 줄 + 이유 줄, 각 24자 이하)
        if (i.capRules.isNotEmpty()) {
            Text("⚠ 점수 상한 ${Params.CAP_SCORE.roundToInt()}점", style = FcText.sm.copy(fontWeight = FontWeight.SemiBold),
                color = GradeTiredText, modifier = Modifier.padding(top = Space.s2))
            Text(i.capRules.first().message, style = FcText.sm, color = TextPrimary)
        } else if (r.breakdown.clipped) {
            Text("점수는 0~100점 범위로 맞췄어요.", style = FcText.sm, color = TextSecondary,
                modifier = Modifier.padding(top = Space.s2))
        }
    }

    // ---- 감점 요인 (감점이 큰 순서) ----
    SectionTitle("감점 요인")
    AppCard {
        val maxAbs = r.breakdown.factors.maxOfOrNull { abs(it.points) }?.coerceAtLeast(1.0) ?: 1.0
        r.breakdown.axes.forEachIndexed { idx, axis ->
            if (idx > 0) HorizontalDivider(Modifier.padding(vertical = Space.s2), thickness = Dim.hairline, color = Separator)
            AxisBlock(axis, r.details, maxAbs)
        }
        // 개선사항(11-18) 5: 감점 요인 합계 = 평소(60점)와 오늘 점수의 차이 (상한·0~100 범위 조정 전)
        HorizontalDivider(Modifier.padding(vertical = Space.s3), thickness = Dim.stroke, color = Separator)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("합계", Modifier.weight(1f), style = FcText.sm.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Text("${points(r.breakdown.totalPoints)}점", style = FcText.lg, color = pointColor(r.breakdown.totalPoints))
        }
        // 계산 줄 (Phase 2: 24자 이하) 예: "평소 60 −3.2 = 57점", "평소 60 +8.5 → 49점 (상한)"
        val raw = (Params.SCORE_BASE + r.breakdown.totalPoints).roundToInt()
        val calc = "평소 ${Params.SCORE_BASE.roundToInt()} ${points(r.breakdown.totalPoints)}" + when {
            i.capRules.isNotEmpty() -> " → ${i.score.roundToInt()}점 (상한)"
            r.breakdown.clipped -> " → ${i.score.roundToInt()}점 (범위)"
            else -> " = ${raw}점"
        }
        Text(calc, style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextSecondary)
        r.notes.forEach {
            Text("· $it", style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextSecondary,
                modifier = Modifier.padding(top = Space.s1))
        }
    }

    // ---- 오늘의 조언 ----
    SectionTitle("오늘의 조언")
    AppCard {
        if (aiOn && f.aiText != null) {
            Text(f.aiText, style = FcText.base, color = TextPrimary)
            Text("AI(Gemini)가 다듬은 문장이에요", style = FcText.xs, color = TextTertiary,
                modifier = Modifier.padding(top = Space.s2))
        } else {
            r.advices.forEachIndexed { idx, a ->
                if (idx > 0) Spacer(Modifier.height(Space.s3))
                Text(a.fact, style = FcText.sm.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                Text(a.action, style = FcText.sm, color = TextSecondary)
            }
            if (aiOn) Text("AI 연결이 안 돼 기본 문장이에요", style = FcText.xs, color = TextTertiary,
                modifier = Modifier.padding(top = Space.s2))
        }
        HorizontalDivider(Modifier.padding(vertical = Space.s3), thickness = Dim.hairline, color = Separator)
        LabeledRow("오늘 추천 활동", r.activity)
    }
}

/** 축 하나 (수면은 세부 지표 3개를 함께 표시) */
@Composable
private fun AxisBlock(axis: AxisPoints, details: Map<com.triple.fcare.engine.Factor, String>, maxAbs: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(axis.axis.label, Modifier.weight(1f), style = FcText.sm.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
        Text("${points(axis.points)}점", style = FcText.sm.copy(fontWeight = FontWeight.Bold), color = pointColor(axis.points))
    }
    if (axis.parts.size == 1) {
        PointsBar(axis.points, maxAbs)
        details[axis.parts.first().factor]?.let { DetailText(it) }
    } else {
        axis.parts.forEach { part ->
            Row(Modifier.padding(top = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                Text(part.factor.label, Modifier.weight(1f), style = FcText.sm, color = TextPrimary)
                Text(points(part.points), style = FcText.sm, color = pointColor(part.points))
            }
            PointsBar(part.points, maxAbs)
            details[part.factor]?.let { DetailText(it) }
        }
    }
}

/** 가운데(0)를 기준으로 감점은 왼쪽(빨강), 가점은 오른쪽(초록)으로 뻗는 막대 */
@Composable
private fun PointsBar(p: Double, maxAbs: Double) {
    val frac = (abs(p) / maxAbs).toFloat().coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s1).height(Dim.bar).clip(Radius.bar).background(FillTrack)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (p < 0 && frac > 0f) Box(Modifier.fillMaxWidth(frac).fillMaxHeight().align(Alignment.CenterEnd).background(GradeTired))
        }
        Box(Modifier.width(Dim.hairline).fillMaxHeight().background(ChartUsual))   // 0점(평소) 기준선
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (p > 0 && frac > 0f) Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(GradeComfort))
        }
    }
}

@Composable
private fun DetailText(text: String) {
    Text(text, style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextSecondary)
}

private fun points(p: Double): String {
    val r = (p * 10).roundToInt() / 10.0
    return when {
        r > 0 -> "+%.1f".format(r)
        r < 0 -> "−%.1f".format(-r)
        else -> "0.0"
    }
}

/** 감점·가점 글자색 — 고정 등급색은 글자로 쓰면 대비가 부족해 진한 글자색을 쓴다(부호 −/+도 함께 표시). */
private fun pointColor(p: Double): Color = when {
    p <= -0.05 -> GradeTiredText
    p >= 0.05 -> GradeComfortText
    else -> TextSecondary
}
