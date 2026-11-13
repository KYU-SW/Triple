package com.triple.fcare.hc

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.triple.fcare.engine.HrSample
import com.triple.fcare.engine.Params
import com.triple.fcare.engine.SleepMetrics
import com.triple.fcare.engine.SleepSessionRaw
import com.triple.fcare.engine.SleepStage
import com.triple.fcare.engine.StageType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/** 운동 기록 원자료 (상세설계서 2.5에 저장하기 전 단계). */
data class ExerciseRaw(
    val id: String,
    val exerciseType: Int,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * Health Connect 기록을 읽어 계산 엔진이 쓰는 형태로 바꾼다 (상세설계서 3.1).
 * 이 클래스만 Health Connect 타입을 알고, 엔진은 Health Connect를 모른다.
 */
class HcReader(
    private val client: HealthConnectClient,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** 여러 페이지로 나뉜 결과를 모두 읽는다. */
    private suspend fun <T : Record> readAll(type: KClass<T>, start: Instant, end: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = token,
                )
            )
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }

    /**
     * 점수 날짜 d의 주 수면 (상세 3.1).
     * 종료 시각이 [d−1 18:00, d 12:00) 안에 있는 세션 중 3시간 이상이고 가장 긴 것.
     */
    suspend fun readMainSleep(scoreDate: LocalDate): SleepSessionRaw? {
        val windowStart = scoreDate.minusDays(1).atTime(Params.SLEEP_WINDOW_START_HOUR, 0).atZone(zone).toInstant()
        val windowEnd = scoreDate.atTime(Params.SLEEP_DEADLINE_HOUR, 0).atZone(zone).toInstant()
        // 세션이 창보다 일찍 시작할 수 있으므로 조회는 하루 더 넓게 하고, 종료 시각으로 걸러 낸다.
        val records = readAll(SleepSessionRecord::class, windowStart.minusSeconds(24 * 3600), windowEnd)
            .filter { !it.endTime.isBefore(windowStart) && it.endTime.isBefore(windowEnd) }
        val sessions = records.map { r ->
            SleepSessionRaw(
                startMillis = r.startTime.toEpochMilli(),
                endMillis = r.endTime.toEpochMilli(),
                stages = r.stages.map { s ->
                    SleepStage(s.startTime.toEpochMilli(), s.endTime.toEpochMilli(), stageType(s.stage))
                },
                source = r.metadata.dataOrigin.packageName,
            )
        }
        return SleepMetrics.pickMainSession(sessions)
    }

    /** 구간 안의 모든 심박 샘플 (출처를 가리지 않고 시각순). */
    suspend fun readHeartRate(startMillis: Long, endMillis: Long): List<HrSample> =
        readAll(HeartRateRecord::class, Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis))
            .flatMap { rec -> rec.samples.map { HrSample(it.time.toEpochMilli(), it.beatsPerMinute.toDouble()) } }
            .filter { it.timeMillis in startMillis..endMillis }
            .sortedBy { it.timeMillis }

    /**
     * [수정 2] 날짜 day에 워치 심박 기록이 하나라도 있는가 = 워치를 착용한 날.
     * 없으면(연동 전·충전·미착용) 그날 운동 부하는 "데이터 없음"으로 처리한다.
     */
    suspend fun hasHeartRate(day: LocalDate): Boolean {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                pageSize = 1,
            )
        ).records.isNotEmpty()
    }

    /** 날짜 day(00:00~24:00)에 시작한 운동 기록 (상세 3.1). */
    suspend fun readExercises(day: LocalDate): List<ExerciseRaw> {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return readAll(ExerciseSessionRecord::class, start, end)
            .filter { !it.startTime.isBefore(start) && it.startTime.isBefore(end) }
            .distinctBy { it.metadata.id }
            .map { ExerciseRaw(it.metadata.id, it.exerciseType, it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }
    }

    private fun stageType(stage: Int): StageType = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> StageType.AWAKE
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> StageType.SLEEPING
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> StageType.OUT_OF_BED
        SleepSessionRecord.STAGE_TYPE_LIGHT -> StageType.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> StageType.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> StageType.REM
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> StageType.AWAKE_IN_BED
        else -> StageType.UNKNOWN
    }
}
