package com.triple.fcare.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.triple.fcare.data.RecoveryScoreEntity
import com.triple.fcare.engine.CapRule
import com.triple.fcare.engine.Grade
import com.triple.fcare.ui.theme.Background
import com.triple.fcare.ui.theme.CHART_ZONE_ALPHA
import com.triple.fcare.ui.theme.ChartGrid
import com.triple.fcare.ui.theme.ChartLoad
import com.triple.fcare.ui.theme.ChartUsual
import com.triple.fcare.ui.theme.Dim
import com.triple.fcare.ui.theme.FcText
import com.triple.fcare.ui.theme.FillTrack
import com.triple.fcare.ui.theme.GradeComfort
import com.triple.fcare.ui.theme.GradeLight
import com.triple.fcare.ui.theme.GradeNormal
import com.triple.fcare.ui.theme.GradeTired
import com.triple.fcare.ui.theme.Radius
import com.triple.fcare.ui.theme.Separator
import com.triple.fcare.ui.theme.Space
import com.triple.fcare.ui.theme.SurfaceWhite
import com.triple.fcare.ui.theme.TextPrimary
import com.triple.fcare.ui.theme.TextSecondary
import com.triple.fcare.ui.theme.TextTertiary
import com.triple.fcare.ui.theme.color
import com.triple.fcare.ui.theme.subtleColor
import com.triple.fcare.ui.theme.textColor
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
        "워치 없이 한 운동은 반영되지 않고 근력 운동처럼 심박이 적게 오르는 운동은 실제보다 적게 반영될 수 있어요."

/** 프로필 맨 아래에 하나만 두는 면책 문구 (11-20: 홈·분석에서 옮겨 합침) */
const val DISCLAIMER = "FCare는 의료기기가 아니며 회복 점수와 조언은 생활 습관 참고용이라 진단을 대신하지 않아요."

/* ---------- 카드 · 제목 ---------- */

/**
 * 흰 카드 (DESIGN.md: 그림자·테두리 0, 모서리 --radius-card 14).
 * 연회색 바탕 위에 흰 면으로만 구분한다. 카드 안에 또 카드를 넣지 않는다(Never).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    container: Color? = null,
    padding: Dp = Space.s4,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .clip(Radius.card)
            .background(container ?: SurfaceWhite)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

/** 제목이 있는 카드 (분석·기록·프로필) */
@Composable
fun InfoCard(title: String, content: @Composable () -> Unit) {
    AppCard {
        Text(title, style = FcText.lg, color = TextPrimary)
        Spacer(Modifier.height(Space.s2))
        content()
    }
}

/** 화면 큰 제목 (상단바 대신, --text-3xl). 화면 위 16 → 제목 → 16 → 첫 내용 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = FcText.x3l, color = TextPrimary,
        modifier = modifier.padding(top = Space.s4, bottom = Space.s2))
}

/** 섹션 제목 (--text-lg). 섹션 사이 간격은 화면에서 spacedBy로 준다. */
@Composable
fun SectionTitle(text: String) {
    Text(text, style = FcText.lg, color = TextPrimary, modifier = Modifier.padding(top = Space.s3))
}

@Composable
fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = FcText.sm, color = TextSecondary)
        Text(value, style = FcText.sm.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
    }
}

