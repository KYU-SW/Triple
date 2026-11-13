package com.triple.fcare.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 단위시험 U-17 ~ U-22: 피드백 엔진 (개선사항 2·4·6).
 * 점수 분해 합계가 RecoveryScorer와 같은지, 조언 규칙이 정해진 순서로 고르는지, Gemini 결과 검사를 확인한다.
 */
class FeedbackEngineTest {

    private val base = Baseline(
        nNights = 28, tstMean = 440.0, tstSd = 40.0, seMean = 0.91, seSd = 0.02,
        onsetMedian = 380.0, wakeMedian = 820.0, timingSd = 35.0,
        nHr = 28, hrMean = 49.0, hrSd = 2.0,
    )

    private fun sleep(tst: Double, se: Double?, onset: Double, wake: Double) =
        SleepSummary(inBedMin = tst / (se ?: 1.0), tstMin = tst, efficiency = se,
            onsetMin = onset, wakeMin = wake, hasStages = se != null)

    private fun input(r: ScoreResult, s: SleepSummary, minHr: Double?, acwr: Double?, avg3: Double? = null) = FeedbackInput(
        score = r.score, rawScore = r.rawScore, grade = r.grade,
        sTst = r.sTst, sSe = r.sSe, sReg = r.sReg, sHr = r.sHr, sLoad = r.sLoad,
        capRules = r.capRules, loadBuilding = r.loadBuilding, baselineNights = base.nNights,
        tstMin = s.tstMin, tstMean = base.tstMean, tstAvg3 = avg3, tstAvg7 = null,
        efficiency = s.efficiency, seMean = base.seMean, onsetMin = s.onsetMin, onsetMedian = base.onsetMedian,
        minHr = minHr, hrMean = base.hrMean, acwr = acwr,
    )

    @Test fun `U-17 점수 분해 합계 = 원점수 − 60 (U-06 예시)`() {
        val s = sleep(360.0, 0.88, 450.0, 810.0)
        val r = RecoveryScorer.score(ScoreInput(s, 52.0, 1.42), base)
        val b = FeedbackEngine.breakdown(r.sTst, r.sSe, r.sReg, r.sHr, r.sLoad)
        assertEquals(r.rawScore - Params.SCORE_BASE, b.totalPoints, 1e-9)
        assertEquals(b.totalPoints, b.factors.sumOf { it.points }, 1e-9)
        // 감점이 큰 순서로 정렬
        assertEquals(b.axes.sortedBy { it.points }, b.axes)
        assertFalse(b.clipped)
    }

    @Test fun `U-18 점수 분해 - 심박 결측 시 재배분 (U-07 예시)`() {
        val s = sleep(360.0, 0.88, 450.0, 810.0)
        val r = RecoveryScorer.score(ScoreInput(s, null, 1.42), base)
        val b = FeedbackEngine.breakdown(r.sTst, r.sSe, r.sReg, r.sHr, r.sLoad)
        assertEquals(r.rawScore - Params.SCORE_BASE, b.totalPoints, 1e-9)
        assertTrue(b.axes.none { it.axis == Axis.HR })
    }

    @Test fun `U-19 조언 - 상한 C1이 먼저, 같은 지표의 일반 조언은 중복하지 않음 (U-08 예시)`() {
        val s = sleep(500.0, 0.934, 380.0, 820.0)
        val r = RecoveryScorer.score(ScoreInput(s, 54.0, 0.70), base)
        val fb = FeedbackEngine.evaluate(input(r, s, 54.0, 0.70))
        assertEquals(1, fb.advices.first().priority)
        assertEquals(Factor.HR, fb.advices.first().factor)
        assertEquals(1, fb.advices.count { it.factor == Factor.HR })
        assertTrue(fb.advices.first().fact.contains("5 bpm"))
    }

    @Test fun `U-20 조언 - 감점이 큰 지표부터, 최대 3개`() {
        // 수면 짧음(−1.5 이하), 부하 1.42, 늦게 잠듦 → 우선순위 2가 여럿
        val s = sleep(360.0, 0.88, 450.0, 810.0)
        val r = RecoveryScorer.score(ScoreInput(s, 52.0, 1.42), base)
        val fb = FeedbackEngine.evaluate(input(r, s, 52.0, 1.42))
        assertTrue(fb.advices.size <= FeedbackEngine.MAX_ADVICES)
        assertTrue(fb.advices.all { it.priority == 2 })
        assertEquals(fb.advices.sortedBy { it.impact }, fb.advices)
        assertTrue(fb.advices.any { it.factor == Factor.LOAD && it.fact.contains("1.42") })
    }

    @Test fun `U-21 조언 - 평소와 같은 날은 유지 조언 1개, 활동 추천은 등급별`() {
        val s = sleep(440.0, 0.91, 415.0, 855.0)
        val r = RecoveryScorer.score(ScoreInput(s, 49.0, 1.1), base)
        val fb = FeedbackEngine.evaluate(input(r, s, 49.0, 1.1))
        assertEquals(1, fb.advices.size)
        assertNull(fb.advices.first().factor)
        assertEquals(FeedbackEngine.activity(Grade.COMFORT), fb.activity)
    }

    @Test fun `U-22 Gemini 결과 검사 - 새 숫자·금지어·길이`() {
        val advices = listOf(Advice(2, -5.0, Factor.LOAD, "최근 7일 운동량이 평소의 1.42배예요.", "오늘은 강도를 낮춰 보세요."))
        val prompt = GeminiPrompt.build(Grade.NORMAL, 41.0, advices)
        assertNotNull(GeminiPrompt.validate("최근 7일 운동량이 평소의 1.42배로 늘었어요. 오늘은 **강도를 낮춰** 보세요.", prompt))
        assertNull(GeminiPrompt.validate("운동량이 평소의 2배예요.", prompt))                 // 없는 숫자
        assertNull(GeminiPrompt.validate("병원에서 진단을 받아 보세요.", prompt))               // 금지어
        assertNull(GeminiPrompt.validate("가".repeat(GeminiPrompt.MAX_CHARS + 1), prompt))   // 너무 김
        assertNull(GeminiPrompt.validate(null, prompt))
        // 보내는 내용은 점수·등급과 사실/권장 문장뿐 (원래 측정값 없음)
        assertFalse(prompt.contains("bpm"))
    }
}
