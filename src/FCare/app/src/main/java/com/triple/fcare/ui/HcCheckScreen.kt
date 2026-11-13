package com.triple.fcare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.triple.fcare.engine.HeartRateMetrics
import com.triple.fcare.engine.Params
import com.triple.fcare.engine.Sex
import com.triple.fcare.engine.SleepMetrics
import com.triple.fcare.engine.TrimpCalculator
import com.triple.fcare.hc.HcAvailability
import com.triple.fcare.hc.HcPermissions
import com.triple.fcare.hc.HcReader
import com.triple.fcare.hc.HealthConnectManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Health Connect 연동 확인 화면 (3단계, 실기기 확인용).
 * 어젯밤 수면·심박과 어제 운동을 읽어서 계산 엔진에 넣었을 때 어떤 값이 나오는지 보여 준다.
 * 상세설계서의 "확인 필요" 항목(샘플 수·간격, 수면 단계 유무)을 여기서 확인한다.
 */
@Composable
fun HcCheckScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { HealthConnectManager(context) }
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("아래 버튼을 눌러 확인을 시작하세요.") }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { result -> granted = result }

    Column(
        modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Health Connect 연동 확인", style = MaterialTheme.typography.titleLarge)
        Text("사용 가능 여부: ${manager.availability()}")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                when (manager.availability()) {
                    HcAvailability.AVAILABLE -> permissionLauncher.launch(HcPermissions.ALL)
                    else -> context.startActivity(manager.storeIntent())
                }
            }) { Text("권한 요청") }
            Button(onClick = {
                scope.launch {
                    log = try {
                        granted = manager.grantedPermissions()
                        readAndDescribe(manager)
                    } catch (e: Exception) {
                        "오류: ${e.javaClass.simpleName} — ${e.message}"
                    }
                }
            }) { Text("데이터 읽기") }
        }
        Text("허용된 권한: " + listOf(
            "수면" to HcPermissions.SLEEP, "심박" to HcPermissions.HEART_RATE,
            "운동" to HcPermissions.EXERCISE, "백그라운드" to HcPermissions.BACKGROUND,
        ).joinToString { (n, p) -> "$n ${if (p in granted) "O" else "X"}" })
        HorizontalDivider()
        Text(log, style = MaterialTheme.typography.bodySmall)
    }
}

private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private fun Long.fmt(zone: ZoneId) = Instant.ofEpochMilli(this).atZone(zone).format(TIME)

/** 오늘 점수 날짜 기준으로 데이터를 읽고 결과를 글자로 정리한다. */
private suspend fun readAndDescribe(manager: HealthConnectManager): String {
    val zone = ZoneId.systemDefault()
    val reader = HcReader(manager.client, zone)
    val today = LocalDate.now(zone)
    val sb = StringBuilder()

    // ---- 수면 ----
    sb.appendLine("■ 점수 날짜 $today 의 주 수면")
    val sleep = reader.readMainSleep(today)
    var minHr: Double? = null
    if (sleep == null) {
        sb.appendLine("  수면 기록 없음 (전날 18:00 ~ 오늘 12:00, 3시간 이상)")
    } else {
        val s = SleepMetrics.summarize(sleep, zone)
        sb.appendLine("  세션 ${sleep.startMillis.fmt(zone)} ~ ${sleep.endMillis.fmt(zone)} (출처 ${sleep.source})")
        sb.appendLine("  수면 단계 기록: ${if (s.hasStages) "있음 (${sleep.stages.size}개)" else "없음"}")
        sb.appendLine("  총 수면 시간 %.0f분, 세션 길이 %.0f분, 수면 효율 %s".format(
            s.tstMin, s.inBedMin, s.efficiency?.let { "%.3f".format(it) } ?: "없음"))
        sb.appendLine("  입면 %.0f분, 기상 %.0f분 (18:00 기준)".format(s.onsetMin, s.wakeMin))

        // ---- 수면 중 심박 ----
        val hr = reader.readHeartRate(sleep.startMillis, sleep.endMillis)
        minHr = HeartRateMetrics.sleepMinHr(hr)
        sb.appendLine("■ 수면 중 심박")
        sb.appendLine("  샘플 수 ${hr.size}, 간격 중앙값 ${HeartRateMetrics.medianIntervalSec(hr)?.let { "%.0f초".format(it) } ?: "-"}")
        sb.appendLine("  최저 심박(3개 이동 중앙값) ${minHr ?: "결측 (샘플 ${Params.HR_MIN_SAMPLES}개 미만)"}, 단순 최솟값 ${HeartRateMetrics.rawMin(hr) ?: "-"}")
    }

    // ---- 어제 운동 ----
    val yesterday = today.minusDays(1)
    val exercises = reader.readExercises(yesterday)
    sb.appendLine("■ $yesterday 운동 ${exercises.size}건 (확인용 가정: 22세 남성)")
    val hrMax = TrimpCalculator.hrMax(22)
    val hrRest = minHr ?: Params.DEFAULT_HR_REST
    for (ex in exercises) {
        val samples = reader.readHeartRate(ex.startMillis, ex.endMillis)
        val t = TrimpCalculator.forSession(ex.startMillis, ex.endMillis, samples, hrRest, hrMax, Sex.MALE)
        sb.appendLine("  종류 ${ex.exerciseType}, ${ex.startMillis.fmt(zone)} ~ ${ex.endMillis.fmt(zone)}")
        sb.appendLine("    심박 샘플 ${t.sampleCount}, 간격 중앙값 ${HeartRateMetrics.medianIntervalSec(samples)?.let { "%.0f초".format(it) } ?: "-"}, " +
            "TRIMP ${t.trimp?.let { "%.1f (%s)".format(it, t.method) } ?: "심박 없음"}")
    }
    return sb.toString()
}
