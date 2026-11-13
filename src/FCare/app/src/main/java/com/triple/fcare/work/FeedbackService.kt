package com.triple.fcare.work

import android.content.Context
import com.triple.fcare.ai.GeminiClient
import com.triple.fcare.data.AdviceDayEntity
import com.triple.fcare.data.AppDatabase
import com.triple.fcare.data.RecoveryScoreEntity
import com.triple.fcare.engine.CapRule
import com.triple.fcare.engine.FeedbackEngine
import com.triple.fcare.engine.FeedbackInput
import com.triple.fcare.engine.FeedbackResult
import com.triple.fcare.engine.GeminiPrompt
import com.triple.fcare.engine.Grade
import java.time.LocalDate

/** 피드백 화면에 보여 줄 하루치 결과 */
data class DayFeedback(
    val date: LocalDate,
    val score: RecoveryScoreEntity,
    val input: FeedbackInput,
    val result: FeedbackResult,
    val aiText: String?,          // Gemini가 다듬은 문장 (없으면 규칙 문장만 표시)
)

/**
 * 피드백 조립 (개선사항 2·4·6).
 * Room에 저장된 점수·수면·심박·부하·기준선을 읽어 FeedbackEngine에 넣고,
 * 설정에 따라 Gemini 문장 다듬기와 점수 알림을 처리한다.
 */
class FeedbackService(context: Context) {
    private val app = context.applicationContext
    private val dao = AppDatabase.get(app).dao()
    private val settings = AppSettings(app)
    private val gemini = GeminiClient()

    val aiAvailable: Boolean get() = gemini.available

    /** 점수 날짜 d의 피드백 입력. 점수가 없거나 "데이터 부족"이면 null */
    suspend fun input(d: LocalDate): Pair<RecoveryScoreEntity, FeedbackInput>? {
        val key = DateRules.key(d)
        val s = dao.score(key) ?: return null
        val score = s.score ?: return null
        val sTst = s.sTst ?: return null
        val grade = s.grade?.let { runCatching { Grade.valueOf(it) }.getOrNull() } ?: Grade.of(score)
        val base = dao.baseline(key)
        val sleep = dao.sleep(key)
        val hr = dao.hr(key)
        val load = dao.load(DateRules.key(d.minusDays(1)))
        suspend fun avgTst(days: Long) = dao.sleepRange(DateRules.key(d.minusDays(days - 1)), key)
            .map { it.tstMin }.takeIf { it.isNotEmpty() }?.average()

        return s to FeedbackInput(
            score = score,
            rawScore = s.rawScore ?: score,
            grade = grade,
            sTst = sTst, sSe = s.sSe, sReg = s.sReg, sHr = s.sHr, sLoad = s.sLoad,
            capRules = s.capRules?.split(",")?.mapNotNull { runCatching { CapRule.valueOf(it) }.getOrNull() }.orEmpty(),
            loadBuilding = s.loadBuilding,
            baselineNights = base?.nNights,
            tstMin = sleep?.tstMin,
            tstMean = base?.tstMean,
            tstAvg3 = avgTst(3),
            tstAvg7 = avgTst(7),
            efficiency = sleep?.efficiency,
            seMean = base?.seMean,
            onsetMin = sleep?.onsetMin,
            onsetMedian = base?.onsetMedian,
            minHr = hr?.minHr,
            hrMean = base?.hrMean,
            acwr = if (s.sLoad != null) load?.acwr else null,
        )
    }

    /**
     * 하루치 피드백. tryAi가 true이고 Gemini 사용에 동의했으며 저장된 문장이 없으면 한 번 만들어 본다.
     * (점수 확정 때 인터넷이 없어 실패한 경우, 피드백 화면을 열 때 다시 시도하기 위함)
     */
    suspend fun feedback(d: LocalDate, tryAi: Boolean): DayFeedback? {
        val (score, fi) = input(d) ?: return null
        val result = FeedbackEngine.evaluate(fi)
        val ai = if (tryAi) ensureAiText(d, fi, result) else storedAiText(d)
        return DayFeedback(d, score, fi, result, ai)
    }

    private suspend fun storedAiText(d: LocalDate): String? =
        if (settings.aiEnabled) dao.advice(DateRules.key(d))?.text else null

    private suspend fun ensureAiText(d: LocalDate, input: FeedbackInput, result: FeedbackResult): String? {
        if (!settings.aiEnabled || !gemini.available) return null
        dao.advice(DateRules.key(d))?.let { return it.text }          // 하루 1회: 이미 만든 문장은 다시 만들지 않음
        val prompt = GeminiPrompt.build(input.grade, input.score, result.advices)
        val text = GeminiPrompt.validate(gemini.generate(GeminiPrompt.SYSTEM, prompt), prompt) ?: return null
        dao.upsertAdvice(AdviceDayEntity(DateRules.key(d), text, gemini.model, System.currentTimeMillis()))
        return text
    }

    /**
     * 점수가 새로 확정된 직후 호출 (ScoreCoordinator).
     * Gemini 문장을 만들어 두고, 백그라운드에서 확정됐으면 알림을 보낸다.
     */
    suspend fun onConfirmed(d: LocalDate, background: Boolean) {
        val fb = runCatching { feedback(d, tryAi = true) }.getOrNull() ?: return
        if (background && settings.notifyEnabled) {
            val line = fb.aiText ?: fb.result.advices.firstOrNull()?.text ?: fb.input.grade.message
            Notifier.showScore(app, fb.input.score, fb.input.grade.label, line, fb.result.activity)
        }
    }
}
