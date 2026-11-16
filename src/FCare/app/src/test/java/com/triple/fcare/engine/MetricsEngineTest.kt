package com.triple.fcare.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 단위시험 U-23 ~ U-31: 워치 원자료를 지표·기준선으로 바꾸는 계산 (상세설계서 3.2 ~ 3.4, 4.2, 4.4).
 * U-01 ~ U-22는 이미 계산된 지표에서 시작하므로, 그 앞 단계를 여기서 따로 시험한다.
 * 시각은 UTC 기준으로 만든다 (기기 시간대와 무관하게 같은 결과).
 */
class MetricsEngineTest {

    private val zone = ZoneOffset.UTC
    private fun t(day: Int, h: Int, m: Int): Long =
        LocalDateTime.of(2026, 11, day, h, m).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun min(m: Int): Long = m * 60_000L

    /* ---------- 수면 지표 (상세 3.1, 3.2) ---------- */

    @Test fun `U-23 18시 기준 경과 분`() {
        assertEquals(0.0, SleepMetrics.minutesFrom18(t(15, 18, 0), zone), 1e-9)
        assertEquals(390.0, SleepMetrics.minutesFrom18(t(16, 0, 30), zone), 1e-9)
        assertEquals(820.0, SleepMetrics.minutesFrom18(t(16, 7, 40), zone), 1e-9)
        assertEquals(1439.0, SleepMetrics.minutesFrom18(t(16, 17, 59), zone), 1e-9)
    }

    @Test fun `U-24 주 수면 선택 - 3시간 이상 중 가장 긴 세션`() {
        val nap = SleepSessionRaw(t(15, 14, 0), t(15, 16, 0), emptyList())          // 2시간: 낮잠
        val six = SleepSessionRaw(t(15, 23, 0), t(16, 5, 0), emptyList())          // 6시간
        val long = SleepSessionRaw(t(15, 23, 0), t(16, 6, 30), emptyList())        // 7시간 30분
        val exact = SleepSessionRaw(t(15, 1, 0), t(15, 4, 0), emptyList())         // 정확히 3시간
        assertEquals(long, SleepMetrics.pickMainSession(listOf(nap, six, long)))
        assertNull(SleepMetrics.pickMainSession(listOf(nap)))
        assertEquals(exact, SleepMetrics.pickMainSession(listOf(nap, exact)))
    }

    @Test fun `U-25 수면 요약 - 단계 기록 있음`() {
        val s = SleepSessionRaw(t(15, 23, 30), t(16, 7, 30), listOf(
            SleepStage(t(15, 23, 30), t(15, 23, 45), StageType.AWAKE),
            SleepStage(t(15, 23, 45), t(16, 3, 0), StageType.LIGHT),
            SleepStage(t(16, 3, 0), t(16, 3, 30), StageType.AWAKE),
            SleepStage(t(16, 3, 30), t(16, 6, 0), StageType.DEEP),
            SleepStage(t(16, 6, 0), t(16, 7, 15), StageType.REM),
            SleepStage(t(16, 7, 15), t(16, 7, 30), StageType.AWAKE_IN_BED),
        ))
        val r = SleepMetrics.summarize(s, zone)
        assertEquals(480.0, r.inBedMin, 1e-9)
        assertEquals(420.0, r.tstMin, 1e-9)        // 480 − 각성 60분
        assertEquals(0.875, r.efficiency!!, 1e-9)
        assertEquals(345.0, r.onsetMin, 1e-9)      // 첫 수면 단계 23:45
        assertEquals(795.0, r.wakeMin, 1e-9)       // 마지막 수면 단계 07:15
        assertTrue(r.hasStages)
    }

    @Test fun `U-26 수면 요약 - 단계 기록 없음`() {
        val s = SleepSessionRaw(t(16, 0, 0), t(16, 6, 0),
            listOf(SleepStage(t(16, 1, 0), t(16, 2, 0), StageType.UNKNOWN)))   // UNKNOWN은 단계로 보지 않음
        val r = SleepMetrics.summarize(s, zone)
        assertEquals(360.0, r.tstMin, 1e-9)
        assertNull(r.efficiency)
        assertEquals(360.0, r.onsetMin, 1e-9)      // 세션 시작 00:00
        assertEquals(720.0, r.wakeMin, 1e-9)       // 세션 종료 06:00
        assertFalse(r.hasStages)
    }

    /* ---------- 수면 중 최저 심박 (상세 3.3) ---------- */

    @Test fun `U-27 최저 심박 - 3개 이동 중앙값, 샘플 10개 미만 결측`() {
        val bpm = listOf(60.0, 58.0, 45.0, 57.0, 55.0, 56.0, 54.0, 58.0, 59.0, 60.0)
        val samples = bpm.mapIndexed { i, v -> HrSample(t(16, 2, 0) + min(i), v) }.shuffled(java.util.Random(1))
        assertEquals(55.0, HeartRateMetrics.sleepMinHr(samples)!!, 1e-9)   // 45 한 번은 무시
        assertEquals(45.0, HeartRateMetrics.rawMin(samples)!!, 1e-9)
        assertEquals(60.0, HeartRateMetrics.medianIntervalSec(samples)!!, 1e-9)
        assertNull(HeartRateMetrics.sleepMinHr(samples.take(9)))
        assertNull(HeartRateMetrics.medianIntervalSec(samples.take(1)))
    }

    /* ---------- 운동별 TRIMP (상세 3.4) ---------- */

