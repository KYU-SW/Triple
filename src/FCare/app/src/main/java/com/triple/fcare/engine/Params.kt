package com.triple.fcare.engine

/**
 * 회복 점수 계산에 쓰는 모든 상수 (상세설계서 4.8 상수 목록, NFR-06).
 *
 * - "설계값"은 선행 연구가 방향만 뒷받침하고 수치는 팀이 정한 값이다.
 *   통합시험(민감도 분석) 결과에 따라 이 파일의 값만 바꿔 조정한다.
 * - 계산 코드 안에는 숫자를 직접 쓰지 않고 반드시 여기의 이름을 쓴다.
 */
object Params {
    // ---- 가중치 (상세 4.6) ----
    const val W_SLEEP = 0.40   // 수면 축 (설계값: 동일 가중 + 수면 근거 강도 보정)
    const val W_HR = 0.30      // 수면 중 최저 심박 축
    const val W_LOAD = 0.30    // 운동 부하 축
    const val W_TST = 0.50     // 수면 축 안: 총 수면 시간
    const val W_REG = 0.25     // 수면 축 안: 수면 규칙성
    const val W_SE = 0.25      // 수면 축 안: 수면 효율

    // ---- 점수 변환·범위 (상세 4.5, 4.7) ----
    const val SCORE_BASE = 60.0    // 평소와 같은 날(R = 0) = 60점
    const val SCORE_SCALE = 25.0   // 점수 = 60 + 25 × R
    const val S_MIN = -3.0         // 지표 점수 하한
    const val S_MAX_SLEEP = 1.0    // 수면 지표 점수 상한
    const val S_MAX_HR = 0.5       // 최저 심박 점수 상한 (비대칭)
    const val S_MAX_LOAD = 0.5     // 운동 부하 점수 상한 (비대칭)

    // ---- 표준편차 하한 (상세 4.4) ----
    const val SD_FLOOR_TST = 30.0      // 분
    const val SD_FLOOR_SE = 0.02
    const val SD_FLOOR_TIMING = 30.0   // 분
    const val SD_FLOOR_HR = 2.0        // bpm

    // ---- 개인 기준선 (상세 4.4) ----
    const val BASELINE_DAYS = 28       // 기준선 기간 (문헌·업계: Polar 28일)
    const val BUILDING_DAYS = 7        // 7일 미만이면 "기준선 쌓는 중"
    const val SHRINK_K = 7             // 혼합 평균의 일반값 비중
    const val HR_MIN_NIGHTS = 3        // 최저 심박 기준선에 필요한 최소 일수
    const val DEFAULT_TST = 450.0      // 일반 기준값: 총 수면 시간(분)
    const val DEFAULT_TST_SD = 45.0
    const val DEFAULT_SE = 0.90        // 일반 기준값: 수면 효율
    const val DEFAULT_SE_SD = 0.03
    const val DEFAULT_TIMING_SD = 45.0 // 일반 기준값: 시각 표준편차(분)
    const val DEFAULT_HR_REST = 60.0   // TRIMP용 안정시 심박 (기준선·당일값 모두 없을 때)

    // ---- 운동 부하 (상세 4.2, 4.3) ----
    const val ATL_DAYS = 7
    const val CTL_DAYS = 28
    val LAMBDA_ATL = 2.0 / (ATL_DAYS + 1)   // 0.25
    val LAMBDA_CTL = 2.0 / (CTL_DAYS + 1)   // ≈ 0.0690
    const val CTL_MIN = 15.0                // ACWR 분모 하한 (TRIMP/일)
    const val ACWR_REST = 0.8               // 이하: +0.5
    const val ACWR_NORMAL_LOW = 1.0         // 0.8~1.0: +0.5 → 0
    const val ACWR_NORMAL_HIGH = 1.3        // 1.0~1.3: 0
    const val ACWR_FLOOR = 1.6              // 1.3~1.6: 0 → −3, 이상: −3
    const val TRIMP_PER_SESSION = 50.0      // 초기 CTL 추정용 운동 1회 TRIMP (확인 필요)
    // [수정 2] 운동 기준선: 워치를 착용한 날(심박 기록 있는 날)이 14일 미만이면 부하 판단을 약하게
    const val LOAD_BUILDING_DAYS = 14       // 설계값
    const val S_MIN_LOAD_BUILDING = -1.0    // 쌓는 중일 때 운동 부하 점수 하한 (C2도 적용 안 함)

    // ---- TRIMP (상세 3.4) — Banister: 분 × HRr × a × e^(b × HRr) ----
    // [수정 1] 여성의 앞 계수 a는 0.86 (Morton, Fitz-Clarke & Banister 1990)
    const val TRIMP_A_MALE = 0.64
    const val TRIMP_A_FEMALE = 0.86
    const val K_MALE = 1.92
    const val K_FEMALE = 1.67
    const val HR_MAX_BASE = 220
    const val TRIMP_GAP_MAX_MIN = 5.0       // 샘플 간격 상한(분)
    const val SAMPLE_INTERVAL_MAX_SEC = 60.0 // 이보다 듬성하면 평균 심박 방식 (확인 필요)
    const val TRIMP_MIN_SAMPLES = 3

    // ---- 상한 규칙 (상세 4.7) ----
    const val CAP_SCORE = 49.0
    const val C1_Z = 2.0
    const val C1_BPM = 5.0
    const val C2_ACWR = 1.5
    const val C3_Z = -2.5
    const val C3_TST_MIN = 300.0

    // ---- [수정 3] 최소 수면 시간 (분석서 3.2 "개인 평균 + 최소 기준") ----
    const val MIN_SLEEP_MIN = 360.0         // 최근 3일 평균이 6시간 미만이면 절대 기준으로 감점
    const val MIN_SLEEP_DAYS = 3            // 어젯밤 포함 최근 3일
    const val MIN_SLEEP_PER_POINT = 60.0    // 60분 부족마다 지표 점수 −1 (최종 약 −5점)

    // ---- 수면·심박 데이터 처리 (상세 3.1~3.3) ----
    const val SLEEP_MIN_SESSION_MIN = 180L  // 이보다 짧은 세션은 낮잠으로 보고 제외
    const val SLEEP_WINDOW_START_HOUR = 18  // 전날 18:00부터
    const val SLEEP_DEADLINE_HOUR = 12      // 당일 12:00까지 (HOM-07 마감)
    const val HR_MIN_SAMPLES = 10           // 수면 구간 샘플이 이보다 적으면 최저 심박 결측 (확인 필요)
    const val HR_MEDIAN_WINDOW = 3          // 최저 심박 이동 중앙값 창
    const val SYNC_INTERVAL_MIN = 60L       // 백그라운드 확인 간격(분)
    const val DAY_START_HOUR = 4            // 04:00부터 새 점수 날짜
}
