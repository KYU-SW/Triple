package com.triple.fcare.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.triple.fcare.data.RecoveryScoreEntity
import com.triple.fcare.engine.CapRule
import com.triple.fcare.engine.Grade
import com.triple.fcare.ui.theme.color
import com.triple.fcare.ui.theme.GradeLight
import com.triple.fcare.ui.theme.GradeComfort
import com.triple.fcare.ui.theme.GradeNormal
import com.triple.fcare.ui.theme.GradeTired
import kotlin.math.abs
import kotlin.math.roundToInt

/* ---------- 표시용 변환 ---------- */

/** 18:00 기준 경과 분 → "HH:mm" */
fun clockFrom18(m: Double): String {
    val total = ((m.roundToInt() + 18 * 60) % 1440 + 1440) % 1440
    return "%02d:%02d".format(total / 60, total % 60)
}

/** 분 → "7시간 20분" */
fun hoursMinutes(min: Double): String {
    val m = min.roundToInt()
    return "${m / 60}시간 ${m % 60}분"
}

fun gradeOf(e: RecoveryScoreEntity?): Grade? = e?.grade?.let { runCatching { Grade.valueOf(it) }.getOrNull() }

fun capOf(e: RecoveryScoreEntity): CapRule? =
    e.capRules?.split(",")?.firstOrNull()?.let { runCatching { CapRule.valueOf(it) }.getOrNull() }

/** 홈 한 줄 안내 (HOM-03): 상한이 걸린 날은 이유 문구. */
fun messageOf(e: RecoveryScoreEntity): String = capOf(e)?.message ?: gradeOf(e)?.message ?: ""

fun signed(x: Double): String = (if (x >= 0) "+" else "−") + "%.0f".format(abs(x))

fun exerciseName(type: Int): String = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "러닝"
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "걷기"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "사이클"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "근력"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "수영"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "등산"
    ExerciseSessionRecord.EXERCISE_TYPE_SOCCER, ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL,
    ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON, ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "구기"
    else -> "기타 운동"
}

/** HOM-10 워치 착용 안내 문구 */
const val WATCH_GUIDE =
    "운동할 때는 워치를 꼭 착용해 주세요.\n\n" +
        "FCare는 워치로 기록한 운동의 심박으로 운동 부하를 계산해요. " +
        "워치 없이 한 운동은 반영되지 않고, 근력 운동처럼 심박이 적게 오르는 운동은 실제보다 적게 반영될 수 있어요."

/* ---------- 공통 컴포넌트 ---------- */

