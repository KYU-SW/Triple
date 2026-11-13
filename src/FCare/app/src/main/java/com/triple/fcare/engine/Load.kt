package com.triple.fcare.engine

import kotlin.math.exp

/** 운동별 TRIMP 산출 (상세설계서 3.4, Banister 방식). */
object TrimpCalculator {

    fun hrMax(age: Int): Double = (Params.HR_MAX_BASE - age).toDouble()

    private fun k(sex: Sex) = if (sex == Sex.MALE) Params.K_MALE else Params.K_FEMALE
    private fun a(sex: Sex) = if (sex == Sex.MALE) Params.TRIMP_A_MALE else Params.TRIMP_A_FEMALE

    /** 심박 예비율 HRr = (HR − 안정시) ÷ (최대 − 안정시), 0~1로 제한. */
    fun hrReserve(hr: Double, hrRest: Double, hrMax: Double): Double =
        ((hr - hrRest) / (hrMax - hrRest)).coerceIn(0.0, 1.0)

    /** 구간 하나의 TRIMP = 분 × HRr × a × e^(b × HRr)  (남 a 0.64·b 1.92, 여 a 0.86·b 1.67). */
    fun impulse(minutes: Double, hrr: Double, sex: Sex): Double =
        minutes * hrr * a(sex) * exp(k(sex) * hrr)

    /**
     * 운동 1건의 TRIMP.
     * - 샘플 3개 미만: 심박 없음 (trimp = null)
     * - 샘플 간격 중앙값 ≤ 60초: 샘플별 합산 (SAMPLE)
     * - 그보다 듬성듬성: 평균 심박 방식 (AVERAGE)
     */
    fun forSession(
        startMillis: Long,
        endMillis: Long,
        samples: List<HrSample>,
        hrRest: Double,
        hrMax: Double,
        sex: Sex,
    ): TrimpResult {
        val s = samples.filter { it.timeMillis in startMillis..endMillis }.sortedBy { it.timeMillis }
        if (s.size < Params.TRIMP_MIN_SAMPLES) return TrimpResult(null, null, null, s.size)
        val avg = s.map { it.bpm }.average()
        val medianGap = HeartRateMetrics.medianIntervalSec(s) ?: Double.MAX_VALUE

        if (medianGap > Params.SAMPLE_INTERVAL_MAX_SEC) {
            val minutes = (endMillis - startMillis) / 60_000.0
            val t = impulse(minutes, hrReserve(avg, hrRest, hrMax), sex)
            return TrimpResult(t, TrimpMethod.AVERAGE, avg, s.size)
        }
        var total = 0.0
        for (i in s.indices) {
            val nextT = if (i + 1 < s.size) s[i + 1].timeMillis else endMillis
            val dt = ((nextT - s[i].timeMillis) / 60_000.0).coerceIn(0.0, Params.TRIMP_GAP_MAX_MIN)
            total += impulse(dt, hrReserve(s[i].bpm, hrRest, hrMax), sex)
        }
        return TrimpResult(total, TrimpMethod.SAMPLE, avg, s.size)
    }

    /** 평균 심박 방식 단독 계산 (단위시험 U-01·U-02용). */
    fun byAverage(minutes: Double, avgHr: Double, hrRest: Double, hrMax: Double, sex: Sex): Double =
        impulse(minutes, hrReserve(avgHr, hrRest, hrMax), sex)
}

/** 운동 부하 ATL·CTL·ACWR (상세설계서 4.2, 4.3). */
object LoadModel {

    /** 앱 사용 첫날의 ATL·CTL 초기값: 운동 빈도 × 운동 1회 TRIMP ÷ 7. */
    fun initial(freq: ExerciseFreq): LoadState {
        val daily = freq.sessionsPerWeek * Params.TRIMP_PER_SESSION / 7.0
        return LoadState(daily, daily)
    }

    /** 하루 갱신 (지수가중 평균). dailyTrimp는 그날 TRIMP 합계, 운동이 없으면 0. */
    fun next(prev: LoadState, dailyTrimp: Double): LoadState = LoadState(
        atl = prev.atl + Params.LAMBDA_ATL * (dailyTrimp - prev.atl),
        ctl = prev.ctl + Params.LAMBDA_CTL * (dailyTrimp - prev.ctl),
    )

    /** ACWR → 운동 부하 지표 점수 s_LOAD (상세 4.3). */
    fun score(acwr: Double): Double = when {
        acwr <= Params.ACWR_REST -> Params.S_MAX_LOAD
        acwr <= Params.ACWR_NORMAL_LOW ->
            Params.S_MAX_LOAD * (Params.ACWR_NORMAL_LOW - acwr) / (Params.ACWR_NORMAL_LOW - Params.ACWR_REST)
        acwr <= Params.ACWR_NORMAL_HIGH -> 0.0
        acwr < Params.ACWR_FLOOR ->
            Params.S_MIN * (acwr - Params.ACWR_NORMAL_HIGH) / (Params.ACWR_FLOOR - Params.ACWR_NORMAL_HIGH)
        else -> Params.S_MIN
    }
}
