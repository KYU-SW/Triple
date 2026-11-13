package com.triple.fcare.work

import android.content.Context
import com.triple.fcare.data.AppDatabase
import com.triple.fcare.data.BaselineDayEntity
import com.triple.fcare.data.ExerciseSessionEntity
import com.triple.fcare.data.HrDayEntity
import com.triple.fcare.data.LoadDayEntity
import com.triple.fcare.data.ProfileEntity
import com.triple.fcare.data.RecoveryScoreEntity
import com.triple.fcare.data.SleepDayEntity
import com.triple.fcare.engine.Baseline
import com.triple.fcare.engine.BaselineCalculator
import com.triple.fcare.engine.CapRule
import com.triple.fcare.engine.DayRecord
import com.triple.fcare.engine.ExerciseFreq
import com.triple.fcare.engine.HeartRateMetrics
import com.triple.fcare.engine.LoadModel
import com.triple.fcare.engine.LoadState
import com.triple.fcare.engine.Params
import com.triple.fcare.engine.RecoveryScorer
import com.triple.fcare.engine.ScoreInput
import com.triple.fcare.engine.ScoreResult
import com.triple.fcare.engine.ScoreStatus
import com.triple.fcare.engine.Sex
import com.triple.fcare.engine.SleepMetrics
import com.triple.fcare.engine.SleepSessionRaw
import com.triple.fcare.engine.SleepSummary
import com.triple.fcare.engine.TrimpCalculator
import com.triple.fcare.hc.HcAvailability
import com.triple.fcare.hc.HcPermissions
import com.triple.fcare.hc.HcReader
import com.triple.fcare.hc.HealthConnectManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** 날짜 규칙 (기본설계서 4.3, 상세설계서 3.5·3.6). */
object DateRules {
    /** 04:00 이전에는 전날을 점수 날짜로 본다. */
    fun scoreDate(now: ZonedDateTime): LocalDate =
        if (now.hour < Params.DAY_START_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()

    /** 점수 날짜 d의 12:00이 지났는가 (HOM-07 마감). */
    fun isPastDeadline(now: ZonedDateTime, d: LocalDate): Boolean =
        !now.isBefore(d.atTime(Params.SLEEP_DEADLINE_HOUR, 0).atZone(now.zone))

    fun key(d: LocalDate): String = d.toString()   // "yyyy-MM-dd"
}

/** 오늘(점수 날짜) 회복 점수의 현재 상태 (기본설계서 3.4). */
sealed interface TodayState {
    /** 확정·일부 데이터·데이터 부족. isNew = 이번 호출에서 새로 확정됨 (알림·조언 생성용) */
    data class Done(val score: RecoveryScoreEntity, val isNew: Boolean = false) : TodayState
    data class Waiting(val previous: RecoveryScoreEntity?) : TodayState     // 대기 (저장하지 않음)
    data object NeedPermission : TodayState                                 // 수면 권한 없음
    data object HcUnavailable : TodayState                                  // Health Connect 미설치
    data object NoProfile : TodayState
    data object Skipped : TodayState                                        // 백그라운드에서 읽기 불가
}

/**
 * 점수 확정 흐름 (상세설계서 3.5 ScoreCoordinator).
 * 앱 실행, [다시 불러오기], SyncWorker, DeadlineWorker가 모두 이 클래스를 호출한다.
 *  1) 어제까지 운동 부하 갱신  2) 수면·심박 요약 저장  3) 기준선 계산  4) 점수 계산·저장
 */
class ScoreCoordinator(private val context: Context) {
    private val dao = AppDatabase.get(context).dao()
    private val hc = HealthConnectManager(context)
    private val zone: ZoneId = ZoneId.systemDefault()

    suspend fun run(now: ZonedDateTime = ZonedDateTime.now(zone), background: Boolean = false): TodayState {
        val state = compute(now, background)
        // 개선사항 2·4: 새로 확정된 점수면 조언 문장(Gemini)을 만들고, 백그라운드면 알림을 보낸다 (잠금 밖에서 — 네트워크 대기)
        if (state is TodayState.Done && state.isNew && state.score.score != null) {
            runCatching { FeedbackService(context).onConfirmed(LocalDate.parse(state.score.date), background) }
        }
        return state
    }

