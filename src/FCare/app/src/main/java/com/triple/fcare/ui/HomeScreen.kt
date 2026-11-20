package com.triple.fcare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.triple.fcare.data.ProfileEntity
import com.triple.fcare.data.RecoveryScoreEntity
import com.triple.fcare.engine.Params
import com.triple.fcare.engine.ScoreStatus
import com.triple.fcare.hc.HcPermissions
import com.triple.fcare.ui.theme.AccentText
import com.triple.fcare.ui.theme.Dim
import com.triple.fcare.ui.theme.FcText
import com.triple.fcare.ui.theme.Radius
import com.triple.fcare.ui.theme.Space
import com.triple.fcare.ui.theme.TextPrimary
import com.triple.fcare.ui.theme.TextSecondary
import com.triple.fcare.ui.theme.TextTertiary
import com.triple.fcare.work.TodayState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DATE_LINE = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val ROW_DATE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val MD = DateTimeFormatter.ofPattern("M/d")
private val HM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 걸음 수 표시 상태 (홈 B3 왼쪽 타일).
 * 걸음 수 읽기(READ_STEPS)는 별도 작업에서 연결한다 — 여기서는 받을 자리만 만든다.
 * Value = 어제 하루 합계, NoPermission = S6a, NoRecord = S6b. 점수 계산에는 쓰지 않는다.
 */
sealed interface StepsUi {
    data class Value(val steps: Long) : StepsUi
    data object NoPermission : StepsUi
    data object NoRecord : StepsUi
}

/** 시간대별 인사말 (JOURNEY.md 홈 문구 칸) */
private fun greeting(nick: String, now: LocalTime): String = when (now.hour) {
    in 0..3 -> "${nick}님, 새벽이에요"
    in 4..10 -> "${nick}님, 좋은 아침이에요"
    in 11..16 -> "${nick}님, 좋은 오후예요"
    else -> "${nick}님, 좋은 저녁이에요"
}

/**
 * 3.1.4 홈 (UI 개편 11-20, 11-20 회의 구성):
 * B1 인사말 → B2 회복 점수 카드 → B3 어제 걸음·수면 타일 → B4 운동 부하 비율 → B5 최근 3일.
 * 상태 S1~S7·X1~X5는 JOURNEY.md 홈 상태 표를 따른다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    profile: ProfileEntity,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState,
    onOpenFeedback: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    steps: StepsUi = StepsUi.NoPermission,
    onRequestSteps: (() -> Unit)? = null,
) {
    val today by vm.today.collectAsState()
    val detail by vm.todayDetail.collectAsState()
    val error by vm.error.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val granted by vm.granted.collectAsState()
    var lastSuccess by rememberSaveable { mutableStateOf<String?>(null) }   // 마지막 갱신 시각 (X4)

    LaunchedEffect(Unit) { vm.refresh() }   // 앱 실행·홈 진입 시 즉시 확인 (기본설계서 3.3)
    LaunchedEffect(refreshing) {
        if (!refreshing && error == null && today != null) lastSuccess = LocalDateTime.now().toString()
    }
    // X4 새로고침 실패: 블록이 아니라 스낵바 (원인 글은 화면에 보이지 않음)
    LaunchedEffect(error) {
        if (error != null) {
            val r = snackbar.showSnackbar("새로고침 실패 · 인터넷 확인", actionLabel = "다시 시도")
            if (r == SnackbarResult.ActionPerformed) vm.refresh()
        }
    }

    // B5 최근 3일: 점수 기준일, 그 전날, 그 전전날
    val baseDate = detail?.date ?: LocalDate.now()
    val recent by produceState<List<Pair<LocalDate, RecoveryScoreEntity?>>>(emptyList(), baseDate, today) {
        value = (0L..2L).map { baseDate.minusDays(it) }.map { it to vm.dayDetail(it).score }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.s4).padding(top = Space.s2, bottom = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            // ---- B1 인사말 줄 (48dp) ----
            Row(Modifier.fillMaxWidth().heightIn(min = Dim.touchMin), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val dateLine = LocalDate.now().format(DATE_LINE)
                    val stale = if (error != null) lastSuccess?.let { " · 마지막 갱신 " + staleLabel(it) } ?: " · 갱신 기록 없음" else ""
                    Text(dateLine + stale, style = FcText.xs, color = TextTertiary, maxLines = 1)
                    Text(greeting(profile.nickname, LocalTime.now()), style = FcText.lg, color = TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(Dim.touchMin), contentAlignment = Alignment.Center) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(Dim.iconSm), strokeWidth = Dim.stroke, color = TextSecondary)
                    else IconButton({ vm.refresh() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "다시 불러오기", tint = TextSecondary)
                    }
                }
            }

            // ---- B2 회복 점수 카드 ----
            ScoreCard(vm, today, detail, onOpenFeedback)

            // ---- B3 어제 걸음 수 · 어제 수면 시간 ----
            TileRow(
                { m -> StepsTile(steps, onRequestSteps, m) },
                { m -> SleepTile(today, detail, m) },
            )

            // ---- B4 운동 부하 비율 ----
            LoadCard(detail, granted)

            // ---- B5 최근 3일 (카드 없음, 줄 사이 구분선) ----
            Text("최근 3일", style = FcText.base.copy(fontWeight = FontWeight.Bold), color = TextPrimary,
                modifier = Modifier.padding(top = Space.s2))
            Column {
                recent.forEachIndexed { i, (d, s) ->
                    if (i > 0) ListDivider()
                    val waiting = i == 0 && today is TodayState.Waiting && s == null
                    ScoreListRow(
                        date = d.format(ROW_DATE),
                        isToday = d == LocalDate.now(),
                        score = s?.score,
                        grade = gradeOf(s),
                        status = when {
                            waiting -> "기다리는 중"
                            s?.status == ScoreStatus.NO_DATA.name -> "데이터 부족"
                            else -> "기록 없음"
                        },
                        onClick = { onOpenDay(d) },
                    )
                }
            }
        }
    }

    if (!profile.guideSeen) GuideDialog { vm.markGuideSeen() }   // HOM-10: 최초 사용 시 안내
}

/** "07:42", 어제 이전이면 "11/19 23:10" */
private fun staleLabel(iso: String): String {
    val t = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return "-"
    return if (t.toLocalDate() == LocalDate.now()) t.format(HM) else t.format(MD) + " " + t.format(HM)
}

