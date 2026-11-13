package com.triple.fcare.engine

import java.time.Instant
import java.time.ZoneId

/** 수면 지표 산출 (상세설계서 3.1, 3.2). */
object SleepMetrics {

    private val AWAKE_TYPES = setOf(StageType.AWAKE, StageType.AWAKE_IN_BED, StageType.OUT_OF_BED)
    private val SLEEP_TYPES = setOf(StageType.LIGHT, StageType.DEEP, StageType.REM, StageType.SLEEPING)

    /**
     * 시각을 "18:00 기준 경과 분"으로 바꾼다 (상세 2.1).
     * 18:00 → 0, 00:30 → 390, 07:40 → 820. 자정을 넘겨도 값이 이어지게 하기 위함.
     */
    fun minutesFrom18(millis: Long, zone: ZoneId): Double {
        val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        val minuteOfDay = t.hour * 60 + t.minute + t.second / 60.0
        return ((minuteOfDay - Params.SLEEP_WINDOW_START_HOUR * 60) % 1440 + 1440) % 1440
    }

    /**
     * 주 수면 선택 (상세 3.1): 길이 3시간 이상인 세션 중 가장 긴 것. 없으면 null.
     * 조회 범위(종료 시각이 전날 18:00 ~ 당일 12:00)는 호출하는 쪽(hc)에서 맞춘다.
     */
    fun pickMainSession(sessions: List<SleepSessionRaw>): SleepSessionRaw? =
        sessions
            .filter { (it.endMillis - it.startMillis) / 60_000 >= Params.SLEEP_MIN_SESSION_MIN }
            .maxByOrNull { it.endMillis - it.startMillis }

    /** 세션 원자료 → 수면 일별 요약 (상세 3.2). */
    fun summarize(session: SleepSessionRaw, zone: ZoneId): SleepSummary {
        val inBed = (session.endMillis - session.startMillis) / 60_000.0
        val stages = session.stages.filter { it.type != StageType.UNKNOWN }
        val hasStages = stages.isNotEmpty()

        // 총 수면 시간 = 세션 길이 − 각성 구간 (단계 기록이 없으면 세션 길이)
        val awakeMin = stages.filter { it.type in AWAKE_TYPES }
            .sumOf { (it.endMillis - it.startMillis) / 60_000.0 }
        val tst = (inBed - awakeMin).coerceAtLeast(0.0)

        // 입면·기상 시각: 첫·마지막 수면 단계 (없으면 세션 시작·종료)
        val sleepStages = stages.filter { it.type in SLEEP_TYPES }
        val onset = sleepStages.minOfOrNull { it.startMillis } ?: session.startMillis
        val wake = sleepStages.maxOfOrNull { it.endMillis } ?: session.endMillis

        return SleepSummary(
            inBedMin = inBed,
            tstMin = tst,
            efficiency = if (hasStages && inBed > 0) tst / inBed else null,
            onsetMin = minutesFrom18(onset, zone),
            wakeMin = minutesFrom18(wake, zone),
            hasStages = hasStages,
        )
    }
}

/** 수면 중 최저 심박 산출 (상세 3.3). */
object HeartRateMetrics {

    /**
     * 이웃한 3개 샘플의 중앙값(이동 중앙값)을 구한 뒤 그 최솟값을 반환한다.
     * 샘플 하나의 측정 오류가 최저값이 되는 것을 막기 위함. 샘플이 10개 미만이면 null.
     */
    fun sleepMinHr(samples: List<HrSample>): Double? {
        if (samples.size < Params.HR_MIN_SAMPLES) return null
        val bpm = samples.sortedBy { it.timeMillis }.map { it.bpm }
        val w = Params.HR_MEDIAN_WINDOW
        return (0..bpm.size - w).minOf { i -> bpm.subList(i, i + w).sorted()[w / 2] }
    }

    /** 단순 최솟값 (비교·검증용, hr_day.raw_min_hr). */
    fun rawMin(samples: List<HrSample>): Double? = samples.minOfOrNull { it.bpm }

    /** 샘플 간격 중앙값(초) — 실기기 확인용 (hr_day.median_interval_sec). */
    fun medianIntervalSec(samples: List<HrSample>): Double? {
        val t = samples.map { it.timeMillis }.sorted()
        if (t.size < 2) return null
        val gaps = t.zipWithNext { a, b -> (b - a) / 1000.0 }.sorted()
        return gaps[gaps.size / 2]
    }
}