/** 카드 라벨 줄: 작은 아이콘 + 이름 (--text-sm, --text-secondary) */
@Composable
fun CardLabel(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Icon(it, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(Dim.iconSm))
            Spacer(Modifier.width(Space.s1 + 2.dp))
        }
        Text(text, style = FcText.sm.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 큰 숫자 + 작은 단위 ("8,420걸음", "7시간 12분"). parts = (숫자, 단위) 쌍 목록 */
@Composable
fun NumberWithUnits(parts: List<Pair<String, String>>, numberStyle: androidx.compose.ui.text.TextStyle = FcText.x2l) {
    Row(verticalAlignment = Alignment.Bottom) {
        parts.forEachIndexed { i, (num, unit) ->
            if (i > 0) Spacer(Modifier.width(Space.s1))
            Text(num, style = numberStyle, color = TextPrimary)
            if (unit.isNotEmpty()) Text(unit, style = FcText.sm, color = TextTertiary,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
    }
}

/**
 * 지표 타일 (반폭): 라벨 + 아이콘 / 큰 숫자 + 단위 / 보조 줄(최대 2줄).
 * accent는 옛 호출과 맞추려고 남긴 인자(색 띠는 없앰 — "색은 데이터에만").
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    caption: String? = null,
    @Suppress("UNUSED_PARAMETER") accent: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    parts: List<Pair<String, String>>? = null,
    caption2: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier.heightIn(min = Dim.tile).clip(Radius.card).background(SurfaceWhite)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Space.s3),
    ) {
        CardLabel(label, icon)
        Spacer(Modifier.height(Space.s1))
        if (parts != null) NumberWithUnits(parts) else Text(value, style = FcText.x2l, color = TextPrimary)
        Spacer(Modifier.height(Space.s1))
        caption?.let { Text(it, style = FcText.xs, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        caption2?.let { Text(it, style = FcText.xs, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** 타일 두 개를 한 줄에 (홈 블록 간격 8) */
@Composable
fun TileRow(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}

/* ---------- 칩 ---------- */

/**
 * 등급 칩: 옅은 바탕 + 등급 점 + 등급 이름(진한 글자). 색만으로 알리지 않고 이름 글자를 함께 둔다.
 * small = 목록용(24dp), 아니면 점수 카드용(28dp).
 */
@Composable
fun GradeChip(grade: Grade, small: Boolean = false) {
    Row(
        Modifier.height(if (small) Dim.chipSm else Dim.chip).clip(Radius.chip).background(grade.subtleColor())
            .padding(horizontal = if (small) Space.s1 + 2.dp else Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (small) Dim.dotSm else Dim.dot).clip(Radius.full).background(grade.color()))
        Spacer(Modifier.width(if (small) Space.s1 else Dim.chipGap))
        Text(grade.label, style = FcText.xs.copy(fontWeight = FontWeight.SemiBold), color = grade.textColor())
    }
}

/** 작은 상태 표시 칩 (허용됨 등). color는 글자색, 바탕은 그 색을 아주 옅게. */
@Composable
fun StatusLabel(text: String, color: Color = TextSecondary) {
    Text(
        text,
        style = FcText.xs.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier.clip(Radius.chip).background(color.copy(alpha = 0.10f))
            .padding(horizontal = Space.s2, vertical = Space.s1),
    )
}

/** 중립 상태 칩 (일부 데이터로 계산, 기준선 쌓는 중 …) */
@Composable
private fun NeutralChip(text: String) {
    Box(Modifier.height(Dim.chip).clip(Radius.chip).background(Background).padding(horizontal = Space.s2),
        contentAlignment = Alignment.Center) {
        Text(text, style = FcText.xs.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary, maxLines = 1)
    }
}

/**
 * 상태 칩 줄 — 1줄 고정. 다 안 들어가면 앞쪽 칩만 보이고 나머지는 "+n"(누를 수 없음).
 * 접근성 라벨은 "상태 n개 더 있음" (JOURNEY.md 홈 상태 겹침 규칙).
 */
@Composable
fun StatusChipRow(chips: List<String>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val gap = Dim.chipGap.roundToPx()
        val loose = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val all = chips.mapIndexed { i, t -> subcompose("c$i") { NeutralChip(t) }.first().measure(loose) }
        val maxW = constraints.maxWidth
        var shown = all.size
        fun widthOf(n: Int, more: Int): Int {
            var w = all.take(n).sumOf { it.width } + gap * (n - 1).coerceAtLeast(0)
            if (more > 0) w += gap + 48.dp.roundToPx()   // "+n" 자리
            return w
        }
        while (shown > 1 && widthOf(shown, all.size - shown) > maxW) shown--
        val more = all.size - shown
        val plus = if (more > 0) subcompose("more") {
            Box(Modifier.semantics { contentDescription = "상태 ${more}개 더 있음" }) { NeutralChip("+$more") }
        }.first().measure(loose) else null
        val h = all.maxOf { it.height }
        layout(maxW, h) {
            var x = 0
            all.take(shown).forEach { it.placeRelative(x, 0); x += it.width + gap }
            plus?.placeRelative(x, 0)
        }
    }
}

/* ---------- 막대 (Data display §1·§2, 사용자 결정 11-20) ---------- */

/**
 * 홈 회복 점수 막대 — 사용자 결정 11-20: 굵은 둥근 막대 하나 + 60 자리 평소 눈금과 "평소" 글자만.
 * 숫자 눈금·값 점·구간 틈 없음. 값은 큰 숫자와 등급 칩이 전한다.
 */
@Composable
fun ScoreBar(score: Double, grade: Grade?, modifier: Modifier = Modifier) {
    val fill = grade?.color() ?: TextSecondary
    val frac = (score / 100.0).toFloat().coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(Dim.marker)) {
            val barH = Dim.bar.toPx()
            val top = (size.height - barH) / 2
            val r = CornerRadius(barH / 2, barH / 2)
            drawRoundRect(FillTrack, Offset(0f, top), Size(size.width, barH), r)
            if (frac > 0f) drawRoundRect(fill, Offset(0f, top), Size(size.width * frac, barH), r)
            val x = size.width * 0.6f   // 평소 = 60점
            drawLine(ChartUsual, Offset(x, 0f), Offset(x, size.height), strokeWidth = Dim.hairline.toPx())
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Text("평소", style = FcText.xs.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary,
                modifier = Modifier.offset(x = maxWidth * 0.6f - 12.dp))
        }
    }
}

