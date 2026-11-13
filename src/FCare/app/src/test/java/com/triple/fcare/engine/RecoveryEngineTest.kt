package com.triple.fcare.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 단위시험 U-01 ~ U-12 (상세설계서 5.3).
 * 기대값은 상세설계서 5장 계산 예시와 같다. 허용 오차: 점수 ±0.1, 그 밖의 값은 표에 적힌 자릿수.
 */
class RecoveryEngineTest {

    // 5.2 예시의 기준선: TST 440분(SD 40), SE 0.91(SD 0.02), 평소 00:20 입면(=380)·07:40 기상(=820), σt 35분, 최저 심박 49(SD 2)
    private val base = Baseline(
        nNights = 28, tstMean = 440.0, tstSd = 40.0, seMean = 0.91, seSd = 0.02,
        onsetMedian = 380.0, wakeMedian = 820.0, timingSd = 35.0,
        nHr = 28, hrMean = 49.0, hrSd = 2.0,
    )

    private fun sleep(tst: Double, se: Double?, onset: Double, wake: Double) =
        SleepSummary(inBedMin = tst / (se ?: 1.0), tstMin = tst, efficiency = se,
            onsetMin = onset, wakeMin = wake, hasStages = se != null)

    @Test fun `U-01 TRIMP 평균 심박 방식 남성`() {
        val t = TrimpCalculator.byAverage(30.0, 150.0, 49.0, TrimpCalculator.hrMax(22), Sex.MALE)
        assertEquals(47.8, t, 0.05)
    }

    @Test fun `U-02 TRIMP 성별 계수 여성 (a 0_86, b 1_67)`() {
        // [수정 1] 여성 앞 계수 a = 0.86 (이전 기대값 40.4는 a = 0.64를 잘못 쓴 값)
        val t = TrimpCalculator.byAverage(30.0, 150.0, 49.0, TrimpCalculator.hrMax(22), Sex.FEMALE)
        assertEquals(54.2, t, 0.05)
    }

    @Test fun `U-03 EWMA 갱신`() {
        val next = LoadModel.next(LoadState(40.0, 30.0), 47.8256)
        assertEquals(42.0, next.atl, 0.05)
        assertEquals(31.2, next.ctl, 0.05)
        assertEquals(1.34, next.acwr, 0.005)
    }

    @Test fun `U-04 ACWR 분모 하한`() {
        val next = LoadModel.next(LoadState(10.0, 2.0), 60.0)
        assertEquals(1.50, next.acwr, 0.005)
        assertEquals(3.75, next.atl / next.ctl, 0.005) // 하한이 없을 때
    }

    @Test fun `U-05 s_LOAD 구간 경계`() {
        assertEquals(0.5, LoadModel.score(0.8), 1e-9)
        assertEquals(0.25, LoadModel.score(0.9), 1e-9)
        assertEquals(0.0, LoadModel.score(1.3), 1e-9)
        assertEquals(-2.0, LoadModel.score(1.5), 1e-9)
        assertEquals(-3.0, LoadModel.score(1.6), 1e-9)
        assertEquals(-3.0, LoadModel.score(1.8), 1e-9)
    }

    @Test fun `U-06 회복 점수 예시 1`() {
        // 01:30 입면(=450), 07:30 기상(=810), TST 360, SE 0.88, 최저 심박 52, ACWR 1.42
        val r = RecoveryScorer.score(ScoreInput(sleep(360.0, 0.88, 450.0, 810.0), 52.0, 1.42), base)
        assertEquals(25.6, r.score, 0.1)
        assertEquals(Grade.NORMAL, r.grade)
        assertTrue(r.capRules.isEmpty())
        assertEquals(ScoreStatus.CONFIRMED, r.status)
    }

    @Test fun `U-07 결측 재배분 (심박 없음)`() {
        val r = RecoveryScorer.score(ScoreInput(sleep(360.0, 0.88, 450.0, 810.0), null, 1.42), base)
        assertEquals(27.0, r.score, 0.1)
        assertEquals(Grade.NORMAL, r.grade)
        assertNull(r.sHr)
        assertEquals(ScoreStatus.PARTIAL, r.status)
    }

    @Test fun `U-08 상한 C1`() {
        // z(TST) +1.5 → 500분, z(SE) +1.2 → 0.934, D = 0, 최저 심박 54 (z +2.5, +5bpm), ACWR 0.70
        val r = RecoveryScorer.score(ScoreInput(sleep(500.0, 0.934, 380.0, 820.0), 54.0, 0.70), base)
        assertEquals(55.0, r.rawScore, 0.1)
        assertEquals(49.0, r.score, 0.1)
        assertEquals(listOf(CapRule.C1), r.capRules)
        assertEquals(Grade.NORMAL, r.grade)
    }

