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
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
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

/** 그래프 기준선 (점선). 예: 최소 수면 6시간, 기간 평균 */
data class RefLine(val value: Double, val label: String, val color: Color)

/**
 * 꺾은선 그래프 (HIS-01·02). 값이 없는 날은 선을 끊는다.
 * - band: 개인 기준선 범위(평균 ± 표준편차)를 옅은 띠로 표시
 * - refLines: 점선 기준선 (개선사항 3: 최소 수면 시간·평균)
 * - onSelect: 주어지면 그래프를 눌러 날짜를 고를 수 있다 (개선사항 5: 자세히 보기)
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
                if (v != null) drawCircle(color, radius = (if (i == selected) 6.0 else 3.5).dp.toPx(), center = Offset(x(i), y(v)))
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