/**
 * 홈 운동 부하 비율 막대 — 사용자 결정 11-20: 글자는 낮음·적정·많음만. 숫자 눈금·끝 눈금 없음.
 * 축 0.5~2.0, 위치 p(v) = (clamp(v) − 0.5) / 1.5 → 적정 0.8~1.3 = 20.00~53.33%, 평소 1.0 = 33.33%.
 */
@Composable
fun LoadBar(ratio: Double?, modifier: Modifier = Modifier) {
    fun p(v: Double) = ((v.coerceIn(0.5, 2.0) - 0.5) / 1.5).toFloat()
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            val label = FcText.xs.copy(fontWeight = FontWeight.Normal)
            Text("낮음", style = label, color = TextTertiary, modifier = Modifier.align(Alignment.CenterStart))
            // "적정"은 평소 눈금과 겹치지 않게 눈금 오른쪽 띠 가운데(43.33%)에 둔다
            Text("적정", style = label.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary,
                modifier = Modifier.offset(x = maxWidth * 0.4333f - 12.dp))
            Text("많음", style = label, color = TextTertiary, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Canvas(Modifier.fillMaxWidth().height(Dim.marker)) {
            val barH = Dim.bar.toPx()
            val top = (size.height - barH) / 2
            val r = CornerRadius(barH / 2, barH / 2)
            drawRoundRect(FillTrack, Offset(0f, top), Size(size.width, barH), r)
            drawRect(Separator, Offset(size.width * p(0.8), top), Size(size.width * (p(1.3) - p(0.8)), barH))
            val ux = size.width * p(1.0)
            drawLine(ChartUsual, Offset(ux, 0f), Offset(ux, size.height), strokeWidth = Dim.hairline.toPx())
            if (ratio != null) {
                val c = Offset(size.width * p(ratio), size.height / 2)
                drawCircle(SurfaceWhite, radius = Dim.marker.toPx() / 2, center = c)
                drawCircle(ChartLoad, radius = Dim.marker.toPx() / 2 - Dim.stroke.toPx(), center = c)
            }
        }
    }
}

/* ---------- 목록 줄 (홈 최근 3일) ---------- */

