package com.triple.fcare.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 수면 직접 수정 (HOM-15, 상세설계서 3.8).
 * 사용자가 고른 잠든 시각·깬 시각을 점수 날짜 d의 수면 세션으로 바꾼다.
 * 수면 단계가 없으므로 요약(SleepMetrics.summarize)에서 총 수면 시간 = 세션 길이, 수면 효율은 없음이 된다.
 */
object ManualSleep {
    /** sleep_day.source에 저장하는 직접 입력 표시 */
    const val SOURCE = "manual"

    /**
     * 깬 시각은 점수 날짜 d, 잠든 시각은 18:00 이후면 d−1 밤, 18:00 전이면 d 새벽으로 해석한다.
     * 깬 시각이 잠든 시각보다 앞이거나 길이가 1시간 미만·16시간 초과면 null(저장하지 않음).
     */
    fun session(scoreDate: LocalDate, onset: LocalTime, wake: LocalTime, zone: ZoneId): SleepSessionRaw? {
        val onsetDate = if (onset.hour >= Params.SLEEP_WINDOW_START_HOUR) scoreDate.minusDays(1) else scoreDate
        val start = onsetDate.atTime(onset).atZone(zone).toInstant().toEpochMilli()
        val end = scoreDate.atTime(wake).atZone(zone).toInstant().toEpochMilli()
        val minutes = (end - start) / 60_000L
        if (minutes < Params.MANUAL_SLEEP_MIN_MIN || minutes > Params.MANUAL_SLEEP_MAX_MIN) return null
        return SleepSessionRaw(startMillis = start, endMillis = end, stages = emptyList(), source = SOURCE)
    }

    fun isManual(source: String?): Boolean = source == SOURCE
}