/** B2 회복 점수 카드: 라벨 줄(+분석 보기) → 큰 숫자 + 등급 칩 → 막대 → 한 줄 안내 → 상태 칩 */
@Composable
private fun ScoreCard(vm: MainViewModel, today: TodayState?, detail: DayDetail?, onOpenFeedback: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refresh() }
    val baseDate = detail?.date
    val label = if (baseDate != null && baseDate != LocalDate.now()) "어제(기준일 ${baseDate.format(MD)})의 회복 점수" else "오늘의 회복 점수"

    AppCard(padding = Space.s3, modifier = Modifier.heightIn(min = Dim.scoreCard)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Dim.touchMin), verticalAlignment = Alignment.CenterVertically) {
            CardLabel(label, modifier = Modifier.weight(1f))
            if (today is TodayState.Done && today.score.score != null) {
                Text("분석 보기", style = FcText.sm.copy(fontWeight = FontWeight.SemiBold), color = AccentText,
                    modifier = Modifier.heightIn(min = Dim.touchMin).clickable(onClick = onOpenFeedback)
                        .padding(horizontal = Space.s1).padding(top = 14.dp))
            }
        }
        when (today) {
            // X3 불러오는 중: 숫자 자리에 진행 표시, 카드 높이 유지
            null, TodayState.Skipped, TodayState.NoProfile -> Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(Dim.icon), strokeWidth = Dim.strokeBold, color = TextSecondary)
            }
            is TodayState.Done -> ScoreBody(today.score, detail?.baseline?.nNights)
            is TodayState.Waiting -> {   // S2 대기
                Text("기다리는 중", style = FcText.x2l, color = TextPrimary)
                Spacer(Modifier.height(Space.s1))
                Text("어젯밤 수면 기록이 오면 점수를 계산해요.", style = FcText.sm, color = TextSecondary)
                today.previous?.let { p -> p.score?.let {
                    Text("직전 점수 ${it.roundToInt()}점 (${LocalDate.parse(p.date).format(MD)})", style = FcText.sm, color = TextTertiary)
                } }
                Text("12:00까지 없으면 데이터 부족으로 표시해요.", style = FcText.sm, color = TextTertiary)
            }
            TodayState.NeedPermission -> {   // X1
                Text("권한 필요", style = FcText.x2l, color = TextPrimary)
                Text("수면 권한이 있어야 점수를 계산해요.", style = FcText.sm, color = TextSecondary)
                PrimaryButton("권한 허용하기") { launcher.launch(HcPermissions.ALL) }
            }
            TodayState.HcUnavailable -> {   // X2
                Text("Health Connect 필요", style = FcText.x2l, color = TextPrimary)
                Text("점수를 계산하려면 설치가 필요해요.", style = FcText.sm, color = TextSecondary)
                PrimaryButton("설치하기") { context.startActivity(vm.hc.storeIntent()) }
            }
        }
    }
}