    private suspend fun compute(now: ZonedDateTime, background: Boolean): TodayState {
        return lock.withLock {
            val profile = dao.profile()?.takeIf { it.onboardingDone } ?: return TodayState.NoProfile
            val d = DateRules.scoreDate(now)
            dao.score(DateRules.key(d))?.let { existing ->
                // 이미 확정된 날은 점수를 다시 계산하지 않는다.
                // 단, DB 업데이트(1→2)나 초기화로 운동 부하 표가 비어 있으면 표시용으로 다시 채운다 (점수는 그대로)
                if (dao.latestLoad() == null) rebuildLoads(d, profile, background)
                return TodayState.Done(existing)
            }

            if (hc.availability() != HcAvailability.AVAILABLE) return TodayState.HcUnavailable
            val granted = hc.grantedPermissions()
            if (HcPermissions.SLEEP !in granted) return TodayState.NeedPermission
            if (background && HcPermissions.BACKGROUND !in granted) return TodayState.Skipped

            val reader = HcReader(hc.client, zone)
            val hasHr = HcPermissions.HEART_RATE in granted
            val hasExercise = HcPermissions.EXERCISE in granted

            // 1) 기준선용 과거 수면 채우기 (처음 실행 시 28일, 이후에는 어제만)
            backfillSleep(reader, d, hasHr)

            // 2) 운동 부하 (운동·심박 권한이 모두 있어야 TRIMP 계산 가능)
            val acwr = if (hasHr && hasExercise) updateLoads(reader, d, profile) else null

            // 3) 오늘 점수 날짜의 수면
            val session = reader.readMainSleep(d)
            if (session == null) {
                if (!DateRules.isPastDeadline(now, d)) return TodayState.Waiting(dao.previousScored(DateRules.key(d)))
                val noData = RecoveryScoreEntity(
                    date = DateRules.key(d), status = ScoreStatus.NO_DATA.name, score = null, rawScore = null,
                    grade = null, sTst = null, sSe = null, sReg = null, sSleep = null, sHr = null, sLoad = null,
                    rIndex = null, capRules = null, baselineStatus = null, confirmedAt = System.currentTimeMillis(),
                )
                dao.upsertScore(noData)
                return TodayState.Done(noData, isNew = true)
            }
            val summary = saveSleep(reader, d, session, hasHr)
            val minHr = dao.hr(DateRules.key(d))?.minHr

            // [수정 3] 최근 3일(어젯밤 포함) 총 수면 시간 평균
            val tstAvg3 = dao.sleepRange(
                DateRules.key(d.minusDays(Params.MIN_SLEEP_DAYS - 1L)), DateRules.key(d),
            ).map { it.tstMin }.average()
            // [수정 2] 워치를 착용한 날이 14일 미만이면 운동 기준선을 쌓는 중
            val loadBuilding = acwr != null &&
                dao.wornLoadDays(DateRules.key(d.minusDays(1))) < Params.LOAD_BUILDING_DAYS

            // 4) 기준선 (전날까지 28일) → 점수
            val baseline = BaselineCalculator.compute(history(d))
            dao.upsertBaseline(baseline.toEntity(d))
            val result = RecoveryScorer.score(
                ScoreInput(summary, minHr, acwr, tstAvg3 = tstAvg3, loadBuilding = loadBuilding), baseline,
            )
            val entity = result.toEntity(d)
            dao.upsertScore(entity)
            TodayState.Done(entity, isNew = true)
        }
    }

