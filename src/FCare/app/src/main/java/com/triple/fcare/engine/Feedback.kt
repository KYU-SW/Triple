package com.triple.fcare.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 피드백 엔진 (개선사항 2·4·6): 회복 점수가 왜 이렇게 나왔는지(감점 요인)와 오늘의 조언을 만든다.
 * - 감점 요인: 점수 = 60 + 25 × R 이므로, R을 이루는 지표별 몫을 "점수(점)"로 바꿔 보여 준다.
 *   (RecoveryScorer와 같은 가중치·같은 결측 재배분을 쓰므로 합계가 원점수와 정확히 맞는다)
 * - 조언: 미리 정한 규칙표에서 조건에 맞는 문장을 고른다. 같은 입력이면 항상 같은 결과 → 단위시험 가능.
 * - Gemini는 규칙이 고른 "내용"을 자연스러운 문장으로 다듬기만 한다 (GeminiPrompt). 실패하면 규칙 문장을 쓴다.
 * Android·Room에 의존하지 않는 순수 Kotlin이다.
 */

/** 감점 요인 지표 */
enum class Factor(val label: String) {
    TST("수면 시간"), REG("수면 규칙성"), SE("수면 효율"), HR("수면 중 최저 심박"), LOAD("운동 부하"),
}

/** 축 (수면 / 심박 / 운동 부하) */
enum class Axis(val label: String) { SLEEP("수면"), HR("수면 중 최저 심박"), LOAD("운동 부하") }

data class FactorPoints(val factor: Factor, val points: Double)

data class AxisPoints(val axis: Axis, val points: Double, val parts: List<FactorPoints>)

/** 점수 분해 결과 */
data class Breakdown(
    val axes: List<AxisPoints>,      // 감점이 큰 순서 (가장 음수인 것이 먼저)
    val totalPoints: Double,         // 25 × R  (평소 60점 대비 차이)
    val clipped: Boolean,            // 60 + 25R 이 0~100 밖이라 잘린 날
) {
    /** 지표 하나하나의 몫 (수면은 세부 지표로 펼침) */
    val factors: List<FactorPoints> get() = axes.flatMap { it.parts }
    /** 가장 큰 감점 요인 (감점이 없으면 null) */
    val worst: FactorPoints? get() = factors.filter { it.points < 0 }.minByOrNull { it.points }
}

/** 피드백 계산 입력: 저장된 점수 결과 + 그날 원래 값 + 기준선 (모두 Room에 있는 값) */
data class FeedbackInput(
    val score: Double,
    val rawScore: Double,
    val grade: Grade,
    val sTst: Double,
    val sSe: Double?,
    val sReg: Double?,
    val sHr: Double?,
    val sLoad: Double?,
    val capRules: List<CapRule>,
    val loadBuilding: Boolean,
    val baselineNights: Int?,
    // 원래 값 (화면 표시용·조언 숫자용)
    val tstMin: Double?,
    val tstMean: Double?,       // 개인 기준선 평균 (28일)
    val tstAvg3: Double?,       // 최근 3일 평균 (수정 3)
    val tstAvg7: Double?,       // 최근 7일 평균 (개선사항 3: 평균 수면을 피드백에 반영)
    val efficiency: Double?,
    val seMean: Double?,
    val onsetMin: Double?,
    val onsetMedian: Double?,
    val minHr: Double?,
    val hrMean: Double?,
    val acwr: Double?,
)

/** 조언 한 줄. fact(사실) + action(권장). priority가 작을수록 먼저, 같으면 impact(점수 몫)가 작을수록 먼저 */
data class Advice(
    val priority: Int,
    val impact: Double,
    val factor: Factor?,
    val fact: String,
    val action: String,
) {
    val text: String get() = "$fact $action"
}

data class FeedbackResult(
    val breakdown: Breakdown,
    val details: Map<Factor, String>,   // 지표별 설명 (원래 값 포함, 화면 전용)
    val advices: List<Advice>,          // 최대 3개
    val notes: List<String>,            // 데이터 상태 안내 (심박 없음, 기준선 쌓는 중 등)
    val activity: String,               // 오늘의 활동 추천 (알림에도 사용)
)

object FeedbackEngine {

    const val MAX_ADVICES = 3
    private const val BAD = -1.0          // 지표 점수 −1 이하 = 평소보다 눈에 띄게 나쁨 (설계값)