/** 흰 바탕 둥근 카드 */
@Composable
fun AppCard(modifier: Modifier = Modifier, container: Color? = null, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container ?: MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

/** 제목이 있는 카드 */
@Composable
fun InfoCard(title: String, content: @Composable () -> Unit) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** 섹션 제목 */
@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 지표 타일: 작은 이름 + 큰 값 + 보조 설명 */
@Composable
fun MetricTile(label: String, value: String, caption: String? = null, accent: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** 타일 두 개를 한 줄에 */
@Composable
fun TileRow(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}

/** 작은 색 표시 라벨 (등급, 상태) */
@Composable
fun StatusLabel(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * 원형 게이지 (HOM-01): 0~100점을 270° 호로 표시하고 가운데 점수를 쓴다.
 * score가 null이면 빈 게이지와 centerText만 표시한다.
 */
@Composable
fun ScoreGauge(score: Double?, grade: Grade?, size: Dp = 200.dp, centerText: String? = null) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = grade?.color() ?: MaterialTheme.colorScheme.primary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke)
            if (score != null) {
                drawArc(fill, startAngle = 135f, sweepAngle = (270f * (score / 100).toFloat()).coerceIn(0f, 270f),
                    useCenter = false, style = stroke)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (score != null) {
                Text("${score.roundToInt()}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text("/ 100", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(centerText ?: "-", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 칩 선택 (성별, 운동 빈도 등) */
@Composable
fun <T> ChipGroup(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        title = { Text("워치 착용 안내") },
        text = { Text(WATCH_GUIDE) },
    )
}

/** 개선사항(11-18) 3: 앱에서 쓰는 용어 설명 (프로필 → 도움말 → 용어 설명) */
val GLOSSARY: List<Pair<String, String>> = listOf(
    "회복 점수" to "0~100점. 어젯밤 수면, 수면 중 최저 심박, 최근 운동량을 내 평소와 비교해 계산해요. 평소와 같은 날이 60점이고, 높을수록 몸이 가벼운 상태예요.",
    "등급" to "피로함(0~25) · 보통(25~50) · 편안함(50~75) · 가벼움(75~100)",
    "평소 (개인 기준선)" to "최근 28일 동안의 내 기록 평균이에요. 기록이 7일보다 적으면 일반 기준값과 섞어서 비교해요(기준선 쌓는 중).",
    "총 수면 시간" to "잠든 시간에서 중간에 깬 시간을 뺀 실제 수면 시간이에요. 최근 3일 평균이 6시간보다 적으면 평소와 상관없이 추가로 감점돼요.",
    "수면 효율" to "누워 있던 시간 중 실제로 잔 시간의 비율이에요. 워치가 수면 단계를 기록한 날만 계산해요.",
    "수면 규칙성" to "잠든 시각과 깬 시각이 평소와 얼마나 비슷한지예요. 평소와 많이 다르면 감점돼요.",
    "수면 중 최저 심박" to "잠자는 동안 가장 낮았던 심박이에요. 평소보다 높으면 피로나 스트레스가 덜 풀린 신호일 수 있어요.",
    "TRIMP" to "운동 한 번의 강도와 시간을 합친 운동량 점수예요. 워치가 잰 운동 중 심박으로 계산해요.",
    "ATL (최근 7일 부하)" to "최근 1주일 TRIMP의 평균이에요. 최근 날일수록 더 크게 반영해요.",
    "CTL (평소 부하)" to "최근 4주 TRIMP의 평균이에요. 내 평소 운동량을 뜻해요.",
    "운동 부하 비율" to "ATL ÷ CTL이에요. 1.0이면 평소와 같고, 1.3을 넘으면 평소보다 운동량이 많이 늘어난 상태예요.",
    "운동 기준선 쌓는 중" to "워치를 찬 날이 14일보다 적으면 운동 부하를 약하게만 반영해요.",
    "점수 상한 (49점)" to "수면 중 심박 급상승, 운동량 급증, 심한 수면 부족 중 하나라도 있으면 다른 항목이 좋아도 49점을 넘지 않아요.",
    "일부 데이터로 계산" to "심박이나 운동 기록이 없어 그 항목을 빼고 계산한 날이에요.",
    "데이터 부족" to "12:00까지 어젯밤 수면 기록이 들어오지 않아 점수를 내지 못한 날이에요.",
    "감점 요인" to "분석 탭에서 각 항목이 평소(60점)보다 점수를 몇 점 내렸는지(−) 올렸는지(+) 보여 줘요.",
)

@Composable
fun GlossaryDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        title = { Text("용어 설명") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GLOSSARY.forEach { (term, desc) ->
                    Text(term, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp))
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

/** 그래프 기준선 (점선). 예: 최소 수면 6시간, 기간 평균 */
data class RefLine(val value: Double, val label: String, val color: Color)

/** 그래프 배경 구간 띠. 예: 회복 점수 등급 구간 0~25 피로함 (11-18 개선 7) */
data class Zone(val from: Double, val to: Double, val label: String, val color: Color)

/** 회복 점수 그래프용 등급 구간 4개 (피로함 빨강 · 보통 주황 · 편안함 초록 · 가벼움 파랑) */
val GRADE_ZONES: List<Zone> = listOf(
    Zone(0.0, 25.0, Grade.TIRED.label, GradeTired),
    Zone(25.0, 50.0, Grade.NORMAL.label, GradeNormal),
    Zone(50.0, 75.0, Grade.COMFORT.label, GradeComfort),
    Zone(75.0, 100.0, Grade.LIGHT.label, GradeLight),
)

/**
 * 꺾은선 그래프 (HIS-01·02). 값이 없는 날은 선을 끊는다.
 * - band: 개인 기준선 범위(평균 ± 표준편차)를 옅은 띠로 표시
 * - refLines: 점선 기준선 (개선사항 3: 최소 수면 시간·평균)
 * - onSelect: 주어지면 그래프를 눌러 날짜를 고를 수 있다 (개선사항 5: 자세히 보기)
 * - zones: 배경 구간 띠, pointColor: 값마다 점 색을 다르게 (11-18 개선 7: 등급 색)
 */
@Composable
fun LineChart(
    values: List<Double?>,
    band: Pair<Double, Double>? = null,
    fixedRange: Pair<Double, Double>? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    refLines: List<RefLine> = emptyList(),
    height: Dp = 120.dp,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    zones: List<Zone> = emptyList(),
    pointColor: ((Double) -> Color)? = null,
) {
    val bandColor = color.copy(alpha = 0.12f)
    val axis = MaterialTheme.colorScheme.outlineVariant
    val present = values.filterNotNull() + listOfNotNull(band?.first, band?.second) + refLines.map { it.value }
    if (values.all { it == null }) {
        Text("아직 기록이 없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp))
        return
    }
    val lo = fixedRange?.first ?: (present.min() - 1)
    val hi = fixedRange?.second ?: (present.max() + 1)
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
    val currentOnSelect by rememberUpdatedState(onSelect)
    val tapModifier = if (onSelect != null && values.size > 1) Modifier.pointerInput(values.size) {
        detectTapGestures { pos ->
            val i = (pos.x / size.width * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
            currentOnSelect?.invoke(i)
        }
    } else Modifier
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween) {
            Text("%.0f".format(hi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.0f".format(lo), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.weight(1f).height(height).then(tapModifier).padding(vertical = 6.dp)) {
            fun x(i: Int) = if (values.size <= 1) size.width / 2 else size.width * i / (values.size - 1)
            fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
            for (k in 0..2) {
                val yy = size.height * k / 2
                drawLine(axis, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
            }
            zones.forEach { z ->
                val top = y(minOf(z.to, hi))
                val bottom = y(maxOf(z.from, lo))
                if (bottom > top) drawRect(z.color.copy(alpha = 0.08f), Offset(0f, top), Size(size.width, bottom - top))
            }
            band?.let { (a, b) -> drawRect(bandColor, Offset(0f, y(b)), Size(size.width, y(a) - y(b))) }
            refLines.forEach { r ->
                drawLine(r.color, Offset(0f, y(r.value)), Offset(size.width, y(r.value)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
            }
            selected?.takeIf { it in values.indices }?.let { i ->
                drawLine(axis, Offset(x(i), 0f), Offset(x(i), size.height), strokeWidth = 1.5.dp.toPx())
            }
            val path = Path()
            var drawing = false
            values.forEachIndexed { i, v ->
                if (v == null) { drawing = false; return@forEachIndexed }
                if (!drawing) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                drawing = true
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            values.forEachIndexed { i, v ->
                if (v != null) drawCircle(pointColor?.invoke(v) ?: color, radius = (if (i == selected) 6.0 else 4.0).dp.toPx(), center = Offset(x(i), y(v)))
            }
        }
    }
    if (zones.isNotEmpty()) {
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            zones.forEach { z ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(z.color))
                    Text(" ${z.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (refLines.isNotEmpty()) {
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            refLines.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(14.dp).height(2.dp).background(r.color))
                    Text(" ${r.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
