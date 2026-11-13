package com.triple.fcare.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.triple.fcare.engine.Params
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 1시간마다 실행 (상세설계서 3.5 SyncWorker).
 * 04:00~12:00 사이에만 실제로 수면 기록을 확인하고, 들어왔으면 점수를 확정한다.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = ZonedDateTime.now()
        if (now.hour in Params.DAY_START_HOUR until Params.SLEEP_DEADLINE_HOUR) {
            runCatching { ScoreCoordinator(applicationContext).run(now, background = true) }
        }
        return Result.success()
    }
}

/**
 * 매일 12:00에 1회 실행 (상세설계서 3.5 DeadlineWorker).
 * 그때까지 수면 기록이 없으면 그날을 "데이터 부족"으로 확정하고, 다음 날 12:00으로 다시 예약한다.
 */
class DeadlineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { ScoreCoordinator(applicationContext).run(ZonedDateTime.now(), background = true) }
        WorkScheduler.scheduleDeadline(applicationContext)
        return Result.success()
    }
}

/** 백그라운드 작업 예약. 앱이 시작될 때마다 호출해도 중복 예약되지 않는다. */
object WorkScheduler {
    private const val SYNC = "fcare_sync"
    private const val DEADLINE = "fcare_deadline"

    fun scheduleAll(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(Params.SYNC_INTERVAL_MIN, TimeUnit.MINUTES).build(),
        )
        scheduleDeadline(context, ExistingWorkPolicy.KEEP)
    }

    fun scheduleDeadline(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val now = ZonedDateTime.now()
        var next = now.toLocalDate().atTime(Params.SLEEP_DEADLINE_HOUR, 0).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next)
        WorkManager.getInstance(context).enqueueUniqueWork(
            DEADLINE,
            policy,
            OneTimeWorkRequestBuilder<DeadlineWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build(),
        )
    }
}