    /**
     * 점수 분해. RecoveryScorer와 같은 규칙:
     * 수면 축 = 있는 세부 지표끼리 가중 평균, R = 있는 축끼리 가중 평균, 점수 = 60 + 25R.
     */
    fun breakdown(sTst: Double, sSe: Double?, sReg: Double?, sHr: Double?, sLoad: Double?): Breakdown {
        val sleepParts = listOfNotNull(
            Triple(Factor.TST, Params.W_TST, sTst),
            sReg?.let { Triple(Factor.REG, Params.W_REG, it) },
            sSe?.let { Triple(Factor.SE, Params.W_SE, it) },
        )
        val wSleepSum = sleepParts.sumOf { it.second }
        val wAxisSum = Params.W_SLEEP + (if (sHr != null) Params.W_HR else 0.0) + (if (sLoad != null) Params.W_LOAD else 0.0)
        val k = Params.SCORE_SCALE / wAxisSum

        val sleepFactors = sleepParts.map { (f, w, s) -> FactorPoints(f, k * Params.W_SLEEP * w * s / wSleepSum) }
        val axes = buildList {
            add(AxisPoints(Axis.SLEEP, sleepFactors.sumOf { it.points }, sleepFactors))
            sHr?.let { val p = k * Params.W_HR * it; add(AxisPoints(Axis.HR, p, listOf(FactorPoints(Factor.HR, p)))) }
            sLoad?.let { val p = k * Params.W_LOAD * it; add(AxisPoints(Axis.LOAD, p, listOf(FactorPoints(Factor.LOAD, p)))) }
        }.sortedBy { it.points }
        val total = axes.sumOf { it.points }
        val unclipped = Params.SCORE_BASE + total
        return Breakdown(axes, total, clipped = unclipped < 0.0 || unclipped > 100.0)
    }

    fun evaluate(input: FeedbackInput): FeedbackResult {
        val b = breakdown(input.sTst, input.sSe, input.sReg, input.sHr, input.sLoad)
        val pts = b.factors.associate { it.factor to it.points }
        return FeedbackResult(
            breakdown = b,
            details = details(input),
            advices = advices(input, pts),
            notes = notes(input),
            activity = activity(input.grade),
        )
    }

    /* ---------- 지표별 설명 (화면 전용, 원래 값 포함) ---------- */

    private fun details(i: FeedbackInput): Map<Factor, String> = buildMap {
        if (i.tstMin != null) {
            val s = StringBuilder("어젯밤 ${hm(i.tstMin)}")
            i.tstMean?.let { m -> s.append(" · 평소 ${hm(m)}") }
            i.tstAvg7?.let { a -> s.append("\n최근 7일 평균 ${hm(a)}") }
            if (i.tstAvg3 != null && i.tstAvg3 < Params.MIN_SLEEP_MIN)
                s.append("\n최근 3일 평균 ${hm(i.tstAvg3)} (기준 ${hm(Params.MIN_SLEEP_MIN)} 미만)")
            put(Factor.TST, s.toString())
        }
        if (i.sReg != null && i.onsetMin != null && i.onsetMedian != null) {
            val d = i.onsetMin - i.onsetMedian
            put(Factor.REG, if (abs(d) < 15) "평소와 비슷한 시각에 잠들었어요" else "평소보다 ${hm(abs(d))} ${if (d > 0) "늦게" else "일찍"} 잠들었어요")
        }
        if (i.efficiency != null) {
            put(Factor.SE, "수면 효율 ${pct(i.efficiency)}%" + (i.seMean?.let { " · 평소 ${pct(it)}%" } ?: ""))
        }
        if (i.minHr != null) {
            put(Factor.HR, "최저 ${i.minHr.roundToInt()} bpm" + (i.hrMean?.let { " · 평소보다 ${signedBpm(i.minHr - it)}" } ?: ""))
        }
        i.acwr?.let { put(Factor.LOAD, "최근 7일 운동량이 평소의 ${ratio(it)}배") }
    }

    /* ---------- 조언 규칙표 (사실은 "평소 대비" 표현만 사용 → Gemini로 보내도 원래 측정값은 나가지 않음) ---------- */

