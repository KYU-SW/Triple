package com.triple.fcare.hc

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord

/** FCare가 요청하는 Health Connect 권한 (기본설계서 3.1). */
object HcPermissions {
    val SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
    val EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    /** 앱이 꺼져 있을 때 읽기. 지원하지 않는 기기에서는 허용 화면에 나오지 않는다. */
    const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    val ALL: Set<String> = setOf(SLEEP, HEART_RATE, EXERCISE, BACKGROUND)
}

/** Health Connect 사용 가능 여부 (PRM-02). */
enum class HcAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

/**
 * Health Connect 설치·권한 확인 담당 (기본설계서 3.2 권한 요청 흐름).
 * 실제 기록 읽기는 HcReader가 한다.
 */
class HealthConnectManager(private val context: Context) {

    fun availability(): HcAvailability =
        when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
            HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UPDATE_REQUIRED
            else -> HcAvailability.NOT_INSTALLED
        }

    /** 사용 가능할 때만 호출해야 한다. */
    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    /** Health Connect 설치·업데이트를 위해 Play 스토어로 이동하는 Intent (PRM-02). */
    fun storeIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
    }
}
