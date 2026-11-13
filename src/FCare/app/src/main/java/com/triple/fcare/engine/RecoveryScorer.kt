package com.triple.fcare.engine

import kotlin.math.abs
import kotlin.math.sqrt

/** 개인 기준선 계산 (상세설계서 4.4). */
object BaselineCalculator {

    /**
     * @param history 점수 날짜 d의 전날까지 최근 28일 기록 (오늘 값은 넣지 않는다)
     */
    fun compute(history: List<DayRecord>): Baseline {
        val days = history.takeLast(Params.BASELINE_DAYS).filter { !it.excludedByC1 }
        val n = days.size

        // 수면 3지표: 0~6일 일반값, 7~27일 혼합 평균, 28일 개인값
        fun blend(personal: Double, general: Double): Double = when {
            n < Params.BUILDING_DAYS -> general
            n >= Params.BASELINE_DAYS -> personal
            else -> (n * personal + Params.SHRINK_K * general) / (n + Params.SHRINK_K)
        }
        val tst = days.map { it.tstMin }
        val se = days.mapNotNull { it.efficiency }
        val onset = days.map { it.onsetMin }
        val wake = days.map { it.wakeMin }
        val useGeneral = n < Params.BUILDING_DAYS

        val tstMean = if (n == 0) Params.DEFAULT_TST else blend(tst.average(), Params.DEFAULT_TST)
        val tstSd = if (useGeneral) Params.DEFAULT_TST_SD else sd(tst)
        val seMean = if (se.isEmpty()) Params.DEFAULT_SE else blend(se.average(), Params.DEFAULT_SE)
        val seSd = if (useGeneral || se.size < 2) Params.DEFAULT_SE_SD else sd(se)
        val timingSd = if (useGeneral) Params.DEFAULT_TIMING_SD else (sd(onset) + sd(wake)) / 2

        // 최저 심박: 3일 이상 쌓이면 개인 평균·표준편차
        val hr = days.mapNotNull { it.minHr }
        val hrReady = hr.size >= Params.HR_MIN_NIGHTS

        return Baseline(
            nNights = n,
            tstMean = tstMean,
            tstSd = tstSd,
            seMean = seMean,
            seSd = seSd,
            onsetMedian = median(onset),
            wakeMedian = median(wake),
            timingSd = timingSd,
            nHr = hr.size,
            hrMean = if (hrReady) hr.average() else null,
            hrSd = if (hrReady) sd(hr) else null,
        )
    }

    /** 표본 표준편차 (n − 1). 값이 1개 이하면 0 → 정규화할 때 하한이 적용된다. */
    fun sd(x: List<Double>): Double {
        if (x.size < 2) return 0.0
        val m = x.average()
        return sqrt(x.sumOf { (it - m) * (it - m) } / (x.size - 1))
    }

    fun median(x: List<Double>): Double? {
        if (x.isEmpty()) return null
        val s = x.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}

/** 회복 점수 계산 (상세설계서 4.5 ~ 4.7). */
object RecoveryScorer {

    private fun z(value: Double, mean: Double, sd: Double, floor: Double) = (value - mean) / maxOf(sd, floor)