    fun advices(i: FeedbackInput, pts: Map<Factor, Double>): List<Advice> {
        val list = mutableListOf<Advice>()
        val caps = i.capRules.toSet()
        fun p(f: Factor) = pts[f] ?: 0.0
        val tstShort = if (i.tstMin != null && i.tstMean != null) i.tstMean - i.tstMin else null
        val hrUp = if (i.minHr != null && i.hrMean != null) i.minHr - i.hrMean else null

        // 우선순위 1: 상한 규칙 (점수를 49점으로 제한한 이유)
        if (CapRule.C2 in caps && i.acwr != null) list += Advice(1, p(Factor.LOAD), Factor.LOAD,
            "최근 7일 운동량이 평소의 ${ratio(i.acwr)}배로 많이 늘었어요.",
            "오늘은 쉬거나 가벼운 스트레칭 정도만 해 보세요.")
        if (CapRule.C1 in caps) list += Advice(1, p(Factor.HR), Factor.HR,
            "수면 중 최저 심박이 평소보다 ${hrUp?.let { "${abs(it).roundToInt()} bpm" } ?: "많이"} 높았어요.",
            "피로·음주·늦은 카페인이 영향을 줄 수 있으니 오늘은 무리하지 마세요.")
        if (CapRule.C3 in caps) list += Advice(1, p(Factor.TST), Factor.TST,
            "어젯밤 수면이 평소보다 크게 부족했어요.",
            "강한 운동은 피하고 오늘 밤은 일찍 잠자리에 들어 보세요.")

        // 우선순위 2: 평소보다 눈에 띄게 나쁜 지표 (상한으로 이미 말한 지표는 제외)
        if (CapRule.C2 !in caps && i.acwr != null && i.acwr > Params.ACWR_NORMAL_HIGH) list += Advice(2, p(Factor.LOAD), Factor.LOAD,
            "최근 7일 운동량이 평소의 ${ratio(i.acwr)}배예요.",
            "오늘은 강도를 낮추거나 가벼운 걷기·스트레칭을 추천해요.")
        if (CapRule.C3 !in caps && i.sTst <= BAD) {
            val fact = if (i.tstAvg3 != null && i.tstAvg3 < Params.MIN_SLEEP_MIN)
                "최근 3일 평균 수면이 기준(${hm(Params.MIN_SLEEP_MIN)})보다 ${hm(Params.MIN_SLEEP_MIN - i.tstAvg3)} 부족해요."
            else if (tstShort != null && tstShort > 0) "어젯밤 수면이 평소보다 ${hm(tstShort)} 짧았어요."
            else "어젯밤 수면이 평소보다 짧았어요."
            list += Advice(2, p(Factor.TST), Factor.TST, fact, "오늘은 평소보다 30분 일찍 잠자리에 들어 보세요.")
        }
        if (CapRule.C1 !in caps && i.sHr != null && i.sHr <= BAD) list += Advice(2, p(Factor.HR), Factor.HR,
            "수면 중 최저 심박이 평소보다 ${hrUp?.let { "${abs(it).roundToInt()} bpm" } ?: "조금"} 높았어요.",
            "물을 충분히 마시고 늦은 카페인·음주는 피해 보세요.")
        if (i.sReg != null && i.sReg <= BAD) {
            val d = if (i.onsetMin != null && i.onsetMedian != null) i.onsetMin - i.onsetMedian else null
            val fact = if (d != null && abs(d) >= 15) "평소보다 ${hm(abs(d))} ${if (d > 0) "늦게" else "일찍"} 잠들었어요."
            else "잠들고 일어난 시각이 평소와 달랐어요."
            list += Advice(2, p(Factor.REG), Factor.REG, fact, "자고 일어나는 시각을 일정하게 맞추면 회복에 도움이 돼요.")
        }
        if (i.sSe != null && i.sSe <= BAD) {
            val drop = if (i.efficiency != null && i.seMean != null) ((i.seMean - i.efficiency) * 100).roundToInt() else null
            list += Advice(2, p(Factor.SE), Factor.SE,
                "누워 있던 시간에 비해 실제로 잔 시간이 평소보다 적었어요" + (drop?.takeIf { it > 0 }?.let { "(효율 ${it}%p 낮음)." } ?: "."),
                "자기 전 휴대폰 사용과 늦은 카페인을 줄여 보세요.")
        }

        // 우선순위 3: 좋은 날 안내
        if (i.acwr != null && i.acwr < Params.ACWR_REST && !i.loadBuilding) list += Advice(3, p(Factor.LOAD), Factor.LOAD,
            "최근 운동량이 평소보다 적어요.",
            "컨디션이 괜찮다면 계획한 운동을 해도 좋아요.")
        if (list.none { it.priority <= 2 }) list += if (i.score >= Params.SCORE_BASE)
            Advice(3, 0.0, null, "수면·심박·운동량이 모두 평소 수준이거나 더 좋아요.", "지금 생활 리듬을 유지해 보세요.")
        else
            Advice(3, 0.0, null, "눈에 띄게 나쁜 항목은 없지만 평소보다 조금 덜 회복했어요.", "오늘은 무리하지 말고 가볍게 움직여 보세요.")

        return list.sortedWith(compareBy<Advice> { it.priority }.thenBy { it.impact }).take(MAX_ADVICES)
    }

