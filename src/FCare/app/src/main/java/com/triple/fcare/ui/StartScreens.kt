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

/** 3.1.1 시작화면 (SPL-01): 로고와 앱 이름을 가운데에 표시. */
@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(132.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(132.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("FCare", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
        Text("퍼케어 · 오늘의 회복을 알려 드려요", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
    }
}

/** 최초 설정 단계 표시 (1/2, 2/2) */
@Composable
private fun StepHeader(step: Int, total: Int, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$step / $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { step / total.toFloat() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
    val valid = nickname.isNotBlank() && age != null && age in 10..99 && sex != null && freq != null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(nickname, { nickname = it.take(12) }, label = { Text("이름(닉네임)") }, singleLine = true,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ageText, { ageText = it.filter(Char::isDigit).take(2) }, label = { Text("나이") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            supportingText = { Text("최대 심박(220 − 나이) 추정에 사용해요") }, modifier = Modifier.fillMaxWidth())
        Text("성별", style = MaterialTheme.typography.labelLarge)
        ChipGroup(listOf(Sex.MALE, Sex.FEMALE), sex, { if (it == Sex.MALE) "남성" else "여성" }) { sex = it }
        Text("운동 부하 계산(TRIMP)에 사용해요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("평소 운동 빈도", style = MaterialTheme.typography.labelLarge)
        ChipGroup(ExerciseFreq.entries, freq, { it.label }) { freq = it }
        Text("처음 며칠 동안 평소 운동량을 추정하는 데 사용해요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Button(onClick = { onSubmit(nickname, age!!, sex!!, freq!!) }, enabled = valid,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(buttonText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 3.1.2 최초 설정 (PRF-01~05). */
@Composable
fun SetupScreen(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
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
    )

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepHeader(2, 2, "건강 데이터 연결", "갤럭시 워치 기록을 Health Connect에서 읽어 와요.")

        // PRM-04 데이터별 사용 목적
        AppCard {
            PERMISSION_PURPOSES.forEachIndexed { i, (name, purpose) ->
                val ok = permissionOf[name]?.let { it in granted } == true
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null,
                        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text("읽은 데이터는 회복 점수 계산에만 쓰고 휴대폰 밖으로 보내지 않아요.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val buttonMod = Modifier.fillMaxWidth().height(52.dp)
        when (availability) {
            HcAvailability.AVAILABLE -> Button({ launcher.launch(HcPermissions.ALL) }, buttonMod, shape = RoundedCornerShape(14.dp)) {
                Text(if (asked) "권한 다시 요청" else "권한 허용하기", style = MaterialTheme.typography.titleMedium)
            }
            else -> {
                Text(if (availability == HcAvailability.UPDATE_REQUIRED) "Health Connect 업데이트가 필요해요." else "Health Connect가 설치돼 있지 않아요.")
                Button({ context.startActivity(vm.hc.storeIntent()) }, buttonMod, shape = RoundedCornerShape(14.dp)) {
                    Text("Play 스토어에서 설치하기") // PRM-02
                }
            }
        }

        if (HcPermissions.SLEEP in granted) {
            Button(onDone, buttonMod, shape = RoundedCornerShape(14.dp)) { Text("시작하기", style = MaterialTheme.typography.titleMedium) }
        } else if (asked) {
            // PRM-03: 수면 권한이 없으면 점수를 계산할 수 없음을 안내
            Text("수면 권한이 없으면 회복 점수를 계산할 수 없어요. 나중에 프로필 화면에서 다시 허용할 수 있어요.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onDone, buttonMod, shape = RoundedCornerShape(14.dp)) { Text("나중에 하기") }
        }
    }
}
