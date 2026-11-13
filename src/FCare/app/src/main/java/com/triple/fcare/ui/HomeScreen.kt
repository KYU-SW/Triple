package com.triple.fcare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.triple.fcare.data.ProfileEntity
import com.triple.fcare.data.RecoveryScoreEntity
import com.triple.fcare.engine.Params
import com.triple.fcare.engine.ScoreStatus
import com.triple.fcare.hc.HcPermissions
import com.triple.fcare.ui.theme.GradeNormal
import com.triple.fcare.ui.theme.GradeTired
import com.triple.fcare.ui.theme.color
import com.triple.fcare.work.TodayState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 3.1.4 홈 (HOM-01~10). [다시 불러오기]는 상단바 버튼(FCareApp)에서 vm.refresh()를 부른다. */
@Composable
fun HomeScreen(vm: MainViewModel, profile: ProfileEntity, modifier: Modifier = Modifier, onOpenFeedback: () -> Unit = {}) {
    val today by vm.today.collectAsState()
    val detail by vm.todayDetail.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }   // 앱 실행·홈 진입 시 즉시 확인 (기본설계서 3.3)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("${profile.nickname}님, 오늘 몸 상태는 어떨까요?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // ---- 회복 점수 (HOM-01, 03, 06, 07, 09) ----
        ScoreHero(vm, today, detail?.baseline?.nNights, onOpenFeedback)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        // ---- 어젯밤 데이터 요약 (HOM-02) ----
        detail?.sleep?.let { sleep ->
            SectionTitle("어젯밤 수면")
            val hr = detail?.hr?.minHr
            val mean = detail?.baseline?.hrMean
            TileRow(
                { m -> MetricTile("총 수면 시간", hoursMinutes(sleep.tstMin), modifier = m) },
                { m -> MetricTile("수면 효율", sleep.efficiency?.let { "${(it * 100).roundToInt()}%" } ?: "-",
                    caption = if (sleep.efficiency == null) "수면 단계 기록 없음" else null, modifier = m) },
            )
            TileRow(
                { m -> MetricTile("잠든 · 깬 시각", "${clockFrom18(sleep.onsetMin)} · ${clockFrom18(sleep.wakeMin)}", modifier = m) },
                { m -> MetricTile("최저 심박", hr?.let { "${it.roundToInt()} bpm" } ?: "-",
                    caption = if (hr != null && mean != null) "평소 대비 ${signed(hr - mean)}" else null,
                    accent = MaterialTheme.colorScheme.secondary, modifier = m) },
            )
        }

        // ---- 운동 부하 요약 (HOM-08) ----
        SectionTitle("운동 부하")
        val load = detail?.load
        if (load == null) {
            AppCard {
                Text("운동 부하를 계산하려면 심박·운동 권한이 필요해요.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            TileRow(
                { m -> MetricTile("어제 운동 강도", "%.0f".format(load.trimpSum), caption = "TRIMP", accent = MaterialTheme.colorScheme.secondary, modifier = m) },
                { m -> MetricTile("최근 7일 부하", "%.1f".format(load.atl), caption = "ATL", accent = MaterialTheme.colorScheme.secondary, modifier = m) },
            )
            AppCard {
                val ratio = load.acwr
                val ratioColor = when {
                    ratio >= Params.C2_ACWR -> GradeTired
                    ratio > Params.ACWR_NORMAL_HIGH -> GradeNormal
                    else -> MaterialTheme.colorScheme.primary
                }
                LabeledRow("최근 운동 부하 비율", "%.2f".format(ratio))
                LinearProgressIndicator(
                    progress = { (ratio / 2.0).toFloat().coerceIn(0f, 1f) },
                    color = ratioColor,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                Text("평소 = 1.0 · 1.3을 넘으면 평소보다 많이 운동한 상태예요", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val noHr = detail?.exercises?.count { it.trimp == null } ?: 0
                if (noHr > 0) Text("심박이 없어 반영하지 못한 운동 ${noHr}건", style = MaterialTheme.typography.labelSmall)
            }
        }

        Text("FCare는 의료기기가 아니며, 회복 점수는 진단을 대신하지 않아요.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
    }

    if (!profile.guideSeen) GuideDialog { vm.markGuideSeen() }   // HOM-10: 최초 사용 시 안내
}

/** 원형 게이지 카드 */
@Composable
private fun ScoreHero(vm: MainViewModel, today: TodayState?, baselineNights: Int?, onOpenFeedback: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refresh() }

    AppCard(container = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("오늘의 회복 점수", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (today) {
                null, TodayState.Skipped, TodayState.NoProfile -> CircularProgressIndicator(Modifier.padding(48.dp))
                is TodayState.Done -> ScoreBody(today.score, baselineNights, onOpenFeedback)
                is TodayState.Waiting -> {
                    ScoreGauge(null, null, centerText = "수면 기록\n기다리는 중")
                    Text("워치가 어젯밤 수면을 아직 보내지 않았어요.", textAlign = TextAlign.Center)
                    today.previous?.let { p ->
                        p.score?.let { Text("직전 점수 ${it.roundToInt()}점 (${p.date})", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text("12:00까지 들어오지 않으면 오늘은 \"데이터 부족\"으로 표시돼요.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                TodayState.NeedPermission -> {
                    ScoreGauge(null, null, centerText = "권한 필요")
                    Text("수면 권한이 없어 회복 점수를 계산할 수 없어요.", textAlign = TextAlign.Center)
                    Button({ launcher.launch(HcPermissions.ALL) }, Modifier.padding(top = 8.dp)) { Text("권한 허용하기") }
                }
                TodayState.HcUnavailable -> {
                    ScoreGauge(null, null, centerText = "Health Connect\n필요")
                    Button({ context.startActivity(vm.hc.storeIntent()) }, Modifier.padding(top = 8.dp)) { Text("설치하기") }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScoreBody(e: RecoveryScoreEntity, baselineNights: Int?, onOpenFeedback: () -> Unit) {
    if (e.status == ScoreStatus.NO_DATA.name || e.score == null) {
        ScoreGauge(null, null, centerText = "데이터 부족")
        Text("12:00까지 어젯밤 수면 기록이 들어오지 않았어요.", textAlign = TextAlign.Center)
        return
    }
    val grade = gradeOf(e)
    ScoreGauge(e.score, grade)
    grade?.let { StatusLabel(it.label, it.color()) }
    Text(messageOf(e), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        if (e.status == ScoreStatus.PARTIAL.name) StatusLabel("일부 데이터로 계산", MaterialTheme.colorScheme.onSurfaceVariant)
        if (baselineNights != null && baselineNights < Params.BUILDING_DAYS)
            StatusLabel("기준선 쌓는 중 $baselineNights/${Params.BUILDING_DAYS}일", MaterialTheme.colorScheme.secondary)
        if (e.loadBuilding)   // [수정 2] 워치 착용 14일 미만: 운동 부하는 약하게 반영
            StatusLabel("운동 기준선 쌓는 중", MaterialTheme.colorScheme.secondary)
    }
    // 개선사항 2: 피드백 보기 → 감점 요인·조언
    Button(onOpenFeedback, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("피드백 보기") }
}