    private fun notes(i: FeedbackInput): List<String> = buildList {
        if (i.sHr == null) add("심박 데이터가 없어(또는 기준선 3일 미만) 수면과 운동량만으로 판단했어요.")
        if (i.sLoad == null) add("운동 부하는 심박·운동 권한이 있어야 계산돼요.")
        if (i.baselineNights != null && i.baselineNights < Params.BUILDING_DAYS)
            add("아직 기록이 ${i.baselineNights}일이라 수면은 일반 기준으로 비교했어요.")
        if (i.loadBuilding) add("운동 기준선을 쌓는 중이라 운동 부하는 약하게 반영했어요.")
    }

    /** 오늘의 활동 추천 (개선사항 4: 점수 기반 활동 추천) */
    fun activity(grade: Grade): String = when (grade) {
        Grade.TIRED -> "휴식이나 가벼운 스트레칭 10~20분"
        Grade.NORMAL -> "가벼운 걷기·저강도 운동 30분 이내"
        Grade.COMFORT -> "평소 계획한 운동"
        Grade.LIGHT -> "계획한 운동 (강도를 조금 높여도 좋아요)"
    }

    /* ---------- 표시 형식 ---------- */

    fun hm(min: Double): String {
        val m = min.roundToInt()
        return when {
            m < 60 -> "${m}분"
            m % 60 == 0 -> "${m / 60}시간"
            else -> "${m / 60}시간 ${m % 60}분"
        }
    }

    private fun pct(x: Double) = (x * 100).roundToInt()
    private fun ratio(x: Double) = String.format(Locale.US, "%.2f", x)   // 소수점은 항상 "."
    private fun signedBpm(d: Double): String {
        val r = d.roundToInt()
        return when {
            r > 0 -> "$r bpm 높음"
            r < 0 -> "${-r} bpm 낮음"
            else -> "같음"
        }
    }
}

/**
 * Gemini 문장 다듬기 (사용자 결정 11-12: 규칙이 내용, Gemini는 문장만).
 * - 보내는 내용: 등급·점수·조언의 사실/권장 문장(모두 "평소 대비" 표현). 이름·나이·성별·원래 측정값은 보내지 않는다.
 * - 받은 문장은 [validate]로 검사하고, 통과하지 못하면 규칙 문장을 그대로 쓴다.
 */
object GeminiPrompt {

    const val MAX_CHARS = 300
    /** 진단·치료처럼 들리는 표현 금지 (웰니스 표현 유지, NFR) */
    val BANNED = listOf("진단", "질환", "질병", "치료", "처방", "약물", "복용", "병원", "의사", "환자", "장애")

    val SYSTEM = """
        너는 꾸준히 운동하는 일반인을 위한 회복 코치다.
        아래 [사실]과 [권장]에 있는 내용만 사용해 한국어 존댓말로 3문장 이내의 조언을 써라.
        규칙: 숫자는 주어진 그대로만 쓰고 새 숫자를 만들지 마라. 새로운 사실을 추가하지 마라.
        질병·진단·치료·약 이야기는 하지 마라. 마크다운·목록·이모지 없이 평범한 문장으로만 써라.
    """.trimIndent()

    fun build(grade: Grade, score: Double, advices: List<Advice>): String = buildString {
        appendLine("오늘 회복 점수: ${score.roundToInt()}점 (${grade.label}, 평소는 60점)")
        appendLine("[사실]")
        advices.forEach { appendLine("- ${it.fact}") }
        appendLine("[권장]")
        advices.forEach { appendLine("- ${it.action}") }
    }

    private val NUMBER = Regex("""\d+(?:\.\d+)?""")

    /** 통과하면 다듬은 문장, 아니면 null */
    fun validate(output: String?, prompt: String): String? {
        val text = output?.replace("*", "")?.replace("#", "")?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (text.isEmpty() || text.length > MAX_CHARS) return null
        if (BANNED.any { text.contains(it) }) return null
        val allowed = NUMBER.findAll(prompt).map { it.value }.toSet()
        if (NUMBER.findAll(text).any { it.value !in allowed }) return null   // 새로 만든 숫자 금지
        return text
    }
}
