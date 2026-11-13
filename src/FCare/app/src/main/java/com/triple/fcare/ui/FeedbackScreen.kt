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
import com.triple.fcare.ui.theme.GradeComfort
import com.triple.fcare.ui.theme.GradeTired
import com.triple.fcare.ui.theme.color
import com.triple.fcare.work.DayFeedback
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 피드백 탭 (개선사항 2·6): 오늘 점수의 감점 요인과 건강 조언.
 * 감점 요인은 "평소(60점)보다 몇 점 낮아졌나/높아졌나"를 지표별로 보여 준다.
 */
@Composable
fun FeedbackScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val fb by vm.feedback.collectAsState()
    val loading by vm.feedbackLoading.collectAsState()
    val aiOn by vm.aiEnabled.collectAsState()
    LaunchedEffect(Unit) { vm.loadFeedback() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val f = fb
        when {
            f != null -> FeedbackBody(f, aiOn)
            loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> AppCard {
                Text("오늘 회복 점수가 확정되면 피드백이 나와요.", style = MaterialTheme.typography.bodyLarge)
                Text("어젯밤 수면 기록이 들어오면 점수와 함께 감점 요인·조언을 보여 드려요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        Text("FCare는 의료기기가 아니며, 조언은 생활 습관 참고용이에요.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun FeedbackBody(f: DayFeedback, aiOn: Boolean) {
    val r = f.result
    val i = f.input

    // ---- 점수 구성: 평소 60점 → 오늘 점수 ----
    AppCard {
        Text("점수 구성", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("평소 ${Params.SCORE_BASE.roundToInt()}점  →  ", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${i.score.roundToInt()}점", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            StatusLabel(i.grade.label, i.grade.color())
        }
        Text("항목별 합계 ${points(r.breakdown.totalPoints)}점", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 상한·잘림이 있으면 합계와 점수가 다른 이유를 알려 준다
        if (i.capRules.isNotEmpty()) {
            Text("⚠ ${i.capRules.first().message} (점수 상한 ${Params.CAP_SCORE.roundToInt()}점 적용)",
                style = MaterialTheme.typography.bodySmall, color = GradeTired, modifier = Modifier.padding(top = 6.dp))
        } else if (r.breakdown.clipped) {
            Text("점수는 0~100점 범위로 맞췄어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }

    // ---- 감점 요인 (감점이 큰 순서) ----
    SectionTitle("감점 요인")
    AppCard {
        val maxAbs = r.breakdown.factors.maxOfOrNull { abs(it.points) }?.coerceAtLeast(1.0) ?: 1.0
        r.breakdown.axes.forEachIndexed { idx, axis ->
            if (idx > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            AxisBlock(axis, r.details, maxAbs)
        }
        r.notes.forEach {
            Text("· $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
        }
    }

    // ---- 오늘의 조언 ----
    SectionTitle("오늘의 조언")
    AppCard {
        if (aiOn && f.aiText != null) {
            Text(f.aiText, style = MaterialTheme.typography.bodyLarge)
            Text("AI(Gemini)가 다듬은 문장이에요", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        } else {
            r.advices.forEachIndexed { idx, a ->
                if (idx > 0) Spacer(Modifier.height(10.dp))
                Text(a.fact, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(a.action, style = MaterialTheme.typography.bodyMedium)
            }
            if (aiOn) Text("AI 문장을 만들지 못해 기본 문장을 보여 드려요 (인터넷 연결 확인)", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        LabeledRow("오늘 추천 활동", r.activity)
    }
}

/** 축 하나 (수면은 세부 지표 3개를 함께 표시) */
@Composable
private fun AxisBlock(axis: AxisPoints, details: Map<com.triple.fcare.engine.Factor, String>, maxAbs: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(axis.axis.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("${points(axis.points)}점", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = pointColor(axis.points))
    }
    if (axis.parts.size == 1) {
        PointsBar(axis.points, maxAbs)
        details[axis.parts.first().factor]?.let { DetailText(it) }
    } else {
        axis.parts.forEach { part ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(part.factor.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(points(part.points), style = MaterialTheme.typography.bodyMedium, color = pointColor(part.points))
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
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (p < 0 && frac > 0f) Box(Modifier.fillMaxWidth(frac).fillMaxHeight().align(Alignment.CenterEnd).background(GradeTired))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (p > 0 && frac > 0f) Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(GradeComfort))
        }
    }
}

@Composable
private fun DetailText(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun points(p: Double): String {
    val r = (p * 10).roundToInt() / 10.0
    return when {
        r > 0 -> "+%.1f".format(r)
        r < 0 -> "−%.1f".format(-r)
        else -> "0.0"
    }
}

@Composable
private fun pointColor(p: Double): Color = when {
    p <= -0.05 -> GradeTired
    p >= 0.05 -> GradeComfort
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
