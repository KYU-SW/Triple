package com.triple.fcare.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.triple.fcare.engine.Grade
import com.triple.fcare.engine.Params
import com.triple.fcare.engine.ScoreStatus
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.runtime.LaunchedEffect
import com.triple.fcare.ui.theme.ChartHr
import com.triple.fcare.ui.theme.ChartLoad
import com.triple.fcare.ui.theme.ChartLoadText
import com.triple.fcare.ui.theme.ChartRefMin
import com.triple.fcare.ui.theme.ChartScoreLine
import com.triple.fcare.ui.theme.ChartSleep
import com.triple.fcare.ui.theme.ChartUsual
import com.triple.fcare.ui.theme.Dim
import com.triple.fcare.ui.theme.FcText
import com.triple.fcare.ui.theme.Radius
import com.triple.fcare.ui.theme.Separator
import com.triple.fcare.ui.theme.Space
import com.triple.fcare.ui.theme.TextPrimary
import com.triple.fcare.ui.theme.TextSecondary
import com.triple.fcare.ui.theme.TextTertiary
import com.triple.fcare.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val MD = DateTimeFormatter.ofPattern("M/d")
private val MDE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

/** 기록 화면의 그래프 종류 (개선사항 5: 누르면 자세히 보기) */
private enum class ChartKind(val title: String, val term: String?) {
    SCORE("회복 점수", null), SLEEP("총 수면 시간", null), HR("수면 중 최저 심박", null), ATL("최근 7일 운동 부하", "ATL"),
}

/** 그래프 위 "단위 ○ · 범위" 줄 (Data display §3: 축이 0에서 시작하지 않으면 범위를 밝힘) */
private fun ChartKind.unit(): String = when (this) {
    ChartKind.SCORE -> "단위 점 · 0~100"
    ChartKind.SLEEP -> "단위 시간 · 4시간부터"
    ChartKind.HR -> "단위 bpm"
    ChartKind.ATL -> "단위 TRIMP · 0부터"
}

/** 고정 축 범위 (주간·월간이 같은 축이라 비교 가능) */
private fun ChartKind.range(d: HistoryData): Pair<Double, Double>? = when (this) {
    ChartKind.SCORE -> 0.0 to 100.0
    ChartKind.SLEEP -> 4.0 to 10.0
    ChartKind.ATL -> 0.0 to ((d.atl.filterNotNull().maxOrNull() ?: 10.0) * 1.2).coerceAtLeast(10.0)
    ChartKind.HR -> d.minHr.filterNotNull().takeIf { it.isNotEmpty() }?.let { v ->
        val lo = kotlin.math.floor(v.min() / 5) * 5
        val hi = maxOf(kotlin.math.ceil(v.max() / 5) * 5, lo + 20)
        lo to hi
    }
}

private fun ChartKind.series(d: HistoryData): List<Double?> = when (this) {
    ChartKind.SCORE -> d.scores
    ChartKind.SLEEP -> d.sleepHours
    ChartKind.HR -> d.minHr
    ChartKind.ATL -> d.atl
}

private fun ChartKind.fmt(v: Double): String = when (this) {
    ChartKind.SCORE -> "${v.roundToInt()}점"
    ChartKind.SLEEP -> hoursMinutes(v * 60)
    ChartKind.HR -> "${v.roundToInt()} bpm"
    ChartKind.ATL -> "%.1f".format(v)
}

/** 개선사항 3: 수면 그래프에 최소 수면 시간(6시간)과 기간 평균을 점선으로 표시 */
@Composable
private fun ChartKind.refLines(d: HistoryData): List<RefLine> = when (this) {
    // 최소 6시간 = 긴 대시(적갈색), 평균 = 점선(먹색) — 색과 모양 둘 다로 구분
    ChartKind.SLEEP -> listOfNotNull(
        RefLine(Params.MIN_SLEEP_MIN / 60, "최소 ${(Params.MIN_SLEEP_MIN / 60).roundToInt()}시간", ChartRefMin),
        d.sleepHours.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.let { RefLine(it, "평균", ChartUsual, dotted = true) },
    )
    ChartKind.SCORE -> listOf(RefLine(60.0, "평소 60", ChartUsual, dotted = true))   // 평소선 (signature)
    else -> listOfNotNull(series(d).filterNotNull().takeIf { it.isNotEmpty() }?.average()?.let { RefLine(it, "평균", ChartUsual, dotted = true) })
}

/** 그래프 색 (11-20 새 팔레트) — 점수 선은 회색, 점은 등급색 */
private fun ChartKind.color() = when (this) {
    ChartKind.SCORE -> ChartScoreLine
    ChartKind.SLEEP -> ChartSleep
    ChartKind.HR -> ChartHr
    ChartKind.ATL -> ChartLoad
}

/** 11-18 개선 7: 회복 점수 그래프는 등급 구간 띠 + 등급 색 점 */
private fun ChartKind.zones(): List<Zone> = if (this == ChartKind.SCORE) GRADE_ZONES else emptyList()
private fun ChartKind.pointColor(): ((Double) -> Color)? =
    if (this == ChartKind.SCORE) ({ v: Double -> Grade.of(v).color() }) else null

