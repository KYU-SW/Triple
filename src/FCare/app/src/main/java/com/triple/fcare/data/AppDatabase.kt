package com.triple.fcare.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** 테이블 읽기·쓰기 (날짜 문자열 "yyyy-MM-dd"는 문자열 비교로도 순서가 맞다). */
@Dao
interface FCareDao {
    // ---- 프로필 ----
    @Query("SELECT * FROM profile WHERE id = 1") fun profileFlow(): Flow<ProfileEntity?>
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun profile(): ProfileEntity?
    @Upsert suspend fun upsertProfile(e: ProfileEntity)

    // ---- 수면·심박 ----
    @Upsert suspend fun upsertSleep(e: SleepDayEntity)
    @Query("SELECT * FROM sleep_day WHERE date = :date") suspend fun sleep(date: String): SleepDayEntity?
    @Query("SELECT * FROM sleep_day WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun sleepRange(from: String, to: String): List<SleepDayEntity>

    @Upsert suspend fun upsertHr(e: HrDayEntity)
    @Query("SELECT * FROM hr_day WHERE date = :date") suspend fun hr(date: String): HrDayEntity?
    @Query("SELECT * FROM hr_day WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun hrRange(from: String, to: String): List<HrDayEntity>

    // ---- 운동·부하 ----
    @Upsert suspend fun upsertExercises(list: List<ExerciseSessionEntity>)
    @Query("DELETE FROM exercise_session WHERE date = :date") suspend fun deleteExercises(date: String)
    @Query("SELECT * FROM exercise_session WHERE date = :date ORDER BY start_time")
    suspend fun exercises(date: String): List<ExerciseSessionEntity>

    @Upsert suspend fun upsertLoad(e: LoadDayEntity)
    @Query("SELECT * FROM load_day WHERE date = :date") suspend fun load(date: String): LoadDayEntity?
    @Query("SELECT * FROM load_day ORDER BY date DESC LIMIT 1") suspend fun latestLoad(): LoadDayEntity?
    @Query("SELECT * FROM load_day WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun loadRange(from: String, to: String): List<LoadDayEntity>
    /** [수정 2] 워치를 착용한(부하가 실제로 기록된) 날 수 */
    @Query("SELECT COUNT(*) FROM load_day WHERE worn = 1 AND date <= :to") suspend fun wornLoadDays(to: String): Int

    // ---- 기준선·회복 점수 ----
    @Upsert suspend fun upsertBaseline(e: BaselineDayEntity)
    @Query("SELECT * FROM baseline_day WHERE date = :date") suspend fun baseline(date: String): BaselineDayEntity?
    @Query("SELECT * FROM baseline_day ORDER BY date DESC LIMIT 1") suspend fun latestBaseline(): BaselineDayEntity?

    @Upsert suspend fun upsertScore(e: RecoveryScoreEntity)
    @Query("SELECT * FROM recovery_score WHERE date = :date") suspend fun score(date: String): RecoveryScoreEntity?
    @Query("SELECT * FROM recovery_score WHERE date < :date AND score IS NOT NULL ORDER BY date DESC LIMIT 1")
    suspend fun previousScored(date: String): RecoveryScoreEntity?
    @Query("SELECT * FROM recovery_score WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun scoreRange(from: String, to: String): List<RecoveryScoreEntity>
    @Query("SELECT * FROM recovery_score ORDER BY date") fun allScoresFlow(): Flow<List<RecoveryScoreEntity>>

    // ---- 기간 요약·내보내기 (개선사항 7, 1) ----
    @Query("SELECT COUNT(*) FROM exercise_session WHERE date BETWEEN :from AND :to") suspend fun exerciseCount(from: String, to: String): Int
    @Query("SELECT * FROM recovery_score ORDER BY date") suspend fun allScores(): List<RecoveryScoreEntity>
    @Query("SELECT * FROM sleep_day ORDER BY date") suspend fun allSleep(): List<SleepDayEntity>
    @Query("SELECT * FROM hr_day ORDER BY date") suspend fun allHr(): List<HrDayEntity>
    @Query("SELECT * FROM load_day ORDER BY date") suspend fun allLoads(): List<LoadDayEntity>

    // ---- Gemini 조언 (개선사항 2) ----
    @Upsert suspend fun upsertAdvice(e: AdviceDayEntity)
    @Query("SELECT * FROM advice_day WHERE date = :date") suspend fun advice(date: String): AdviceDayEntity?
}

/** 기기 안의 로컬 데이터베이스 (기본설계서 4.1: Room 채택). */
@Database(
    entities = [
        ProfileEntity::class, SleepDayEntity::class, HrDayEntity::class, ExerciseSessionEntity::class,
        LoadDayEntity::class, BaselineDayEntity::class, RecoveryScoreEntity::class, AdviceDayEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FCareDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * 1 → 2 ([수정 2]): load_day에 worn 열 추가, recovery_score에 load_building 열 추가.
         * 기존 load_day는 연동 전 기간을 "운동 0"으로 계산한 잘못된 값이라 지우고 다시 계산하게 한다.
         * 이미 확정된 회복 점수(recovery_score)는 설계대로 바꾸지 않는다.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `load_day`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `load_day` (`date` TEXT NOT NULL, `trimp_sum` REAL NOT NULL, " +
                        "`atl` REAL NOT NULL, `ctl` REAL NOT NULL, `acwr` REAL NOT NULL, `atl_minus_ctl` REAL NOT NULL, " +
                        "`worn` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
                db.execSQL("ALTER TABLE `recovery_score` ADD COLUMN `load_building` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 2 → 3 (개선사항 2): Gemini 조언 문장 테이블 추가. 기존 데이터는 그대로 둔다. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `advice_day` (`date` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fcare.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
