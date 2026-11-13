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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.triple.fcare.engine.Grade
import com.triple.fcare.engine.Params
import com.triple.fcare.engine.ScoreStatus
import com.triple.fcare.ui.theme.GradeTired
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
private enum class ChartKind(val title: String) {
    SCORE("회복 점수"), SLEEP("총 수면 시간 (시간)"), HR("수면 중 최저 심박 (bpm)"), ATL("최근 7일 운동 부하 (ATL)"),
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
    ChartKind.SLEEP -> listOfNotNull(
        RefLine(Params.MIN_SLEEP_MIN / 60, "최소 ${(Params.MIN_SLEEP_MIN / 60).roundToInt()}시간", GradeTired),
        d.sleepHours.filterNotNull().takeIf { it.isNotEmpty() }?.average()
            ?.let { RefLine(it, "기간 평균", MaterialTheme.colorScheme.secondary) },
    )
    else -> emptyList()
}

@Composable
private fun ChartKind.color() = when (this) {
    ChartKind.SCORE, ChartKind.SLEEP -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.secondary
}

private fun ChartKind.band(d: HistoryData) = when (this) {
    ChartKind.SLEEP -> d.sleepBand
    ChartKind.HR -> d.hrBand
    else -> null
}

private fun ChartKind.summary(d: HistoryData): String? {
    if (this == ChartKind.ATL) return null
    val avg = series(d).filterNotNull().takeIf { it.isNotEmpty() }?.average() ?: return null
    return "평균 " + fmt(avg)
}

/** 3.1.7 기록 (HIS-01~03). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    var days by remember { mutableStateOf(7) }
    val data by produceState<HistoryData?>(null, days) { value = vm.history(days) }
    val period by produceState<PeriodSummary?>(null, days) { value = vm.periodSummary(days) }
    var showPicker by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val detail by produceState<DayDetail?>(null, picked) { value = picked?.let { vm.dayDetail(it) } }
    var expanded by remember { mutableStateOf<ChartKind?>(null) }
    BackHandler(enabled = expanded != null) { expanded = null }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                Text("${it.dates.first().format(MD)} ~ ${it.dates.last().format(MD)}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 개선사항 7: 주/월 요약
            period?.let { SummaryCard(it, days) }
            d?.let {
                ChartKind.entries.forEach { kind ->
                    ChartCard(kind.title, kind.summary(it), onClick = { expanded = kind }) {
                        LineChart(kind.series(it), band = kind.band(it), color = kind.color(), refLines = kind.refLines(it),
                            fixedRange = if (kind == ChartKind.SCORE) 0.0 to 100.0 else null)
                    }
                }
                Text("옅은 띠는 내 평소 범위(최근 28일 평균 ± 표준편차)예요. 그래프를 누르면 날짜별 값을 볼 수 있어요.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // HIS-03 날짜 선택 조회
            SectionTitle("날짜별 기록")
            OutlinedButton(onClick = { showPicker = true }, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Text("  " + (picked?.toString() ?: "날짜 선택"))
            }
            detail?.let { DayDetailCard(it) }
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
private fun ChartCard(title: String, summary: String?, onClick: () -> Unit, chart: @Composable () -> Unit) {
    AppCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            summary?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
        chart()
        Text("눌러서 자세히 보기 ›", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.End)
    }
}

/** 개선사항 5: 그래프 자세히 보기 — 큰 그래프 + 누른 날짜의 값 + 날짜별 목록 */
@Composable
private fun ChartDetail(kind: ChartKind, d: HistoryData, onBack: () -> Unit) {
    val values = kind.series(d)
    var selected by remember(kind, d) { mutableStateOf(values.indexOfLast { it != null }.takeIf { it >= 0 }) }
    TextButton(onClick = onBack) { Text("‹ 기록으로 돌아가기") }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kind.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            kind.summary(d)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
        LineChart(values, band = kind.band(d), color = kind.color(), refLines = kind.refLines(d),
            fixedRange = if (kind == ChartKind.SCORE) 0.0 to 100.0 else null,
            height = 240.dp, selected = selected, onSelect = { selected = it })
        selected?.let { i ->
            Text("${d.dates[i].format(MDE)}  ·  ${values[i]?.let { kind.fmt(it) } ?: "기록 없음"}",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        }
        Text("그래프를 눌러 날짜를 고르세요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AppCard {
        Text("날짜별 값", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
        Text(if (days == 7) "최근 7일 요약" else "최근 30일 요약", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("직전 ${days}일(${prev.from.format(MD)} ~ ${prev.to.format(MD)})과 비교", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LabeledRow("평균 회복 점수", c.avgScore?.let { a ->
            "${a.roundToInt()}점" + (prev.avgScore?.let { " (${signed(a - it)})" } ?: "")
        } ?: "-")
        LabeledRow("점수가 나온 날", "${c.scoredDays} / ${days}일")
        LabeledRow("평균 수면", c.avgSleepMin?.let { a ->
            hoursMinutes(a) + (prev.avgSleepMin?.let { " (${signed(a - it)}분)" } ?: "")
        } ?: "-")
        LabeledRow("평균 수면 중 최저 심박", c.avgMinHr?.let { "${it.roundToInt()} bpm" } ?: "-")
        LabeledRow("운동", "${c.exerciseCount}회 · TRIMP 합 %.0f".format(c.trimpSum))
        LabeledRow("가장 잦은 감점 요인", c.worstFactor?.let { "${it.label} (${c.worstFactorDays}일)" } ?: "없음")
    }
}

@Composable
private fun DayDetailCard(d: DayDetail) {
    AppCard {
        Text(d.date.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val s = d.score
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                s == null -> Text("회복 점수 기록 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.status == ScoreStatus.NO_DATA.name || s.score == null -> Text("데이터 부족", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val g = gradeOf(s)
                    Text("${s.score.roundToInt()}점  ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    g?.let { StatusLabel(it.label, it.color()) }
                }
            }
        }
        s?.takeIf { it.score != null }?.let { Text(messageOf(it), style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        d.sleep?.let {
            LabeledRow("총 수면 시간", hoursMinutes(it.tstMin))
            LabeledRow("수면 효율", it.efficiency?.let { e -> "${(e * 100).roundToInt()}%" } ?: "-")
            LabeledRow("잠든 · 깬 시각", "${clockFrom18(it.onsetMin)} · ${clockFrom18(it.wakeMin)}")
        } ?: LabeledRow("수면", "기록 없음")
        LabeledRow("수면 중 최저 심박", d.hr?.minHr?.let { "${it.roundToInt()} bpm" } ?: "-")
        d.load?.let { LabeledRow("전날 운동 부하 비율", "%.2f".format(it.acwr)) }
        // EXE-12 날짜별 운동 목록 (전날 운동)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("전날 운동", style = MaterialTheme.typography.labelLarge)
        if (d.exercises.isEmpty()) Text("운동 기록 없음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        d.exercises.forEach { ex ->
            LabeledRow(
                "${exerciseName(ex.exerciseType)} · ${ex.durationMin.roundToInt()}분",
                ex.trimp?.let { "TRIMP %.0f".format(it) } ?: "심박 없음",   // EXE-11
            )
        }
    }
}
