# FCare 유사 서비스 비교와 차별점

> 기준일: 2026-11-24 · 비교 대상 서비스의 내용은 각 회사의 공식 도움말·블로그와 공개 보도를 바탕으로 정리했다. 가격·기능은 바뀔 수 있으므로 최신 정보는 각 공식 페이지를 확인한다.

## 1. 한눈에 보기

| | **FCare** | 삼성 헬스 에너지 점수 | WHOOP Recovery | Oura Readiness | Garmin Body Battery | Fitbit Daily Readiness |
| --- | --- | --- | --- | --- | --- | --- |
| 필요한 것 | 이미 가진 Galaxy Watch + 무료 앱 | Galaxy Watch·Ring | WHOOP 밴드 + 멤버십(구독) | Oura 링 + 멤버십(구독) | Garmin 기기 | Fitbit·Pixel Watch + **Premium 구독** |
| 쓰는 신호 | 수면(시간·효율·규칙성), 수면 중 최저 심박, 운동 부하(TRIMP·ACWR) | 전날 수면·활동, 수면 중 심박·HRV 등 | HRV, 안정시 심박, 수면, 호흡수 | 9개 요인(수면, 최저 심박, HRV 균형, 체온, 활동 균형 등) | HRV, 스트레스, 수면, 활동 | HRV, 안정시 심박, 수면 |
| 계산식·가중치 공개 | ✅ **전부 공개** (가중치·상수·상한 규칙·근거 문헌) | ❌ 비공개 | ❌ 비공개 | ❌ 비공개 (요인 목록만 공개) | ❌ 비공개 | ❌ 비공개 |
| 점수 이유 설명 | ✅ 평소 60점 대비 **지표별 몇 점** 올리고 내렸는지 숫자로 분해, 합계가 점수와 일치 | 요인 표시 | 요인 표시 | 요인별 상태 막대 | 충전·소모 그래프 | 요인 표시 |
| 운동 부하 반영 방식 | 운동 중 심박으로 계산한 TRIMP의 7일·28일 비율을 점수에 직접 반영, 과부하는 상한 규칙 | 전날 활동 | Strain(별도 점수) | 활동 균형 | 활동이 배터리 소모 | (현재 판은 활동 제외) |
| 개인 기준선 | 최근 28일, 처음 7일은 일반값과 섞어 첫날부터 점수 | 비공개 | 비공개 | 최대 2주 학습, 14일 가중 평균 | 비공개 | 4일 착용 후 첫 점수 |
| 데이터 저장 | **기기 안에만** (회원가입·서버 없음) | 삼성 계정 동기화(선택) | 회사 클라우드 | 회사 클라우드 | 회사 클라우드 | 회사 클라우드 |
| 검증 자료 공개 | ✅ 단위시험 31개(GitHub Actions 자동 실행), 독립 재계산, 민감도 분석, 통합시험 결과서 | 비공개 | 일부 연구 홍보 | 일부 연구 홍보 | 비공개 | 비공개 |

## 2. 서비스별 정리

### 삼성 헬스 에너지 점수
- 전날의 수면과 활동을 바탕으로 아침에 컨디션 점수를 보여 준다. 조지아대(UGA)와 함께 개발했고 수면 중 심박·HRV 연구를 참고했다고 밝혔다.
- 계산식과 가중치는 공개하지 않는다. 동료심사로 독립 검증된 자료도 찾지 못했다.
- FCare와 같은 Galaxy Watch 데이터를 쓰지만, **HRV는 Health Connect로 넘겨주지 않아** 다른 앱은 쓸 수 없다(삼성 헬스 운영 답변, 2026-08).

### WHOOP Recovery
- HRV, 안정시 심박, 수면, 호흡수로 매일 아침 0~100% 회복 점수를 내고 초록·노랑·빨강으로 구분한다.
- 밴드는 멤버십(구독)에 포함되는 방식이라 **구독 없이는 점수를 볼 수 없다**.

### Oura Readiness
- 수면, 최저 심박, HRV 균형, 체온, 활동 균형 등 9개 요인을 개인 평균과 비교하고, 평균을 배우는 데 최대 2주가 걸린다.
- 요인 목록은 공개하지만 요인을 어떻게 합산하는지는 공개하지 않는다.

### Garmin Body Battery
- Firstbeat 분석 엔진으로 HRV·스트레스·수면·활동을 읽어 5~100의 “에너지 잔량”을 보여 준다. 정확한 알고리즘은 공개하지 않는다.

### Fitbit Daily Readiness
- HRV, 안정시 심박, 수면으로 점수를 내며, **Fitbit Premium 구독이 있어야** 볼 수 있다. 첫 점수까지 4일 착용이 필요하다.

### 공통점
독립 연구자들이 10개 제조사 14개 종합 점수를 검토한 결과, 산출식을 공개하거나 독립적으로 검증한 곳은 없었다(Doherty 외 2025, *Translational Exercise Biomedicine*).

## 3. FCare의 차별점