    @Test fun `U-28 TRIMP 샘플별 방식 - 1분 간격과 5분 상한`() {
        val start = t(16, 18, 0)
        // 10분 운동, 1분마다 150 bpm → 10분 × 1.594
        val even = (0 until 10).map { HrSample(start + min(it), 150.0) }
        val a = TrimpCalculator.forSession(start, start + min(10), even, 49.0, 198.0, Sex.MALE)
        assertEquals(TrimpMethod.SAMPLE, a.method)
        assertEquals(15.94, a.trimp!!, 0.01)
        // 0·1·2분, 10분 샘플, 12분 종료 → 1 + 1 + min(8, 5) + 2 = 9분 × 1.594
        val gap = listOf(0, 1, 2, 10).map { HrSample(start + min(it), 150.0) }
        val b = TrimpCalculator.forSession(start, start + min(12), gap, 49.0, 198.0, Sex.MALE)
        assertEquals(TrimpMethod.SAMPLE, b.method)
        assertEquals(14.35, b.trimp!!, 0.01)
    }

    @Test fun `U-29 TRIMP 평균 심박 방식과 심박 없음`() {
        val start = t(16, 18, 0)
        // 5분 간격(60초 초과) → 평균 심박 방식, 평균 150 → U-01과 같은 47.8
        val sparse = (0 until 6).map { HrSample(start + min(it * 5), if (it % 2 == 0) 140.0 else 160.0) }
        val outside = HrSample(start - min(10), 200.0)   // 운동 구간 밖 샘플은 제외
        val a = TrimpCalculator.forSession(start, start + min(30), sparse + outside, 49.0, 198.0, Sex.MALE)
        assertEquals(TrimpMethod.AVERAGE, a.method)
        assertEquals(150.0, a.avgHr!!, 1e-9)
        assertEquals(47.8, a.trimp!!, 0.05)
        assertEquals(6, a.sampleCount)
        // 샘플 2개 → 심박 없음 (EXE-11)
        val none = TrimpCalculator.forSession(start, start + min(30), sparse.take(2), 49.0, 198.0, Sex.MALE)
        assertNull(none.trimp)
        assertNull(none.method)
        assertEquals(178.0, TrimpCalculator.hrMax(42), 1e-9)
    }

    /* ---------- 개인 기준선 (상세 4.4) ---------- */

    private fun day(tst: Double, se: Double?, onset: Double, wake: Double, hr: Double?, c1: Boolean = false) =
        DayRecord(tst, se, onset, wake, hr, c1)

    @Test fun `U-30 기준선 - 기록 0일, 3일(일반값), 14일(혼합 평균)`() {
        val empty = BaselineCalculator.compute(emptyList())
        assertEquals(0, empty.nNights)
        assertEquals(450.0, empty.tstMean, 1e-9)
        assertEquals(45.0, empty.tstSd, 1e-9)
        assertNull(empty.onsetMedian)
        assertNull(empty.hrMean)
        assertEquals(BaselineStatus.BUILDING, empty.status)

        val three = BaselineCalculator.compute(listOf(
            day(400.0, 0.90, 360.0, 800.0, 50.0), day(420.0, 0.92, 380.0, 820.0, 52.0), day(440.0, 0.94, 400.0, 840.0, 54.0)))
        assertEquals(450.0, three.tstMean, 1e-9)      // 7일 미만: 수면은 일반값
        assertEquals(0.90, three.seMean, 1e-9)
        assertEquals(45.0, three.timingSd, 1e-9)
        assertEquals(380.0, three.onsetMedian!!, 1e-9)
        assertEquals(52.0, three.hrMean!!, 1e-9)      // 심박은 3일부터 개인값
        assertEquals(2.0, three.hrSd!!, 1e-9)

        val fourteen = BaselineCalculator.compute((0 until 14).map { i ->
            if (i % 2 == 0) day(400.0, 0.92, 370.0, 820.0, 50.0) else day(440.0, 0.92, 390.0, 820.0, 50.0) })
        assertEquals(430.0, fourteen.tstMean, 1e-9)   // (14 × 420 + 7 × 450) ÷ 21
        assertEquals(20.755, fourteen.tstSd, 0.001)
        assertEquals(0.91333, fourteen.seMean, 0.00001)
        assertEquals(380.0, fourteen.onsetMedian!!, 1e-9)
        assertEquals(5.189, fourteen.timingSd, 0.001) // (10.377 + 0) ÷ 2
        assertEquals(BaselineStatus.READY, fourteen.status)
    }

    @Test fun `U-31 기준선 - 28일 창, C1 날 제외, 운동 부하 초기값`() {
        val old = List(2) { day(600.0, 0.90, 380.0, 820.0, 50.0) }       // 28일보다 오래된 기록 (빠져야 함)
        val recent = List(28) { day(430.0, 0.90, 380.0, 820.0, 50.0) }
        val full = BaselineCalculator.compute(old + recent)
        assertEquals(28, full.nNights)
        assertEquals(430.0, full.tstMean, 1e-9)

        val withC1 = recent.toMutableList().apply { this[10] = day(300.0, 0.90, 380.0, 820.0, 60.0, c1 = true) }
        val excluded = BaselineCalculator.compute(old + withC1)
        assertEquals(27, excluded.nNights)
        assertEquals(434.12, excluded.tstMean, 0.01)  // (27 × 430 + 7 × 450) ÷ 34
        assertEquals(50.0, excluded.hrMean!!, 1e-9)   // C1 날 심박 60도 빠짐

        assertEquals(10.71, LoadModel.initial(ExerciseFreq.W1_2).ctl, 0.01)
        assertEquals(25.0, LoadModel.initial(ExerciseFreq.W3_4).atl, 1e-9)
        assertEquals(39.29, LoadModel.initial(ExerciseFreq.W5_PLUS).ctl, 0.01)
    }
}
