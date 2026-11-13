package com.triple.fcare.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.triple.fcare.data.AppDatabase
import com.triple.fcare.data.BaselineDayEntity
import com.triple.fcare.data.ExerciseSessionEntity
import com.triple.fcare.data.HrDayEntity
import com.triple.fcare.data.LoadDayEntity
import com.triple.fcare.data.ProfileEntity
import com.triple.fcare.data.RecoveryScoreEntity
import com.triple.fcare.data.SleepDayEntity
import com.triple.fcare.engine.ExerciseFreq
import com.triple.fcare.engine.Factor
import com.triple.fcare.engine.FeedbackEngine
import com.triple.fcare.engine.Grade
import com.triple.fcare.engine.Sex
import com.triple.fcare.hc.HcAvailability
import com.triple.fcare.hc.HealthConnectManager
import com.triple.fcare.work.AppSettings
import com.triple.fcare.work.DateRules
import com.triple.fcare.work.DayFeedback
import com.triple.fcare.work.FeedbackService
import com.triple.fcare.work.ScoreCoordinator
import com.triple.fcare.work.TodayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZonedDateTime

/** 프로필 상태에 따라 시작 화면이 갈린다 (SPL-02). */
sealed interface ProfileState {
    data object Loading : ProfileState
    data object None : ProfileState                            // 최초 실행 → 최초 설정
    data class NeedPermission(val profile: ProfileEntity) : ProfileState  // 프로필만 입력함 → 권한 요청
    data class Ready(val profile: ProfileEntity) : ProfileState
}

/** 하루치 표시 데이터 (홈 요약, 기록 화면의 날짜별 조회). */
data class DayDetail(
    val date: LocalDate,
    val score: RecoveryScoreEntity?,
    val baseline: BaselineDayEntity?,
    val sleep: SleepDayEntity?,
    val hr: HrDayEntity?,
    val load: LoadDayEntity?,                       // 그 전날(d−1)의 운동 부하 = 이 점수에 쓰인 부하
    val exercises: List<ExerciseSessionEntity>,     // 그 전날(d−1)의 운동 목록
)

/** 기록(추이) 화면 데이터 (HIS-01, HIS-02). */
data class HistoryData(
    val dates: List<LocalDate>,
    val scores: List<Double?>,
    val sleepHours: List<Double?>,
    val minHr: List<Double?>,
    val atl: List<Double?>,
    val sleepBand: Pair<Double, Double>?,   // 개인 기준선 평균 ± 표준편차 (시간)
    val hrBand: Pair<Double, Double>?,
)

/** 기간 하나의 요약 (개선사항 7: 주/월 요약) */
data class PeriodStats(
    val from: LocalDate,
    val to: LocalDate,
    val avgScore: Double?,
    val scoredDays: Int,
    val gradeCounts: Map<Grade, Int>,
    val avgSleepMin: Double?,
    val avgMinHr: Double?,
    val exerciseCount: Int,
    val trimpSum: Double,
    val worstFactor: Factor?,      // 가장 자주 가장 큰 감점 요인이었던 지표
    val worstFactorDays: Int,
)

/** 이번 기간과 직전 같은 길이 기간 비교 */
data class PeriodSummary(val current: PeriodStats, val previous: PeriodStats)