/** 평소 범위 띠는 그리지 않는다(11-20 문구: 옅은 띠 = 등급 구간만). 평균은 점선으로 보인다. */
@Suppress("UNUSED_PARAMETER")
private fun ChartKind.band(d: HistoryData): Pair<Double, Double>? = null

private fun ChartKind.summary(d: HistoryData): String? {
    if (this == ChartKind.ATL) return null
    val avg = series(d).filterNotNull().takeIf { it.isNotEmpty() }?.average() ?: return null
    return "평균 " + fmt(avg)
}

/** 3.1.7 기록 (HIS-01~03). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, modifier: Modifier = Modifier, initialDate: LocalDate? = null) {
    var days by remember { mutableStateOf(7) }
    val data by produceState<HistoryData?>(null, days) { value = vm.history(days) }
    val period by produceState<PeriodSummary?>(null, days) { value = vm.periodSummary(days) }
    var showPicker by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<LocalDate?>(initialDate) }
    LaunchedEffect(initialDate) { if (initialDate != null) picked = initialDate }   // 홈 최근 3일 줄에서 온 날짜
    val detail by produceState<DayDetail?>(null, picked) { value = picked?.let { vm.dayDetail(it) } }
    var expanded by remember { mutableStateOf<ChartKind?>(null) }
    BackHandler(enabled = expanded != null) { expanded = null }
    val scroll = rememberScrollState()

    Column(
        modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = Space.s4).padding(bottom = Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        ScreenTitle("기록")
        // 홈에서 날짜를 골라 들어왔으면 그 날 기록을 맨 위에 먼저 보여 준다
        if (initialDate != null && expanded == null) detail?.let { DayDetailCard(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipGroup(listOf(7, 30), days, { if (it == 7) "주간" else "월간" }) { days = it }
        }

        val d = data
        val k = expanded
        if (d != null && k != null) {
            // 개선사항 5: 그래프 자세히 보기
            ChartDetail(k, d) { expanded = null }
        } else {
            d?.let {
                Text("${it.dates.first().format(MD)} ~ ${it.dates.last().format(MD)}", style = FcText.sm, color = TextSecondary)
            }
            // 개선사항 7: 주/월 요약
            period?.let { SummaryCard(it, days) }
            d?.let {
                ChartKind.entries.forEach { kind ->
                    ChartCard(kind, kind.summary(it), onClick = { expanded = kind }) {
                        LineChart(kind.series(it), band = kind.band(it), color = kind.color(), refLines = kind.refLines(it),
                            zones = kind.zones(), pointColor = kind.pointColor(), fixedRange = kind.range(it), unit = kind.unit())
                    }
                }
                Text("회복 점수 그래프의 옅은 색 띠는 등급 구간이에요. 그래프를 누르면 날짜별 값을 볼 수 있어요.",
                    style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextSecondary)
            }

            // HIS-03 날짜 선택 조회
            SectionTitle("날짜별 기록")
            OutlinedButton(onClick = { showPicker = true }, shape = Radius.control,
                modifier = Modifier.fillMaxWidth().height(Dim.button)) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = TextSecondary)
                Text("  " + (picked?.format(MDE) ?: "날짜 선택"), style = FcText.base, color = TextPrimary)
            }
            if (initialDate == null) detail?.let { DayDetailCard(it) }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showPicker = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("취소") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun ChartCard(kind: ChartKind, summary: String?, onClick: () -> Unit, chart: @Composable () -> Unit) {
    AppCard(onClick = onClick) {
        ChartHeader(kind, summary)
        Spacer(Modifier.height(Space.s2))
        chart()
        Text("눌러서 자세히 보기 ›", style = FcText.xs, color = TextTertiary,
            modifier = Modifier.fillMaxWidth().padding(top = Space.s1), textAlign = TextAlign.End)
    }
}

/** 그래프 카드 머리: 쉬운 이름(+ 보조 용어) · 평균 값 */
@Composable
private fun ChartHeader(kind: ChartKind, summary: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(kind.title, style = FcText.base.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
        kind.term?.let { Text(" $it", style = FcText.xs, color = TextTertiary) }
        Spacer(Modifier.weight(1f))
        summary?.let { Text(it, style = FcText.sm.copy(fontWeight = FontWeight.SemiBold),
            color = if (kind == ChartKind.ATL) ChartLoadText else TextSecondary) }
    }
}