1. **열린 계산식**: 가중치(수면 0.40 · 심박 0.30 · 부하 0.30), 지표 범위, 상한 규칙, 기준선 기간까지 모든 값이 `Params.kt` 한 파일과 [상세설계서](../3_설계/FCare%20상세설계서.docx), [근거 조사 보고서](FCare%20회복%20점수%20근거%20조사%20보고서.md)에 공개되어 있다. 비교한 상용 서비스 중 계산식을 공개한 곳은 없다.
2. **이유까지 보여 주는 점수**: 분석 탭에서 “평소 60점에서 수면 시간 −14.7점, 최저 심박 −12.0점, 운동 부하 +3.8점 → 38점”처럼 지표별 기여를 숫자로 보여 준다. 합계가 점수와 정확히 일치하도록 계산 엔진과 같은 식으로 분해한다.
3. **추가 비용 0원**: 이미 가진 Galaxy Watch와 Health Connect만 쓰고, 별도 기기·구독·회원가입이 없다.
4. **운동하는 사람을 위한 부하 반영**: 운동 중 심박으로 계산한 훈련 부하(Banister TRIMP)를 7일·28일 지수가중 평균의 비율로 바꿔 회복 점수에 직접 넣는다. 운동을 쉬는 날은 소폭 가점, 평소보다 크게 늘면 감점·상한으로 알려 준다.
5. **한국어 맞춤 조언**: 정해진 규칙표에 따라 “사실 + 권장 행동” 형태의 조언을 최대 3개 보여 주고, 동의한 경우에만 AI가 문장만 자연스럽게 다듬는다(내용은 규칙이 정함).
6. **안전장치가 있는 점수**: 심박 급상승·부하 급증·심한 수면 부족은 다른 지표가 좋아도 점수를 49점 이하로 묶고 이유를 보여 준다. 최근 3일 평균 수면이 6시간 미만이면 개인 평균과 관계없이 감점한다.
7. **개인정보 최소화**: 건강 데이터는 기기 안에만 저장하고 서버로 보내지 않는다. AI 문장 다듬기는 사용자가 동의한 경우에만, 점수·등급과 “평소 대비” 조언 문장만 보낸다(이름·나이·성별·원래 측정값은 보내지 않음).
8. **검증 과정 공개**: 단위시험 31개를 GitHub Actions가 매번 자동으로 돌리고, 실제 워치 데이터로 앱과 독립 재계산이 0.000점 차이로 일치함을 통합시험 결과서에 공개했다.
9. **확장 가능한 구조**: Android 표준인 Health Connect를 통해 데이터를 읽으므로, 다른 회사 워치도 Health Connect에 수면·심박·운동 기록을 쓰면 같은 방식으로 동작할 수 있다.

## 4. 개발 측면 비교: Android 공식 Health 샘플 ([android/health-samples](https://github.com/android/health-samples))

Google이 제공하는 Health Connect·Health Services 샘플 모음과 FCare 저장소를 비교했다.

| 항목 | android/health-samples | FCare | 반영 |
| --- | --- | --- | --- |
| 목적 | API 사용법 예시 (기록 읽기·쓰기·삭제, 변경 추적) | 실제 문제(회복 판단)를 푸는 완성 앱 | — |
| CI | 샘플마다 GitHub Actions로 빌드·단위시험, 시험 보고서를 아티팩트로 저장 | 없었음 | ✅ 단위시험·커버리지·APK 빌드 워크플로 추가, 보고서 아티팩트 저장 |
| 권한 | 시연용으로 읽기·쓰기 권한 다수 요청 | **읽기 전용 5개**(수면·심박·운동·걸음 수·백그라운드), 필요한 것만 | FCare가 최소 권한 |
| 권한 안내 화면 | `ACTION_SHOW_PERMISSIONS_RATIONALE` + Android 14 `VIEW_PERMISSION_USAGE` | 같은 두 진입점을 별도 설명 화면으로 구현 | 동일 |
| 대량 읽기 | 페이지 토큰 읽기, 집계(aggregate) | 페이지 토큰 읽기, 걸음 수 집계 | 동일 |
| 변경 추적 | Changes API(차등 동기화) 예시 | 날짜별 다시 읽기 | 개선 후보 |
| 시험 | 단위시험 일부, 워치 화면 스크린샷 시험 | 계산 엔진 단위시험 31개(줄 커버리지 95%), 실기기 통합시험 36항목 | FCare가 계산 검증이 더 깊음 |
| README | 준비물(기기·Health Connect 앱), 시험 도구(Health Connect Toolbox), 스크린샷 | 기능·계산식·데이터 흐름·빌드 방법 | ✅ 준비물·시험 도구 안내 추가 |
| 기여 안내 | CONTRIBUTING, 이슈 템플릿, CODEOWNERS | 없었음 | ✅ 기여 안내·이슈 템플릿 추가 |
| 코드 형식 | spotless·.editorconfig | 없었음 | ✅ .editorconfig 추가 |
| 라이선스 | Apache-2.0 | 없음 | 팀 결정 필요 |

## 출처

- 삼성 헬스: [Samsung Health (삼성전자)](https://www.samsung.com/sg/apps/samsung-health/), [UGA 공동 개발 발표](https://coe.uga.edu/news/2024-07-samsung-electronics-uga-to-develop-new-digital-health-experience/)
- WHOOP: [WHOOP 멤버십·회복](https://ww2.whoop.com/experience/recovery/), [WHOOP 자주 묻는 질문](https://www.whoop.com/us/en/thelocker/your-top-questions-answered/)
- Oura: [Readiness Score 도움말](https://support.ouraring.com/hc/en-us/articles/360057791533), [Oura 블로그](https://ouraring.com/blog/readiness-score/)
- Garmin: [Body Battery 매뉴얼](https://www8.garmin.com/manuals-apac/webhelp/instincte/EN-SG/GUID-2561A49C-E230-4F54-A9E8-F97413B6B67C-3505.html), [Garmin 블로그](https://www.garmin.com/en-US/blog/fitness/body-battery-thrive/)
- Fitbit: [Daily Readiness 도움말](https://support.google.com/fitbit/answer/14236710), [Fitbit Daily Readiness](https://www.fitbit.com/global/us/technology/daily-readiness-score)
- 독립 검토: Doherty, Baldwin, Lambe, Burke, Altini 2025, [10.1515/teb-2025-0001](https://www.degruyterbrill.com/document/doi/10.1515/teb-2025-0001/html?lang=en)
- 개발 비교: [android/health-samples](https://github.com/android/health-samples)