/**
 * 화면 상태 관리 (기본설계서 2.2 상태 관리 계층).
 * 화면은 이 ViewModel만 보고, 계산·저장은 ScoreCoordinator와 Room이 한다.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    private val coordinator = ScoreCoordinator(app)
    val hc = HealthConnectManager(app)
    private val settings = AppSettings(app)
    private val feedbackService = FeedbackService(app)

    // ---- 설정 (개선사항 2·4) ----
    private val _aiEnabled = MutableStateFlow(settings.aiEnabled)
    val aiEnabled: StateFlow<Boolean> = _aiEnabled.asStateFlow()
    private val _notifyEnabled = MutableStateFlow(settings.notifyEnabled)
    val notifyEnabled: StateFlow<Boolean> = _notifyEnabled.asStateFlow()
    /** local.properties에 Gemini API 키가 있는가 */
    val aiAvailable: Boolean get() = feedbackService.aiAvailable

    fun setAiEnabled(on: Boolean) { settings.aiEnabled = on; _aiEnabled.value = on }
    fun setNotifyEnabled(on: Boolean) { settings.notifyEnabled = on; _notifyEnabled.value = on }
    /** 알림 권한을 앱 시작 때 한 번만 물어보기 위한 표시 */
    fun shouldAskNotification(): Boolean = !settings.notifyAsked
    fun markNotificationAsked() { settings.notifyAsked = true }

    // ---- 피드백 (개선사항 2·6) ----
    private val _feedback = MutableStateFlow<DayFeedback?>(null)
    val feedback: StateFlow<DayFeedback?> = _feedback.asStateFlow()
    private val _feedbackLoading = MutableStateFlow(false)
    val feedbackLoading: StateFlow<Boolean> = _feedbackLoading.asStateFlow()

    /** 오늘(점수 날짜) 피드백. Gemini 동의 시 저장된 문장이 없으면 한 번 만들어 본다. */
    fun loadFeedback() {
        viewModelScope.launch {
            _feedbackLoading.value = true
            try {
                _feedback.value = feedbackService.feedback(DateRules.scoreDate(ZonedDateTime.now()), tryAi = true)
            } catch (e: Exception) {
                _feedback.value = null
            } finally {
                _feedbackLoading.value = false
            }
        }
    }

    val profileState: StateFlow<ProfileState> = dao.profileFlow()
        .map { p ->
            when {
                p == null -> ProfileState.None
                !p.onboardingDone -> ProfileState.NeedPermission(p)
                else -> ProfileState.Ready(p)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileState.Loading)

    private val _today = MutableStateFlow<TodayState?>(null)
    val today: StateFlow<TodayState?> = _today.asStateFlow()

    private val _todayDetail = MutableStateFlow<DayDetail?>(null)
    val todayDetail: StateFlow<DayDetail?> = _todayDetail.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _granted = MutableStateFlow<Set<String>>(emptySet())
    val granted: StateFlow<Set<String>> = _granted.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 오늘 점수 확인·계산 (앱 실행, [다시 불러오기]). */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            try {
                _today.value = coordinator.run()
                refreshPermissions()
                _todayDetail.value = dayDetail(DateRules.scoreDate(ZonedDateTime.now()))
            } catch (e: Exception) {
                _error.value = "불러오는 중 오류가 났어요: ${e.message}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            _granted.value = if (hc.availability() == HcAvailability.AVAILABLE)
                runCatching { hc.grantedPermissions() }.getOrDefault(emptySet()) else emptySet()
        }
    }

    // ---- 프로필 (PRF, SET-01) ----
    fun saveProfile(nickname: String, age: Int, sex: Sex, freq: ExerciseFreq) {
        viewModelScope.launch {
            val old = dao.profile()
            dao.upsertProfile(
                ProfileEntity(
                    nickname = nickname.trim(), age = age, sex = sex.name, exerciseFreq = freq.name,
                    onboardingDone = old?.onboardingDone ?: false,
                    guideSeen = old?.guideSeen ?: false,
                    createdAt = old?.createdAt ?: System.currentTimeMillis(),
                )
            )
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { dao.profile()?.let { dao.upsertProfile(it.copy(onboardingDone = true)) } }
    }

    fun markGuideSeen(seen: Boolean = true) {
        viewModelScope.launch { dao.profile()?.let { dao.upsertProfile(it.copy(guideSeen = seen)) } }
    }

    /** 저장된 데이터 초기화 (SET-03): 모든 테이블을 비우면 최초 설정부터 다시 시작한다. */
    fun resetData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.clearAllTables() }
            _today.value = null
            _todayDetail.value = null
        }
    }

    // ---- 조회 ----
    suspend fun dayDetail(d: LocalDate): DayDetail {
        val key = DateRules.key(d)
        val prev = DateRules.key(d.minusDays(1))
        return DayDetail(
            date = d,
            score = dao.score(key),
            baseline = dao.baseline(key),
            sleep = dao.sleep(key),
            hr = dao.hr(key),
            load = dao.load(prev),
            exercises = dao.exercises(prev),
        )
    }

    /** 최근 n일 추이 (HIS-01·02). */
    suspend fun history(days: Int): HistoryData {
        val end = DateRules.scoreDate(ZonedDateTime.now())
        val start = end.minusDays(days - 1L)
        val from = DateRules.key(start)
        val to = DateRules.key(end)
        val scores = dao.scoreRange(from, to).associateBy { it.date }
        val sleeps = dao.sleepRange(from, to).associateBy { it.date }
        val hrs = dao.hrRange(from, to).associateBy { it.date }
        val loads = dao.loadRange(DateRules.key(start.minusDays(1)), DateRules.key(end.minusDays(1))).associateBy { it.date }
        val dates = (0 until days).map { start.plusDays(it.toLong()) }
        val base = dao.latestBaseline()
        return HistoryData(
            dates = dates,
            scores = dates.map { scores[DateRules.key(it)]?.score },
            sleepHours = dates.map { sleeps[DateRules.key(it)]?.tstMin?.div(60) },
            minHr = dates.map { hrs[DateRules.key(it)]?.minHr },
            atl = dates.map { loads[DateRules.key(it.minusDays(1))]?.atl },
            sleepBand = base?.let { (it.tstMean - it.tstSd) / 60 to (it.tstMean + it.tstSd) / 60 },
            hrBand = base?.let { b -> if (b.hrMean != null && b.hrSd != null) b.hrMean - b.hrSd to b.hrMean + b.hrSd else null },
        )
    }

    fun clearError() { _error.value = null }

    // ---- 주/월 요약 (개선사항 7) ----
    suspend fun periodSummary(days: Int): PeriodSummary {
        val end = DateRules.scoreDate(ZonedDateTime.now())
        val cur = stats(end.minusDays(days - 1L), end)
        val prev = stats(end.minusDays(2L * days - 1), end.minusDays(days.toLong()))
        return PeriodSummary(cur, prev)
    }

    private suspend fun stats(start: LocalDate, end: LocalDate): PeriodStats {
        val from = DateRules.key(start)
        val to = DateRules.key(end)
        // 점수 날짜 d에 쓰인 운동은 d−1의 운동 → 운동 통계는 하루 앞당긴 기간
        val exFrom = DateRules.key(start.minusDays(1))
        val exTo = DateRules.key(end.minusDays(1))
        val scored = dao.scoreRange(from, to).filter { it.score != null }
        val worst = scored.mapNotNull { s ->
            s.sTst?.let { FeedbackEngine.breakdown(it, s.sSe, s.sReg, s.sHr, s.sLoad).worst?.factor }
        }.groupingBy { it }.eachCount().maxByOrNull { it.value }
        return PeriodStats(
            from = start,
            to = end,
            avgScore = scored.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.average(),
            scoredDays = scored.size,
            gradeCounts = scored.mapNotNull { e -> e.grade?.let { runCatching { Grade.valueOf(it) }.getOrNull() } }
                .groupingBy { it }.eachCount(),
            avgSleepMin = dao.sleepRange(from, to).map { it.tstMin }.takeIf { it.isNotEmpty() }?.average(),
            avgMinHr = dao.hrRange(from, to).mapNotNull { it.minHr }.takeIf { it.isNotEmpty() }?.average(),
            exerciseCount = dao.exerciseCount(exFrom, exTo),
            trimpSum = dao.loadRange(exFrom, exTo).sumOf { it.trimpSum },
            worstFactor = worst?.key,
            worstFactorDays = worst?.value ?: 0,
        )
    }

    // ---- 기록 내보내기 (개선사항 1: 로컬 저장 + 파일 내보내기) ----
    /**
     * 사용자가 고른 위치에 CSV로 저장한다. 익명화: 닉네임·나이·성별은 넣지 않는다.
     * 점수 날짜 d 한 줄에 그날 점수·수면·심박과 전날(d−1) 운동 부하를 담는다.
     * @return 저장한 줄 수, 실패하면 null
     */
    suspend fun exportCsv(uri: Uri): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val scores = dao.allScores().associateBy { it.date }
            val sleeps = dao.allSleep().associateBy { it.date }
            val hrs = dao.allHr().associateBy { it.date }
            val loads = dao.allLoads().associateBy { it.date }
            val dates = (scores.keys + sleeps.keys).sorted()
            fun n(x: Double?, digits: Int = 2) = x?.let { "%.${digits}f".format(java.util.Locale.US, it) } ?: ""
            val sb = StringBuilder("﻿")   // 엑셀에서 한글이 깨지지 않도록 BOM
            sb.append("date,status,score,grade,cap_rules,s_tst,s_se,s_reg,s_sleep,s_hr,s_load,")
            sb.append("tst_min,efficiency,onset,wake,min_hr,prev_day_trimp,prev_day_acwr,prev_day_worn\n")
            for (d in dates) {
                val s = scores[d]
                val sl = sleeps[d]
                val l = loads[LocalDate.parse(d).minusDays(1).toString()]
                sb.append(listOf(
                    d, s?.status ?: "", n(s?.score, 1), s?.grade ?: "", s?.capRules?.replace(",", " ") ?: "",
                    n(s?.sTst), n(s?.sSe), n(s?.sReg), n(s?.sSleep), n(s?.sHr), n(s?.sLoad),
                    n(sl?.tstMin, 0), n(sl?.efficiency, 3),
                    sl?.let { clockFrom18(it.onsetMin) } ?: "", sl?.let { clockFrom18(it.wakeMin) } ?: "",
                    n(hrs[d]?.minHr, 0), n(l?.trimpSum, 1), n(l?.acwr), l?.worn?.let { if (it) "1" else "0" } ?: "",
                ).joinToString(",")).append("\n")
            }
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                it.write(sb.toString().toByteArray(Charsets.UTF_8))
            } ?: return@runCatching null
            dates.size
        }.getOrNull()
    }
}
