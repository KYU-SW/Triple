package com.triple.fcare.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room 테이블 정의 — 상세설계서 2장 테이블 정의서와 1:1로 대응한다.
 * 날짜는 "yyyy-MM-dd" 문자열, 시각은 epoch 밀리초.
 */

/** 2.2 profile (프로필) */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val nickname: String,
    val age: Int,
    val sex: String,                                          // "MALE" / "FEMALE"
    @ColumnInfo(name = "exercise_freq") val exerciseFreq: String, // ExerciseFreq 이름
    @ColumnInfo(name = "onboarding_done") val onboardingDone: Boolean,
    @ColumnInfo(name = "guide_seen") val guideSeen: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** 2.3 sleep_day (수면 일별 요약) — date는 점수 날짜 d */
@Entity(tableName = "sleep_day")
data class SleepDayEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "session_start") val sessionStart: Long,
    @ColumnInfo(name = "session_end") val sessionEnd: Long,
    @ColumnInfo(name = "onset_min") val onsetMin: Double,
    @ColumnInfo(name = "wake_min") val wakeMin: Double,
    @ColumnInfo(name = "in_bed_min") val inBedMin: Double,
    @ColumnInfo(name = "tst_min") val tstMin: Double,
    val efficiency: Double?,
    @ColumnInfo(name = "has_stages") val hasStages: Boolean,
    val source: String,
    @ColumnInfo(name = "synced_at") val syncedAt: Long,
)

/** 2.4 hr_day (심박 일별 요약) */
@Entity(tableName = "hr_day")
data class HrDayEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "min_hr") val minHr: Double?,
    @ColumnInfo(name = "raw_min_hr") val rawMinHr: Double?,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    @ColumnInfo(name = "median_interval_sec") val medianIntervalSec: Double?,
    @ColumnInfo(name = "synced_at") val syncedAt: Long,
)

/** 2.5 exercise_session (운동 기록) — date는 운동 시작 날짜 */
@Entity(tableName = "exercise_session", indices = [Index("date")])
data class ExerciseSessionEntity(
    @PrimaryKey val id: String,
    val date: String,
    @ColumnInfo(name = "exercise_type") val exerciseType: Int,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "duration_min") val durationMin: Double,
    @ColumnInfo(name = "avg_hr") val avgHr: Double?,
    @ColumnInfo(name = "hr_sample_count") val hrSampleCount: Int,
    val trimp: Double?,                                          // 심박 없음이면 null (EXE-11)
    @ColumnInfo(name = "trimp_method") val trimpMethod: String?,
)

/** 2.6 load_day (운동 부하 일별 요약) — date는 운동 날짜 */
@Entity(tableName = "load_day")
data class LoadDayEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "trimp_sum") val trimpSum: Double,
    val atl: Double,
    val ctl: Double,
    val acwr: Double,
    @ColumnInfo(name = "atl_minus_ctl") val atlMinusCtl: Double,
    /** [수정 2] 그날 워치 심박 기록이 있었는가. false면 데이터 없음 → ATL·CTL을 전날 값 그대로 둠 */
    val worn: Boolean,
)

/** 2.7 baseline_day (기준선) — date는 점수 날짜 d */
@Entity(tableName = "baseline_day")
data class BaselineDayEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "n_nights") val nNights: Int,
    @ColumnInfo(name = "tst_mean") val tstMean: Double,
    @ColumnInfo(name = "tst_sd") val tstSd: Double,
    @ColumnInfo(name = "se_mean") val seMean: Double,
    @ColumnInfo(name = "se_sd") val seSd: Double,
    @ColumnInfo(name = "onset_median") val onsetMedian: Double?,
    @ColumnInfo(name = "wake_median") val wakeMedian: Double?,
    @ColumnInfo(name = "timing_sd") val timingSd: Double,
    @ColumnInfo(name = "n_hr") val nHr: Int,
    @ColumnInfo(name = "hr_mean") val hrMean: Double?,
    @ColumnInfo(name = "hr_sd") val hrSd: Double?,
    val status: String,                                          // BUILDING / READY
)

/** 2.8 recovery_score (회복 점수 결과) — date는 점수 날짜 d */
@Entity(tableName = "recovery_score")
data class RecoveryScoreEntity(
    @PrimaryKey val date: String,
    val status: String,                                          // CONFIRMED / PARTIAL / NO_DATA
    val score: Double?,
    @ColumnInfo(name = "raw_score") val rawScore: Double?,
    val grade: String?,                                          // Grade 이름
    @ColumnInfo(name = "s_tst") val sTst: Double?,
    @ColumnInfo(name = "s_se") val sSe: Double?,
    @ColumnInfo(name = "s_reg") val sReg: Double?,
    @ColumnInfo(name = "s_sleep") val sSleep: Double?,
    @ColumnInfo(name = "s_hr") val sHr: Double?,
    @ColumnInfo(name = "s_load") val sLoad: Double?,
    @ColumnInfo(name = "r_index") val rIndex: Double?,
    @ColumnInfo(name = "cap_rules") val capRules: String?,       // "C1,C2" 등
    @ColumnInfo(name = "baseline_status") val baselineStatus: String?,
    @ColumnInfo(name = "confirmed_at") val confirmedAt: Long,
    /** [수정 2] 계산 당시 운동 기준선을 쌓는 중이었는가 (착용 14일 미만) */
    @ColumnInfo(name = "load_building", defaultValue = "0") val loadBuilding: Boolean = false,
)

/**
 * 2.9 advice_day (Gemini가 다듬은 조언 문장) — 개선사항 2, date는 점수 날짜 d.
 * 점수가 확정될 때 하루 1회 만들어 저장하고, 그날은 같은 문장을 보여 준다.
 * 규칙 문장은 저장된 점수로 언제든 다시 만들 수 있으므로 저장하지 않는다.
 */
@Entity(tableName = "advice_day")
data class AdviceDayEntity(
    @PrimaryKey val date: String,
    val text: String,
    val model: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