/** 날짜 · (오늘) · 점수 · 등급 칩. 줄 전체가 누르는 영역(48dp). 점수가 없으면 status 글자와 "—". */
@Composable
fun ScoreListRow(date: String, isToday: Boolean, score: Double?, grade: Grade?, status: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable(onClick = onClick).padding(horizontal = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, style = FcText.sm, color = TextPrimary)
        if (isToday) Text("  오늘", style = FcText.xs, color = TextTertiary)
        Spacer(Modifier.weight(1f))
        if (score != null) {
            Text("${score.roundToInt()}", style = FcText.base.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = TextPrimary)
            Spacer(Modifier.width(Space.s3))
            Box(Modifier.widthIn(min = Dim.chipSlot), contentAlignment = Alignment.CenterEnd) {
                grade?.let { GradeChip(it, small = true) }
            }
        } else {
            Text(status ?: "기록 없음", style = FcText.xs, color = TextTertiary)
            Spacer(Modifier.width(Space.s3))
            Box(Modifier.widthIn(min = Dim.chipSlot), contentAlignment = Alignment.CenterEnd) {
                Text("—", style = FcText.base, color = TextTertiary)
            }
        }
    }
}

@Composable
fun ListDivider() = HorizontalDivider(thickness = Dim.hairline, color = Separator)

/* ---------- 선택 칩 · 대화상자 ---------- */

/** 칩 선택 (성별, 운동 빈도, 주간/월간). 각진 칩(--radius-chip), 높이 48dp 누르는 영역 */
@Composable
fun <T> ChipGroup(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        options.forEach { o ->
            val on = o == selected
            FilterChip(
                selected = on,
                onClick = { onSelect(o) },
                label = { Text(label(o), style = FcText.sm.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)) },
                shape = Radius.chip,
                modifier = Modifier.heightIn(min = Dim.touchMin),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SurfaceWhite, labelColor = TextSecondary,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = TextPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = on,
                    borderColor = TextTertiary, selectedBorderColor = MaterialTheme.colorScheme.primary,
                    borderWidth = Dim.hairline, selectedBorderWidth = Dim.stroke,
                ),
            )
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
        shape = Radius.sheet,
        containerColor = SurfaceWhite,
    )
}

/** 앱에서 쓰는 용어 설명 (프로필 → 도움말 → 용어 설명). JOURNEY.md 용어 설명 표 16개와 같은 문장. */
val GLOSSARY: List<Pair<String, String>> = listOf(
    "회복 점수" to "0~100점. 어젯밤 수면, 수면 중 최저 심박, 최근 운동 부하를 내 평소와 비교해 계산해요. 평소와 같은 날이 60점이고 높을수록 몸이 가벼운 상태예요.",
    "등급" to "점수 구간이에요. 피로함 0~25 · 보통 25~50 · 편안함 50~75 · 가벼움 75~100",
    "평소 (개인 기준선)" to "최근 28일 동안의 내 기록 평균이에요. 기록이 7일보다 적으면 일반 기준값과 섞어서 비교해요(기준선 쌓는 중).",
    "총 수면 시간" to "잠든 시간에서 중간에 깬 시간을 뺀 실제 수면 시간이에요. 최근 3일 평균이 6시간보다 적으면 평소와 상관없이 추가로 감점돼요.",
    "수면 효율" to "누워 있던 시간 중 실제로 잔 시간의 비율이에요. 워치가 수면 단계를 기록한 날만 계산해요.",
    "수면 규칙성" to "잠든 시각과 깬 시각이 평소와 얼마나 비슷한지예요. 평소와 많이 다르면 감점돼요.",
    "수면 중 최저 심박" to "잠자는 동안 가장 낮았던 심박이에요. 평소보다 높으면 피로나 스트레스가 덜 풀린 신호일 수 있어요.",
    "TRIMP" to "운동 한 번의 강도와 시간을 합친 운동량 점수예요. 워치가 잰 운동 중 심박으로 계산해요.",
    "ATL (최근 7일 부하)" to "최근 1주일 TRIMP의 평균이에요. 최근 날일수록 더 크게 반영해요.",
    "CTL (평소 부하)" to "최근 4주 TRIMP의 평균이에요. 내 평소 운동 부하를 뜻해요.",
    "운동 부하 비율" to "ATL ÷ CTL이에요. 1.0이면 평소와 같고 1.3을 넘으면 평소보다 운동 부하가 많이 늘어난 상태예요.",
    "운동 기준선 쌓는 중" to "워치를 찬 날이 14일보다 적으면 운동 부하를 약하게만 반영해요.",
    "점수 상한 (49점)" to "수면 중 심박 급상승, 운동 부하 급증, 심한 수면 부족 중 하나라도 있으면 다른 항목이 좋아도 49점을 넘지 않아요.",
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
                    Text(term, style = FcText.sm.copy(fontWeight = FontWeight.Bold), color = TextPrimary,
                        modifier = Modifier.padding(top = Space.s3))
                    Text(desc, style = FcText.sm, color = TextSecondary)
                }
            }
        },
        shape = Radius.sheet,
        containerColor = SurfaceWhite,
    )
}

