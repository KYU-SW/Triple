package com.triple.fcare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.triple.fcare.R
import com.triple.fcare.data.ProfileEntity
import com.triple.fcare.engine.ExerciseFreq
import com.triple.fcare.engine.Sex
import com.triple.fcare.hc.HcAvailability
import com.triple.fcare.hc.HcPermissions
import com.triple.fcare.hc.PERMISSION_PURPOSES
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import com.triple.fcare.ui.theme.Background
import com.triple.fcare.ui.theme.Dim
import com.triple.fcare.ui.theme.ErrorText
import com.triple.fcare.ui.theme.FcText
import com.triple.fcare.ui.theme.Radius
import com.triple.fcare.ui.theme.Space
import com.triple.fcare.ui.theme.SuccessText
import com.triple.fcare.ui.theme.SurfaceWhite
import com.triple.fcare.ui.theme.TextPrimary
import com.triple.fcare.ui.theme.TextSecondary
import com.triple.fcare.ui.theme.TextTertiary

/** 3.1.1 시작화면 (SPL-01): 로고와 앱 이름을 가운데에 표시. */
@Composable
fun SplashScreen() {
    // 시작 화면만 가운데 정렬 (DESIGN.md 정렬 규칙). 연회색 종이 위 흰 원 로고 + 먹색 앱 이름. 로고 자체는 그대로.
    Column(
        Modifier.fillMaxSize().background(Background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(132.dp).clip(CircleShape).background(SurfaceWhite), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(132.dp))
        }
        Spacer(Modifier.height(Space.s6))
        Text("FCare", style = FcText.x3l, color = TextPrimary)
        Text("퍼케어 · 오늘의 회복을 알려 드려요", style = FcText.base, color = TextSecondary)
    }
}

/** 최초 설정 단계 표시 (1/2, 2/2) */
@Composable
private fun StepHeader(step: Int, total: Int, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text("$step / $total", style = FcText.sm.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary)
        LinearProgressIndicator(progress = { step / total.toFloat() }, modifier = Modifier.fillMaxWidth(),
            color = TextPrimary, trackColor = SurfaceWhite, drawStopIndicator = {})
        Spacer(Modifier.height(Space.s2))
        Text(title, style = FcText.x3l, color = TextPrimary)
        Text(subtitle, style = FcText.base, color = TextSecondary)
    }
}

/** 받침이 있으면 "을", 없으면 "를" */
private fun objParticle(word: String): String {
    val c = word.lastOrNull() ?: return "를"
    return if (c in '가'..'힣' && (c - '가') % 28 != 0) "을" else "를"
}

/**
 * 프로필 입력 양식 — 최초 설정(PRF-01~05)과 프로필 수정(SET-01)에서 함께 쓴다.
 */