/** 개선사항 5: 그래프 자세히 보기 — 큰 그래프 + 누른 날짜의 값 + 날짜별 목록 */
@Composable
private fun ChartDetail(kind: ChartKind, d: HistoryData, onBack: () -> Unit) {
    val values = kind.series(d)
    var selected by remember(kind, d) { mutableStateOf(values.indexOfLast { it != null }.takeIf { it >= 0 }) }
    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Dim.touchMin)) { Text("‹ 기록으로 돌아가기") }
    AppCard {
        ChartHeader(kind, kind.summary(d))
        Spacer(Modifier.height(Space.s2))
        LineChart(values, band = kind.band(d), color = kind.color(), refLines = kind.refLines(d), zones = kind.zones(),
            pointColor = kind.pointColor(), fixedRange = kind.range(d), unit = kind.unit(),
            height = 240.dp, selected = selected, onSelect = { selected = it })
        selected?.let { i ->
            Text("${d.dates[i].format(MDE)}  ·  ${values[i]?.let { kind.fmt(it) } ?: "기록 없음"}",
                style = FcText.base.copy(fontWeight = FontWeight.Bold), color = TextPrimary, modifier = Modifier.padding(top = Space.s2))
        }
        Text("그래프를 눌러 날짜를 고르세요", style = FcText.xs, color = TextTertiary)
    }
    AppCard {
        Text("날짜별 값", style = FcText.base.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
        d.dates.indices.reversed().forEach { i ->
            LabeledRow(d.dates[i].format(MDE), values[i]?.let { kind.fmt(it) } ?: "-")
        }
    }
}

/** 개선사항 7: 주/월 요약 (직전 같은 기간과 비교) */
@Composable
private fun SummaryCard(p: PeriodSummary, days: Int) {
    val c = p.current
    val prev = p.previous
    AppCard {
        Text(if (days == 7) "최근 7일 요약" else "최근 30일 요약", style = FcText.lg, color = TextPrimary)
        Text("직전 ${days}일(${prev.from.format(MD)} ~ ${prev.to.format(MD)})과 비교", style = FcText.xs, color = TextTertiary)
        HorizontalDivider(Modifier.padding(vertical = Space.s2), thickness = Dim.hairline, color = Separator)
        LabeledRow("평균 회복 점수", c.avgScore?.let { a ->
            "${a.roundToInt()}점" + (prev.avgScore?.let { " (${signed(a - it)})" } ?: "")
        } ?: "-")
        LabeledRow("점수가 나온 날", "${c.scoredDays} / ${days}일")
        LabeledRow("평균 수면", c.avgSleepMin?.let { a ->
            hoursMinutes(a) + (prev.avgSleepMin?.let { " (${signed(a - it)}분)" } ?: "")
        } ?: "-")
        LabeledRow("평균 수면 중 최저 심박", c.avgMinHr?.let { "${it.roundToInt()} bpm" } ?: "-")
        LabeledRow("운동", "${c.exerciseCount}회 · 운동량 합 %.0f".format(c.trimpSum))
        LabeledRow("가장 잦은 감점 요인", c.worstFactor?.let { "${it.label} (${c.worstFactorDays}일)" } ?: "없음")
    }
}

@Composable
private fun DayDetailCard(d: DayDetail) {
    AppCard {
        Text(d.date.format(MDE), style = FcText.lg, color = TextPrimary)
        val s = d.score
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            when {
                s == null -> Text("회복 점수 기록 없음", style = FcText.sm, color = TextSecondary)
                s.status == ScoreStatus.NO_DATA.name || s.score == null -> Text("데이터 부족", style = FcText.sm, color = TextSecondary)
                else -> {
                    NumberWithUnits(listOf("${s.score.roundToInt()}" to "점"))
                    Spacer(Modifier.width(Space.s2))
                    gradeOf(s)?.let { GradeChip(it) }
                }
            }
        }
        s?.takeIf { it.score != null }?.let { Text(messageOf(it), style = FcText.sm, color = TextPrimary) }
        HorizontalDivider(Modifier.padding(vertical = Space.s2), thickness = Dim.hairline, color = Separator)
        d.sleep?.let {
            LabeledRow("총 수면 시간", hoursMinutes(it.tstMin))
            LabeledRow("수면 효율", it.efficiency?.let { e -> "${(e * 100).roundToInt()}%" } ?: "-")
            LabeledRow("잠든 · 깬 시각", "${clockFrom18(it.onsetMin)} · ${clockFrom18(it.wakeMin)}")
        } ?: LabeledRow("수면", "기록 없음")
        LabeledRow("수면 중 최저 심박", d.hr?.minHr?.let { "${it.roundToInt()} bpm" } ?: "-")
        d.load?.let { LabeledRow("전날 운동 부하 비율", "%.2f".format(it.acwr)) }
        // EXE-12 날짜별 운동 목록 (전날 운동)
        HorizontalDivider(Modifier.padding(vertical = Space.s2), thickness = Dim.hairline, color = Separator)
        Text("전날 운동", style = FcText.sm.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
        if (d.exercises.isEmpty()) Text("운동 기록 없음", style = FcText.sm, color = TextSecondary)
        d.exercises.forEach { ex ->
            LabeledRow(
                "${exerciseName(ex.exerciseType)} · ${ex.durationMin.roundToInt()}분",
                ex.trimp?.let { "운동량 %.0f".format(it) } ?: "심박 없음",   // EXE-11
            )
        }
    }
}