    fun score(input: ScoreInput, base: Baseline): ScoreResult {
        val sleep = input.sleep

        // ---- 1) 수면 지표 점수 (4.5) ----
        val zTst = z(sleep.tstMin, base.tstMean, base.tstSd, Params.SD_FLOOR_TST)
        val sTstPersonal = zTst.coerceIn(Params.S_MIN, Params.S_MAX_SLEEP)
        // [수정 3] 최소 수면 시간: 최근 3일 평균이 6시간 미만이면 개인 기준과 상관없이 감점 (더 나쁜 값 사용)
        val avg3 = input.tstAvg3 ?: sleep.tstMin
        val sTst = if (avg3 >= Params.MIN_SLEEP_MIN) sTstPersonal else minOf(
            sTstPersonal,
            ((avg3 - Params.MIN_SLEEP_MIN) / Params.MIN_SLEEP_PER_POINT).coerceAtLeast(Params.S_MIN),
        )
        val sSe = sleep.efficiency?.let {
            z(it, base.seMean, base.seSd, Params.SD_FLOOR_SE).coerceIn(Params.S_MIN, Params.S_MAX_SLEEP)
        }
        val sReg = if (base.onsetMedian != null && base.wakeMedian != null) {
            val d = (abs(sleep.onsetMin - base.onsetMedian) + abs(sleep.wakeMin - base.wakeMedian)) / 2
            (1 - d / maxOf(base.timingSd, Params.SD_FLOOR_TIMING)).coerceIn(Params.S_MIN, Params.S_MAX_SLEEP)
        } else null

        // ---- 2) 수면 축: 있는 지표끼리 가중 평균 (4.6) ----
        val sleepParts = listOfNotNull(
            Params.W_TST to sTst,
            sReg?.let { Params.W_REG to it },
            sSe?.let { Params.W_SE to it },
        )
        val sSleep = sleepParts.sumOf { it.first * it.second } / sleepParts.sumOf { it.first }

        // ---- 3) 최저 심박 축: 기준선(3일 이상)과 당일 값이 모두 있어야 계산 ----
        val zHr = if (input.minHr != null && base.hrMean != null && base.hrSd != null)
            z(input.minHr, base.hrMean, base.hrSd, Params.SD_FLOOR_HR) else null
        val sHr = zHr?.let { (-it).coerceIn(Params.S_MIN, Params.S_MAX_HR) }

        // ---- 4) 운동 부하 축 ----
        // [수정 2] 운동 기준선을 쌓는 중(착용 14일 미만)이면 감점은 −1까지만
        val sLoad = input.acwr?.let {
            val s = LoadModel.score(it)
            if (input.loadBuilding) s.coerceAtLeast(Params.S_MIN_LOAD_BUILDING) else s
        }

        // ---- 5) 회복 지수 R: 없는 축은 남은 축 가중치로 다시 나눔 ----
        val axes = listOfNotNull(
            Params.W_SLEEP to sSleep,
            sHr?.let { Params.W_HR to it },
            sLoad?.let { Params.W_LOAD to it },
        )
        val r = axes.sumOf { it.first * it.second } / axes.sumOf { it.first }

        // ---- 6) 점수 변환과 상한 규칙 (4.7) ----
        val raw = (Params.SCORE_BASE + Params.SCORE_SCALE * r).coerceIn(0.0, 100.0)
        val caps = buildList {
            if (zHr != null && zHr >= Params.C1_Z && input.minHr!! - base.hrMean!! >= Params.C1_BPM) add(CapRule.C1)
            if (input.acwr != null && input.acwr >= Params.C2_ACWR && !input.loadBuilding) add(CapRule.C2)
            if (zTst <= Params.C3_Z || sleep.tstMin < Params.C3_TST_MIN) add(CapRule.C3)
        }
        val finalScore = if (caps.isEmpty()) raw else minOf(raw, Params.CAP_SCORE)

        // ---- 7) 상태: 최저 심박(당일 값 없음)이나 운동 부하가 빠지면 "일부 데이터로 계산" ----
        // 기준선이 아직 3일이 안 돼서 심박 축을 뺀 경우는 정상(CONFIRMED)으로 본다.
        val partial = input.minHr == null || input.acwr == null
        return ScoreResult(
            status = if (partial) ScoreStatus.PARTIAL else ScoreStatus.CONFIRMED,
            score = finalScore,
            rawScore = raw,
            grade = Grade.of(finalScore),
            sTst = sTst, sSe = sSe, sReg = sReg, sSleep = sSleep, sHr = sHr, sLoad = sLoad,
            rIndex = r,
            capRules = caps,
            baselineStatus = base.status,
            loadBuilding = input.loadBuilding,
        )
    }
}
