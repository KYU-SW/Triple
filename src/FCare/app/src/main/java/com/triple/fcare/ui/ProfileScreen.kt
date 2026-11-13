package com.triple.fcare.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.triple.fcare.data.ProfileEntity
import com.triple.fcare.engine.ExerciseFreq
import com.triple.fcare.engine.Sex
import com.triple.fcare.hc.HcPermissions
import com.triple.fcare.work.Notifier
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 3.1.9 프로필·설정 (SET-01~03). */
@Composable
fun ProfileScreen(vm: MainViewModel, profile: ProfileEntity, onOpenCheck: () -> Unit, modifier: Modifier = Modifier) {
    val granted by vm.granted.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refreshPermissions()
    }
    LaunchedEffect(Unit) { vm.refreshPermissions() }

    // ---- 개선사항 4: 알림 / 개선사항 2: Gemini / 개선사항 1: 내보내기 ----
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifyOn by vm.notifyEnabled.collectAsState()
    val aiOn by vm.aiEnabled.collectAsState()
    var canNotify by remember { mutableStateOf(Notifier.canNotify(context)) }
    var confirmAi by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        canNotify = ok
        vm.setNotifyEnabled(ok)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val n = vm.exportCsv(uri)
            exportResult = if (n != null) "${n}일치 기록을 저장했어요." else "저장하지 못했어요. 다른 위치를 골라 주세요."
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- 프로필 요약 + 수정 (SET-01) ----
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text(profile.nickname.take(1), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val sex = if (profile.sex == Sex.FEMALE.name) "여성" else "남성"
                    val freq = runCatching { ExerciseFreq.valueOf(profile.exerciseFreq).label }.getOrDefault("")
                    Text("${profile.age}세 · $sex · $freq", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({ editing = !editing }) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(if (editing) " 닫기" else " 수정")
                }
            }
            if (editing) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                ProfileForm(profile, "저장") { n, a, s, f -> vm.saveProfile(n, a, s, f); editing = false }
                Text("나이·성별을 바꾸면 이후 계산하는 운동 부하부터 반영돼요.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // ---- 권한 상태 (SET-02) ----
        SectionTitle("Health Connect 권한")
        AppCard {
            listOf(
                "수면" to HcPermissions.SLEEP, "심박수" to HcPermissions.HEART_RATE,
                "운동" to HcPermissions.EXERCISE, "백그라운드 읽기" to HcPermissions.BACKGROUND,
            ).forEach { (name, p) ->
                val ok = p in granted
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    StatusLabel(if (ok) "허용됨" else "허용 안 됨",
                        if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
            Button({ launcher.launch(HcPermissions.ALL) }, Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp)) {
                Text("권한 다시 설정")
            }
        }

        // ---- 알림·AI 설정 ----
        SectionTitle("알림 · AI")
        AppCard {
            SwitchRow(
                "점수 알림",
                if (notifyOn && !canNotify) "휴대폰 설정에서 FCare 알림이 꺼져 있어요" else "점수가 확정되면 점수와 오늘 추천 활동을 알려 줘요",
                notifyOn && canNotify,
            ) { on ->
                if (on && !canNotify) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.setNotifyEnabled(on)
            }
            HorizontalDivider()
            SwitchRow(
                "AI 문장 다듬기 (Gemini)",
                if (!vm.aiAvailable) "API 키가 없어 사용할 수 없어요 (local.properties의 gemini.apiKey)"
                else "조언 문장을 Gemini로 자연스럽게 다듬어요. 인터넷이 필요해요",
                aiOn && vm.aiAvailable,
                enabled = vm.aiAvailable,
            ) { on -> if (on) confirmAi = true else vm.setAiEnabled(false) }
        }

        // ---- 데이터 보관·내보내기 ----
        SectionTitle("내 기록")
        AppCard {
            Text("기록은 이 휴대폰 안에만 저장돼요. 휴대폰의 Google 백업이 켜져 있으면 함께 백업되고, " +
                "잠금화면(PIN·패턴)을 설정해 두면 백업도 암호화돼요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(top = 8.dp))
            MenuRow(Icons.Filled.Share, "기록 내보내기 (CSV)") { confirmExport = true }
        }

        // ---- 도움말 ----
        SectionTitle("도움말")
        AppCard {
            MenuRow(Icons.Filled.Info, "워치 착용 안내") { showGuide = true }            // HOM-10
            HorizontalDivider()
            MenuRow(Icons.Filled.Settings, "Health Connect 연동 확인 (시험용)", onClick = onOpenCheck)
        }

        // ---- 데이터 초기화 (SET-03) ----
        AppCard {
            MenuRow(Icons.Filled.Delete, "저장된 데이터 초기화", tint = MaterialTheme.colorScheme.error) { confirmReset = true }
        }
        Text("FCare · Triple팀 · 2026 창의설계 2", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
    }

    if (showGuide) GuideDialog { showGuide = false }
    if (confirmAi) {
        AlertDialog(
            onDismissRequest = { confirmAi = false },
            title = { Text("AI 문장 다듬기를 켤까요?") },
            text = {
                Text("조언 문장을 자연스럽게 다듬기 위해 오늘의 점수·등급과 조언 요약(예: \"최근 7일 운동량이 평소의 1.45배\")을 " +
                    "Google Gemini로 보내요.\n\n이름·나이·성별과 원래 측정 기록(수면 시각, 심박 기록 등)은 보내지 않아요. " +
                    "무료 사용 등급에서는 보낸 내용이 Google 서비스 개선에 쓰일 수 있어요.\n\n" +
                    "문장을 만들지 못하면 앱에 들어 있는 기본 문장을 보여 줘요.")
            },
            confirmButton = { TextButton({ confirmAi = false; vm.setAiEnabled(true) }) { Text("동의하고 켜기") } },
            dismissButton = { TextButton({ confirmAi = false }) { Text("취소") } },
        )
    }
    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("기록을 내보낼까요?") },
            text = {
                Text("날짜별 회복 점수·수면·심박·운동 부하를 CSV 파일로 저장해요. 닉네임·나이·성별은 넣지 않아요.\n\n" +
                    "파일에는 건강 정보가 들어 있으니 공유할 때 주의해 주세요.")
            },
            confirmButton = {
                TextButton({ confirmExport = false; exportLauncher.launch("FCare_기록_${LocalDate.now()}.csv") }) { Text("저장 위치 고르기") }
            },
            dismissButton = { TextButton({ confirmExport = false }) { Text("취소") } },
        )
    }
    exportResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { exportResult = null },
            confirmButton = { TextButton({ exportResult = null }) { Text("확인") } },
            text = { Text(msg) },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("데이터를 초기화할까요?") },
            text = { Text("프로필과 회복 점수 기록이 모두 지워지고 처음 화면으로 돌아가요. Health Connect에 있는 워치 기록은 지워지지 않아요.") },
            confirmButton = { TextButton({ confirmReset = false; vm.resetData() }) { Text("초기화", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmReset = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), color = tint)
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, caption: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