@Composable
private fun ScoreBody(e: RecoveryScoreEntity, baselineNights: Int?) {
    if (e.status == ScoreStatus.NO_DATA.name || e.score == null) {   // S3 데이터 부족
        Text("데이터 부족", style = FcText.x2l, color = TextPrimary)
        Spacer(Modifier.height(Space.s1))
        Text("수면 기록이 12:00까지 오지 않았어요.", style = FcText.sm, color = TextSecondary)
        return
    }
    val grade = gradeOf(e)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        NumberWithUnits(listOf("${e.score.roundToInt()}" to "점"), numberStyle = FcText.x4l)
        Spacer(Modifier.weight(1f))
        grade?.let { GradeChip(it) }
    }
    Spacer(Modifier.height(Space.s3))
    ScoreBar(e.score, grade)
    Spacer(Modifier.height(Space.s1))
    Text(messageOf(e), style = FcText.sm, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    // S4·S5 상태 칩: 일부 데이터 → 기준선 → 운동 기준선 순서, 1줄 고정 + "+n"
    val chips = buildList {
        if (e.status == ScoreStatus.PARTIAL.name) add("일부 데이터로 계산")
        if (baselineNights != null && baselineNights < Params.BUILDING_DAYS) add("기준선 쌓는 중 $baselineNights/${Params.BUILDING_DAYS}일")
        if (e.loadBuilding) add("운동 기준선 쌓는 중")
    }
    if (chips.isNotEmpty()) {
        Spacer(Modifier.height(Space.s1))
        StatusChipRow(chips)
    }
}

/** 주 버튼 (높이 52dp, 각진 모서리 12) */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.fillMaxWidth().padding(top = Space.s2).heightIn(min = Dim.button), enabled = enabled,
        shape = Radius.control, colors = ButtonDefaults.buttonColors(),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)) {
        Text(text, style = FcText.base.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** B3 왼쪽: 어제 걸음 수 (보여 주기만, 점수에 쓰지 않음) */
@Composable
private fun StepsTile(steps: StepsUi, onRequest: (() -> Unit)?, modifier: Modifier) {
    when (steps) {
        is StepsUi.Value -> MetricTile("어제 걸음 수", "", "점수에는 쓰지 않아요", modifier = modifier,
            icon = Icons.AutoMirrored.Rounded.DirectionsWalk, parts = listOf("%,d".format(steps.steps) to "걸음"))
        StepsUi.NoPermission -> MetricTile("어제 걸음 수", "—", if (onRequest != null) "권한 필요 ›" else "권한 필요",
            modifier = modifier, icon = Icons.AutoMirrored.Rounded.DirectionsWalk, caption2 = "점수에 영향 없어요",
            onClick = onRequest)
        StepsUi.NoRecord -> MetricTile("어제 걸음 수", "—", "기록 없음", modifier = modifier,
            icon = Icons.AutoMirrored.Rounded.DirectionsWalk, caption2 = "점수에 영향 없어요")
    }
}

/** B3 오른쪽: 어제 수면 시간 (점수 계산에 쓴 어젯밤 수면) */
@Composable
private fun SleepTile(today: TodayState?, detail: DayDetail?, modifier: Modifier) {
    val sleep = detail?.sleep
    if (sleep != null) {
        val m = sleep.tstMin.roundToInt()
        MetricTile("어제 수면 시간", "", "점수에 반영된 수면", modifier = modifier, icon = Icons.Rounded.Bedtime,
            parts = listOf("${m / 60}" to "시간", "${m % 60}" to "분"))
    } else {
        val why = when (today) {
            is TodayState.Waiting -> "기다리는 중"
            TodayState.NeedPermission -> "권한 필요"
            TodayState.HcUnavailable -> "연결 필요"
            else -> "기록 없음"
        }
        MetricTile("어제 수면 시간", "—", why, modifier = modifier, icon = Icons.Rounded.Bedtime)
    }
}

/** B4 운동 부하 비율 카드: 머리 줄(라벨 + 비율 숫자) → 막대(낮음·적정·많음) → 보조 줄 */
@Composable
private fun LoadCard(detail: DayDetail?, granted: Set<String>) {
    val load = detail?.load
    val noPerm = HcPermissions.HEART_RATE !in granted || HcPermissions.EXERCISE !in granted
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { }
    AppCard(padding = Space.s3, modifier = Modifier.heightIn(min = Dim.loadCard),
        onClick = if (load == null && noPerm && granted.isNotEmpty()) ({ launcher.launch(HcPermissions.ALL) }) else null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardLabel("운동 부하 비율", Icons.AutoMirrored.Rounded.DirectionsRun, Modifier.weight(1f))
            if (load != null) NumberWithUnits(listOf("%.2f".format(load.acwr) to "배"))
            else Text("—", style = FcText.x2l, color = TextTertiary)
        }
        Spacer(Modifier.height(Space.s1))
        LoadBar(load?.acwr)
        val noHr = detail?.exercises?.count { it.trimp == null } ?: 0
        val loadBuilding = detail?.score?.loadBuilding == true
        val caption = when {
            load == null && noPerm -> "심박·운동 권한이 필요해요 ›"
            load == null -> "운동 기록이 쌓이면 보여 드려요"
            noHr > 0 -> "심박이 없어 반영하지 못한 운동 ${noHr}건"
            loadBuilding -> "운동 기준선을 쌓는 중이라 부하를 약하게 반영해요."
            else -> "1.0이 평소예요. 1.3을 넘으면 많이 운동한 상태예요."
        }
        Text(caption, style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextSecondary, maxLines = 2,
            modifier = Modifier.padding(top = Space.s1))
    }
}