    @Test fun `U-09 상한 경계 (+4bpm)`() {
        val r = RecoveryScorer.score(ScoreInput(sleep(500.0, 0.934, 380.0, 820.0), 53.0, 0.70), base)
        assertEquals(58.8, r.score, 0.1)
        assertEquals(Grade.COMFORT, r.grade)
        assertTrue(r.capRules.isEmpty())
    }

    @Test fun `U-10 상한 C3`() {
        // TST 290분, 나머지 평소 (SE 0.91, D = σt = 35, 최저 심박 49, ACWR 1.1)
        val r = RecoveryScorer.score(ScoreInput(sleep(290.0, 0.91, 415.0, 855.0), 49.0, 1.1), base)
        assertEquals(45.0, r.score, 0.1)
        assertEquals(listOf(CapRule.C3), r.capRules)
        assertEquals(Grade.NORMAL, r.grade)
    }

    @Test fun `U-11 등급 경계`() {
        assertEquals(Grade.TIRED, Grade.of(24.95))
        assertEquals(Grade.NORMAL, Grade.of(25.0))
        assertEquals(Grade.NORMAL, Grade.of(49.99))
        assertEquals(Grade.LIGHT, Grade.of(75.0))
    }

    @Test fun `U-12 평소와 같은 날`() {
        val r = RecoveryScorer.score(ScoreInput(sleep(440.0, 0.91, 415.0, 855.0), 49.0, 1.1), base)
        assertEquals(60.0, r.score, 0.1)
        assertEquals(Grade.COMFORT, r.grade)
    }

    // ---------- [수정 3] 최소 수면 시간 (최근 3일 평균 6시간) ----------

    @Test fun `U-13 최소 수면 - 3일 평균 6시간 미만이면 절대 기준으로 감점`() {
        // 어젯밤 400분(개인 z −1.0), 최근 3일 평균 270분 → 절대 기준 (270−360)/60 = −1.5 → 더 나쁜 −1.5 사용
        val r = RecoveryScorer.score(ScoreInput(sleep(400.0, 0.91, 415.0, 855.0), 49.0, 1.1, tstAvg3 = 270.0), base)
        assertEquals(-1.5, r.sTst, 1e-9)
        assertEquals(52.5, r.score, 0.1)
    }

    @Test fun `U-14 최소 수면 - 기준 이상이면 개인 기준 그대로, 많이 잔 날 가점 유지`() {
        // 어젯밤 500분(가점 +1), 3일 평균 420분 → 그대로 +1, 65.0점
        val ok = RecoveryScorer.score(ScoreInput(sleep(500.0, 0.91, 415.0, 855.0), 49.0, 1.1, tstAvg3 = 420.0), base)
        assertEquals(1.0, ok.sTst, 1e-9)
        assertEquals(65.0, ok.score, 0.1)
        // 같은 날이라도 3일 평균이 330분이면 누적 부족 반영 → −0.5, 57.5점
        val debt = RecoveryScorer.score(ScoreInput(sleep(500.0, 0.91, 415.0, 855.0), 49.0, 1.1, tstAvg3 = 330.0), base)
        assertEquals(-0.5, debt.sTst, 1e-9)
        assertEquals(57.5, debt.score, 0.1)
    }

    // ---------- [수정 2] 운동 기준선 쌓는 중 (착용 14일 미만) ----------

    @Test fun `U-15 운동 기준선 쌓는 중 - C2 끔, 부하 점수 하한 −1`() {
        val building = RecoveryScorer.score(ScoreInput(sleep(440.0, 0.91, 415.0, 855.0), 49.0, 1.7, loadBuilding = true), base)
        assertEquals(-1.0, building.sLoad!!, 1e-9)
        assertTrue(building.capRules.isEmpty())
        assertEquals(52.5, building.score, 0.1)
        // 기준선이 쌓인 뒤에는 원래대로 −3, C2
        val ready = RecoveryScorer.score(ScoreInput(sleep(440.0, 0.91, 415.0, 855.0), 49.0, 1.7), base)
        assertEquals(-3.0, ready.sLoad!!, 1e-9)
        assertEquals(listOf(CapRule.C2), ready.capRules)
        assertEquals(37.5, ready.score, 0.1)
    }

    @Test fun `U-16 운동 부하 - 착용하지 않은 날은 갱신하지 않음`() {
        // 착용한 날만 LoadModel.next를 부르므로, 미착용 3일 뒤에도 상태가 그대로여야 한다 (ScoreCoordinator 규칙)
        var state = LoadState(25.0, 25.0)
        val days = listOf(60.0 to true, 0.0 to false, 0.0 to false, 0.0 to false)
        for ((trimp, worn) in days) if (worn) state = LoadModel.next(state, trimp)
        assertEquals(33.75, state.atl, 1e-6)
        assertEquals(25.0 + (60.0 - 25.0) * 2 / 29, state.ctl, 1e-6)
    }
}
