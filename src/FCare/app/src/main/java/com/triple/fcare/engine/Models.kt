package com.triple.fcare.engine

/*
 * 계산 엔진이 주고받는 데이터 형태.
 * Health Connect·Room 클래스에 의존하지 않도록 엔진 전용 타입으로 정의한다 (기본설계서 2.2).
 */

enum class Sex { MALE, FEMALE }

/** 평소 운동 빈도 (PRF-04). sessionsPerWeek는 초기 CTL 추정에 쓰는 주당 횟수 중간값. */
enum class ExerciseFreq(val label: String, val sessionsPerWeek: Double) {
    W1_2("주 1~2회", 1.5),
    W3_4("주 3~4회", 3.5),
    W5_PLUS("주 5회 이상", 5.5),
}

/** 심박 샘플 한 개 (시각은 epoch 밀리초). */
data class HrSample(val timeMillis: Long, val bpm: Double)

/** 수면 단계 종류 — Health Connect 단계를 엔진용으로 단순화. */
enum class StageType { AWAKE, SLEEPING, OUT_OF_BED, LIGHT, DEEP, REM, AWAKE_IN_BED, UNKNOWN }

data class SleepStage(val startMillis: Long, val endMillis: Long, val type: StageType)

/** 수면 세션 원자료 (상세 3.1). */
data class SleepSessionRaw(
    val startMillis: Long,
    val endMillis: Long,
    val stages: List<SleepStage>,
    val source: String = "",
)

/** 수면 일별 요약 (상세 2.3). 시각은 18:00 기준 경과 분. */
data class SleepSummary(
    val inBedMin: Double,
    val tstMin: Double,
    val efficiency: Double?,   // 수면 단계 기록이 없으면 null
    val onsetMin: Double,
    val wakeMin: Double,
    val hasStages: Boolean,
)

/** 운동 1건의 TRIMP 결과 (상세 3.4). */
enum class TrimpMethod { SAMPLE, AVERAGE }

data class TrimpResult(
    val trimp: Double?,          // 심박 없음이면 null (EXE-11)
    val method: TrimpMethod?,
    val avgHr: Double?,
    val sampleCount: Int,
)

/** 운동 부하 상태 (상세 2.6). */
data class LoadState(val atl: Double, val ctl: Double) {
    val acwr: Double get() = atl / maxOf(ctl, Params.CTL_MIN)
}

/** 기준선 계산에 쓰는 과거 하루 기록. */
data class DayRecord(
    val tstMin: Double,
    val efficiency: Double?,
    val onsetMin: Double,
    val wakeMin: Double,
    val minHr: Double?,
    val excludedByC1: Boolean = false,
)

enum class BaselineStatus { BUILDING, READY }

/** 개인 기준선 (상세 2.7, 4.4). 표준편차는 하한 적용 전 값이며 정규화할 때 하한을 적용한다. */
data class Baseline(
    val nNights: Int,
    val tstMean: Double,
    val tstSd: Double,
    val seMean: Double,
    val seSd: Double,
    val onsetMedian: Double?,  // 기록이 하나도 없으면 null → 규칙성 지표 제외
    val wakeMedian: Double?,
    val timingSd: Double,
    val nHr: Int,
    val hrMean: Double?,       // nHr < 3이면 null → 심박 축 제외
    val hrSd: Double?,
) {
    val status: BaselineStatus
        get() = if (nNights < Params.BUILDING_DAYS) BaselineStatus.BUILDING else BaselineStatus.READY
}

/** 점수 날짜 하루의 계산 입력. */
data class ScoreInput(
    val sleep: SleepSummary,
    val minHr: Double?,   // 수면 중 최저 심박. 없으면 null
    val acwr: Double?,    // 어제(d−1)의 ACWR. 운동·심박 권한이 없으면 null
    /** [수정 3] 어젯밤 포함 최근 3일 총 수면 시간 평균(분). null이면 어젯밤 값만 사용 */
    val tstAvg3: Double? = null,
    /** [수정 2] 워치를 착용한 날(부하 기록)이 14일 미만이면 true → C2 끔, 부하 점수 하한 −1 */
    val loadBuilding: Boolean = false,
)

enum class ScoreStatus { CONFIRMED, PARTIAL, NO_DATA }

enum class CapRule(val message: String) {
    C1("수면 중 심박이 평소보다 많이 높아요. 오늘은 컨디션을 살펴 주세요."),
    C2("최근 운동량이 평소보다 많이 늘었어요. 오늘은 회복에 집중해 보세요."),
    C3("어젯밤 잠이 많이 부족했어요. 오늘은 무리하지 마세요."),
}

/** 등급과 안내 문구 (기본설계서 5.4, HOM-03). */
enum class Grade(val label: String, val message: String) {
    TIRED("피로함", "오늘 운동은 자제하는 게 좋아요!"),
    NORMAL("보통", "무리하지 말고 가볍게 움직여 보세요."),
    COMFORT("편안함", "평소처럼 운동해도 좋은 컨디션이에요."),
    LIGHT("가벼움", "몸이 가벼워요. 계획한 운동을 해 보세요!");

    companion object {
        /** 등급은 반올림 전 점수로 판정한다 (상세 4.7). */
        fun of(score: Double): Grade = when {
            score < 25.0 -> TIRED
            score < 50.0 -> NORMAL
            score < 75.0 -> COMFORT
            else -> LIGHT
        }
    }
}

/** 회복 점수 계산 결과 (상세 2.8). */
data class ScoreResult(
    val status: ScoreStatus,
    val score: Double,
    val rawScore: Double,
    val grade: Grade,
    val sTst: Double,
    val sSe: Double?,
    val sReg: Double?,
    val sSleep: Double,
    val sHr: Double?,
    val sLoad: Double?,
    val rIndex: Double,
    val capRules: List<CapRule>,
    val baselineStatus: BaselineStatus,
    val loadBuilding: Boolean = false,
) {
    /** 홈 화면 한 줄 안내: 상한이 걸리면 이유 문구, 아니면 등급 문구. */
    val message: String get() = capRules.firstOrNull()?.message ?: grade.message
}