/* ---------- 꺾은선 그래프 (기록 탭, Data display §3) ---------- */

/** 그래프 기준선. dotted = 점선(평균·평소), 아니면 긴 대시(최소 6시간) — 색이 아니라 모양으로도 구분 */
data class RefLine(val value: Double, val label: String, val color: Color, val dotted: Boolean = false)

/** 그래프 배경 구간 띠. 예: 회복 점수 등급 구간 */
data class Zone(val from: Double, val to: Double, val label: String, val color: Color, val textColor: Color = color)

/** 회복 점수 그래프용 등급 구간 4개 (띠는 등급색 × 0.08, 이름은 등급 글자색) */
val GRADE_ZONES: List<Zone> = listOf(
    Zone(0.0, 25.0, Grade.TIRED.label, GradeTired, Grade.TIRED.textColor()),
    Zone(25.0, 50.0, Grade.NORMAL.label, GradeNormal, Grade.NORMAL.textColor()),
    Zone(50.0, 75.0, Grade.COMFORT.label, GradeComfort, Grade.COMFORT.textColor()),
    Zone(75.0, 100.0, Grade.LIGHT.label, GradeLight, Grade.LIGHT.textColor()),
)

/**
 * 꺾은선 그래프 (HIS-01·02). 값이 없는 날은 선을 끊는다.
 * - unit: 그래프 위 "단위 ○ · ○부터" 줄 (축이 0에서 시작하지 않을 때 범위를 밝힘)
 * - band: 개인 기준선 범위(평균 ± 표준편차) 옅은 띠
 * - refLines: 기준선(최소 수면 6시간은 긴 대시, 평균·평소는 점선)
 * - zones: 등급 구간 띠 + 경계선(--chart-score-line) + 오른쪽 구간 이름
 * - onSelect: 그래프를 눌러 날짜 고르기 (자세히 보기)
 */