@Composable
fun ProfileForm(initial: ProfileEntity?, buttonText: String, onSubmit: (String, Int, Sex, ExerciseFreq) -> Unit) {
    var nickname by rememberSaveable { mutableStateOf(initial?.nickname ?: "") }
    var ageText by rememberSaveable { mutableStateOf(initial?.age?.toString() ?: "") }
    var sex by rememberSaveable { mutableStateOf(initial?.sex?.let { Sex.valueOf(it) }) }
    var freq by rememberSaveable { mutableStateOf(initial?.exerciseFreq?.let { runCatching { ExerciseFreq.valueOf(it) }.getOrNull() }) }
    val age = ageText.toIntOrNull()
    val ageOut = ageText.isNotEmpty() && (age == null || age !in 10..99)
    val valid = nickname.isNotBlank() && age != null && age in 10..99 && sex != null && freq != null

    // 이유 줄 (버튼이 꺼져 있을 때만, 항상 한 줄): 글자 입력 항목이 남았으면 그것부터, 선택 항목과 섞지 않음
    val missingText = listOfNotNull("닉네임".takeIf { nickname.isBlank() }, "나이".takeIf { ageText.isEmpty() })
    val missingChoice = listOfNotNull("성별".takeIf { sex == null }, "운동 빈도".takeIf { freq == null })
    val reason = when {
        valid -> null
        missingText.isNotEmpty() -> missingText.joinToString("·").let { it + objParticle(it) + " 입력해 주세요" }
        missingChoice.isNotEmpty() -> missingChoice.joinToString("·").let { it + objParticle(it) + " 선택해 주세요" }
        ageOut -> "나이를 10~99세로 입력해 주세요"
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        OutlinedTextField(nickname, { nickname = it.take(12) }, label = { Text("닉네임") }, singleLine = true,
            shape = Radius.control, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ageText, { ageText = it.filter(Char::isDigit).take(2) }, label = { Text("나이") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = Radius.control, isError = ageOut,
            supportingText = { Text(if (ageOut) "10~99세로 입력해 주세요" else "최대 심박을 추정하는 데 써요") },
            modifier = Modifier.fillMaxWidth())
        Text("성별", style = FcText.sm.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
        ChipGroup(listOf(Sex.MALE, Sex.FEMALE), sex, { if (it == Sex.MALE) "남성" else "여성" }) { sex = it }
        Text("운동 부하 계산에 써요", style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextSecondary)
        Text("평소 운동 빈도", style = FcText.sm.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
        ChipGroup(ExerciseFreq.entries, freq, { it.label }) { freq = it }
        Text("처음 며칠 동안 평소 운동 부하를 추정하는 데 써요", style = FcText.xs.copy(fontWeight = FontWeight.Normal), color = TextSecondary)
        reason?.let { Text(it, style = FcText.xs, color = ErrorText, maxLines = 1, modifier = Modifier.padding(top = Space.s1)) }
        PrimaryButton(buttonText, enabled = valid) { onSubmit(nickname, age!!, sex!!, freq!!) }
    }
}

/** 3.1.2 최초 설정 (PRF-01~05). */
@Composable
fun SetupScreen(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().background(Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s6),
    ) {
        StepHeader(1, 2, "반가워요!", "회복 점수 계산에 필요한 기본 정보를 알려 주세요.")
        AppCard {
            ProfileForm(null, "다음") { n, a, s, f -> vm.saveProfile(n, a, s, f) }  // 저장되면 권한 화면으로 이동 (PRF-05)
        }
    }
}

/** 3.1.3 Health Connect 권한 요청 (PRM-01~04). */
@Composable
fun PermissionScreen(vm: MainViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val granted by vm.granted.collectAsState()
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        asked = true
        vm.refreshPermissions()
    }
    LaunchedEffect(Unit) { vm.refreshPermissions() }
    val availability = vm.hc.availability()
    val permissionOf = mapOf(
        "수면" to HcPermissions.SLEEP, "심박수" to HcPermissions.HEART_RATE,
        "운동" to HcPermissions.EXERCISE, "백그라운드 읽기" to HcPermissions.BACKGROUND,
        "걸음 수 (선택)" to HcPermissions.STEPS,
    )

    Column(
        Modifier.fillMaxSize().background(Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        StepHeader(2, 2, "건강 데이터 연결", "갤럭시 워치 기록을 Health Connect에서 읽어 와요.")

        // PRM-04 데이터별 사용 목적. 허용 여부는 아이콘 색만이 아니라 아이콘 모양 + "허용됨" 글자로 알린다.
        AppCard {
            PERMISSION_PURPOSES.forEachIndexed { i, (name, purpose) ->
                val ok = permissionOf[name]?.let { it in granted } == true
                if (i > 0) ListDivider()
                Row(Modifier.padding(vertical = Space.s3), verticalAlignment = Alignment.Top) {
                    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = null,
                        tint = if (ok) SuccessText else TextTertiary, modifier = Modifier.size(Dim.iconSm))
                    Spacer(Modifier.width(Space.s3))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = FcText.base.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        Text(purpose, style = FcText.sm, color = TextSecondary)
                    }
                    StatusLabel(if (ok) "허용됨" else "허용 안 됨", if (ok) SuccessText else TextSecondary)
                }
            }
        }
        Text("Health Connect 기록은 휴대폰 밖으로 나가지 않아요. AI 문장 다듬기를 켠 경우에만 점수·등급·평소 대비 문장이 " +
            "Gemini로 가요. 기록 내보내기는 직접 고를 때만 해요.", style = FcText.sm, color = TextSecondary)

        when (availability) {
            HcAvailability.AVAILABLE -> PrimaryButton(if (asked) "권한 다시 요청" else "권한 허용하기") { launcher.launch(HcPermissions.ALL) }
            else -> {
                val update = availability == HcAvailability.UPDATE_REQUIRED
                Text(if (update) "Health Connect를 업데이트해 주세요." else "Health Connect가 필요해요.",
                    style = FcText.sm, color = ErrorText)
                PrimaryButton(if (update) "Play 스토어에서 업데이트하기" else "Play 스토어에서 설치하기") {   // PRM-02
                    context.startActivity(vm.hc.storeIntent())
                }
            }
        }

        if (HcPermissions.SLEEP in granted) {
            PrimaryButton("시작하기", onClick = onDone)
        } else if (asked) {
            // PRM-03: 수면 권한이 없으면 점수를 계산할 수 없음을 안내 (두 줄, 각각 한 줄)
            Column {
                Text("수면 권한이 없으면 점수를 낼 수 없어요.", style = FcText.sm, color = ErrorText)
                Text("나중에 프로필에서 다시 허용할 수 있어요.", style = FcText.sm, color = TextSecondary)
            }
            OutlinedButton(onDone, Modifier.fillMaxWidth().height(Dim.button), shape = Radius.control) {
                Text("나중에 하기", style = FcText.base.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