    /** 수면 일별 요약(+수면 중 심박) 저장. */
    private suspend fun saveSleep(reader: HcReader, d: LocalDate, session: SleepSessionRaw, hasHr: Boolean): SleepSummary {
        val s = SleepMetrics.summarize(session, zone)
        val now = System.currentTimeMillis()
        dao.upsertSleep(
            SleepDayEntity(
                date = DateRules.key(d), sessionStart = session.startMillis, sessionEnd = session.endMillis,
                onsetMin = s.onsetMin, wakeMin = s.wakeMin, inBedMin = s.inBedMin, tstMin = s.tstMin,
                efficiency = s.efficiency, hasStages = s.hasStages, source = session.source, syncedAt = now,
            )
        )
        if (hasHr) {
            val hr = reader.readHeartRate(session.startMillis, session.endMillis)
            dao.upsertHr(
                HrDayEntity(
                    date = DateRules.key(d), minHr = HeartRateMetrics.sleepMinHr(hr), rawMinHr = HeartRateMetrics.rawMin(hr),
                    sampleCount = hr.size, medianIntervalSec = HeartRateMetrics.medianIntervalSec(hr), syncedAt = now,
                )
            )
        }
        return s
    }

    /**
     * 기준선에 쓸 과거 수면을 Health Connect에서 채운다.
     * Health Connect는 권한을 처음 허용한 시점 기준 최근 30일까지 읽을 수 있으므로,
     * 처음 실행할 때 28일치를 채우면 첫날부터 개인 기준선을 쓸 수 있다.
     */
    private suspend fun backfillSleep(reader: HcReader, d: LocalDate, hasHr: Boolean) {
        val from = d.minusDays(Params.BASELINE_DAYS.toLong())
        val existing = dao.sleepRange(DateRules.key(from), DateRules.key(d.minusDays(1))).map { it.date }.toSet()
        val days = if (existing.isEmpty()) (1..Params.BASELINE_DAYS).map { d.minusDays(it.toLong()) } else listOf(d.minusDays(1))
        for (day in days) {
            if (DateRules.key(day) in existing) continue
            val session = reader.readMainSleep(day) ?: continue
            saveSleep(reader, day, session, hasHr)
        }
    }

    /** 기준선 계산용 기록 (d−28 ~ d−1). C1이 걸린 날은 제외 표시. */
    private suspend fun history(d: LocalDate): List<DayRecord> {
        val from = DateRules.key(d.minusDays(Params.BASELINE_DAYS.toLong()))
        val to = DateRules.key(d.minusDays(1))
        val hr = dao.hrRange(from, to).associateBy { it.date }
        val c1Days = dao.scoreRange(from, to).filter { it.capRules?.contains(CapRule.C1.name) == true }.map { it.date }.toSet()
        return dao.sleepRange(from, to).map {
            DayRecord(it.tstMin, it.efficiency, it.onsetMin, it.wakeMin, hr[it.date]?.minHr, excludedByC1 = it.date in c1Days)
        }
    }

    /**
     * 어제(d−1)까지 날짜별 운동 부하를 갱신하고 어제의 ACWR을 돌려준다 (상세설계서 4.2).
     * 마지막으로 계산한 날도 다시 계산한다 (늦게 동기화된 운동 반영).
     */
    private suspend fun updateLoads(reader: HcReader, d: LocalDate, profile: ProfileEntity): Double? {
        val target = d.minusDays(1)
        val latest = dao.latestLoad()
        val freq = runCatching { ExerciseFreq.valueOf(profile.exerciseFreq) }.getOrDefault(ExerciseFreq.W3_4)

        var day: LocalDate
        var state: LoadState
        if (latest == null) {
            // 처음: 28일 전부터 Health Connect 과거 기록으로 워밍업. 시작값은 운동 빈도로 추정
            day = target.minusDays(Params.BASELINE_DAYS.toLong() - 1)
            state = LoadModel.initial(freq)
        } else {
            day = minOf(LocalDate.parse(latest.date), target)
            val prev = dao.load(DateRules.key(day.minusDays(1)))
            state = prev?.let { LoadState(it.atl, it.ctl) } ?: LoadModel.initial(freq)
        }
        if (day.isBefore(target.minusDays(60))) day = target.minusDays(60)   // 오래 안 쓴 경우 읽는 범위 제한

        while (!day.isAfter(target)) {
            // [수정 2] 워치 심박 기록이 없는 날(연동 전·충전·미착용)은 "운동 0"이 아니라 "데이터 없음":
            // ATL·CTL을 갱신하지 않고 전날 값을 그대로 둔다.
            val worn = reader.hasHeartRate(day)
            val sum = if (worn) dayTrimp(reader, day, profile) else 0.0
            if (worn) state = LoadModel.next(state, sum)
            dao.upsertLoad(
                LoadDayEntity(DateRules.key(day), sum, state.atl, state.ctl, state.acwr, state.atl - state.ctl, worn = worn)
            )
            day = day.plusDays(1)
        }
        return dao.load(DateRules.key(target))?.acwr
    }