@Composable
fun LineChart(
    values: List<Double?>,
    band: Pair<Double, Double>? = null,
    fixedRange: Pair<Double, Double>? = null,
    color: Color = TextSecondary,
    refLines: List<RefLine> = emptyList(),
    height: Dp = 120.dp,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    zones: List<Zone> = emptyList(),
    pointColor: ((Double) -> Color)? = null,
    unit: String? = null,
) {
    if (values.all { it == null }) {
        Text("아직 기록이 없어요", style = FcText.xs, color = TextTertiary, modifier = Modifier.padding(vertical = Space.s6))
        return
    }
    val present = values.filterNotNull() + listOfNotNull(band?.first, band?.second) + refLines.map { it.value }
    val lo = fixedRange?.first ?: (present.min() - 1)
    val hi = fixedRange?.second ?: (present.max() + 1)
    val longDash = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))
    val dots = PathEffect.dashPathEffect(floatArrayOf(3f, 6f))
    val currentOnSelect by rememberUpdatedState(onSelect)
    val tapModifier = if (onSelect != null && values.size > 1) Modifier.pointerInput(values.size) {
        detectTapGestures { pos ->
            val i = (pos.x / size.width * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
            currentOnSelect?.invoke(i)
        }
    } else Modifier
    unit?.let { Text(it, style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextTertiary,
        modifier = Modifier.padding(bottom = Space.s1)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween) {
            Text("%.0f".format(hi), style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextTertiary)
            Text("%.0f".format(lo), style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextTertiary)
        }
        Spacer(Modifier.width(Space.s2))
        Canvas(Modifier.weight(1f).height(height).then(tapModifier).padding(vertical = Space.s2)) {
            fun x(i: Int) = if (values.size <= 1) size.width / 2 else size.width * i / (values.size - 1)
            fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
            // 장식 격자(위·가운데·아래). 등급 구간이 있으면 구간 경계가 격자를 대신한다.
            if (zones.isEmpty()) for (k in 0..2) {
                val yy = size.height * k / 2
                drawLine(ChartGrid, Offset(0f, yy), Offset(size.width, yy), strokeWidth = Dim.hairline.toPx())
            }
            zones.forEach { z ->
                val top = y(minOf(z.to, hi))
                val bottom = y(maxOf(z.from, lo))
                if (bottom > top) drawRect(z.color.copy(alpha = CHART_ZONE_ALPHA), Offset(0f, top), Size(size.width, bottom - top))
                // 구간 경계선은 3:1 이상 색으로 (Data display §5)
                drawLine(com.triple.fcare.ui.theme.ChartScoreLine, Offset(0f, bottom), Offset(size.width, bottom),
                    strokeWidth = Dim.hairline.toPx())
            }
            band?.let { (a, b) -> drawRect(color.copy(alpha = 0.10f), Offset(0f, y(b)), Size(size.width, y(a) - y(b))) }
            refLines.forEach { r ->
                drawLine(r.color, Offset(0f, y(r.value)), Offset(size.width, y(r.value)),
                    strokeWidth = (if (r.dotted) Dim.stroke else 1.5.dp).toPx(),
                    pathEffect = if (r.dotted) dots else longDash, cap = if (r.dotted) StrokeCap.Round else StrokeCap.Butt)
            }
            selected?.takeIf { it in values.indices }?.let { i ->
                drawLine(TextTertiary, Offset(x(i), 0f), Offset(x(i), size.height), strokeWidth = Dim.hairline.toPx())
            }
            val path = Path()
            var drawing = false
            values.forEachIndexed { i, v ->
                if (v == null) { drawing = false; return@forEachIndexed }
                if (!drawing) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                drawing = true
            }
            drawPath(path, color, style = Stroke(width = Dim.stroke.toPx(), cap = StrokeCap.Round))
            values.forEachIndexed { i, v ->
                if (v != null) {
                    val rr = (if (i == selected) 6.0 else 4.0).dp.toPx()
                    drawCircle(pointColor?.invoke(v) ?: color, radius = rr, center = Offset(x(i), y(v)))
                    if (pointColor != null)   // 등급색 점은 윤곽으로 3:1 확보
                        drawCircle(color, radius = rr, center = Offset(x(i), y(v)), style = Stroke(Dim.hairline.toPx()))
                }
            }
        }
    }
    if (zones.isNotEmpty()) {
        Row(Modifier.padding(top = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            zones.forEach { z ->
                Text("${z.label} ${z.from.roundToInt()}~${z.to.roundToInt()}", style = FcText.xs, color = z.textColor)
            }
        }
    }
    if (refLines.isNotEmpty()) {
        Row(Modifier.padding(top = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            refLines.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.width(16.dp).height(Dim.stroke)) {
                        drawLine(r.color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                            strokeWidth = size.height,
                            pathEffect = if (r.dotted) PathEffect.dashPathEffect(floatArrayOf(3f, 4f)) else null)
                    }
                    Text(" ${r.label}", style = FcText.xs, color = TextSecondary)
                }
            }
        }
    }
}