    /**
     * 운동 부하 표(load_day)가 비어 있을 때 최근 28일을 Health Connect에서 다시 계산해 채운다.
     * 오늘 점수가 이미 확정된 뒤 앱을 업데이트한 경우에도 기록 탭·홈의 ATL이 바로 보이게 하기 위함.
     * 권한이 없거나 읽기에 실패하면 조용히 건너뛰고, 다음 점수 계산 때 다시 시도된다.
     */
    private suspend fun rebuildLoads(d: LocalDate, profile: ProfileEntity, background: Boolean) {
        if (hc.availability() != HcAvailability.AVAILABLE) return
        val granted = runCatching { hc.grantedPermissions() }.getOrNull() ?: return
        if (HcPermissions.HEART_RATE !in granted || HcPermissions.EXERCISE !in granted) return
        if (background && HcPermissions.BACKGROUND !in granted) return
        runCatching { updateLoads(HcReader(hc.client, zone), d, profile) }
    }

    /** 하루 운동의 TRIMP 합계 (심박 없는 운동 제외, EXE-11). */
    private suspend fun dayTrimp(reader: HcReader, day: LocalDate, profile: ProfileEntity): Double {
        val exercises = reader.readExercises(day)
        val hrMax = TrimpCalculator.hrMax(profile.age)
        val sex = if (profile.sex == Sex.FEMALE.name) Sex.FEMALE else Sex.MALE
        // 안정시 심박: 기준선 평균 → 다음 날 아침 최저 심박 → 일반값 (상세 3.4)
        val hrRest = dao.latestBaseline()?.hrMean
            ?: dao.hr(DateRules.key(day.plusDays(1)))?.minHr
            ?: Params.DEFAULT_HR_REST

        val rows = exercises.map { ex ->
            val samples = reader.readHeartRate(ex.startMillis, ex.endMillis)
            val t = TrimpCalculator.forSession(ex.startMillis, ex.endMillis, samples, hrRest, hrMax, sex)
            ExerciseSessionEntity(
                id = ex.id, date = DateRules.key(day), exerciseType = ex.exerciseType,
                startTime = ex.startMillis, endTime = ex.endMillis,
                durationMin = (ex.endMillis - ex.startMillis) / 60_000.0,
                avgHr = t.avgHr, hrSampleCount = t.sampleCount, trimp = t.trimp, trimpMethod = t.method?.name,
            )
        }
        dao.deleteExercises(DateRules.key(day))
        if (rows.isNotEmpty()) dao.upsertExercises(rows)
        return rows.sumOf { it.trimp ?: 0.0 }
    }

    private fun Baseline.toEntity(d: LocalDate) = BaselineDayEntity(
        date = DateRules.key(d), nNights = nNights, tstMean = tstMean, tstSd = tstSd, seMean = seMean, seSd = seSd,
        onsetMedian = onsetMedian, wakeMedian = wakeMedian, timingSd = timingSd, nHr = nHr,
        hrMean = hrMean, hrSd = hrSd, status = status.name,
    )

    private fun ScoreResult.toEntity(d: LocalDate) = RecoveryScoreEntity(
        date = DateRules.key(d), status = status.name, score = score, rawScore = rawScore, grade = grade.name,
        sTst = sTst, sSe = sSe, sReg = sReg, sSleep = sSleep, sHr = sHr, sLoad = sLoad, rIndex = rIndex,
        capRules = capRules.joinToString(",") { it.name }.ifEmpty { null },
        baselineStatus = baselineStatus.name, confirmedAt = System.currentTimeMillis(),
        loadBuilding = loadBuilding,
    )

    companion object {
        /** 앱 화면과 백그라운드 작업이 동시에 계산하지 않도록 잠금. */
        private val lock = Mutex()
    }
}
