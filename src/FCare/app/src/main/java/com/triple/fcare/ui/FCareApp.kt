package com.triple.fcare.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import com.triple.fcare.hc.HcPermissions
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.triple.fcare.data.ProfileEntity
import com.triple.fcare.ui.theme.Background
import com.triple.fcare.ui.theme.Dim
import com.triple.fcare.ui.theme.FcText
import com.triple.fcare.ui.theme.TabBg
import com.triple.fcare.ui.theme.TabDivider
import com.triple.fcare.ui.theme.TabFg
import com.triple.fcare.ui.theme.TabSelectedBg
import com.triple.fcare.ui.theme.TabSelectedFg
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * 앱 화면 흐름 (분석서 화면 구성):
 * 시작 → 최초 설정 → Health Connect 권한 요청 → 홈 / 분석 / 기록 / 프로필(하단 탭)
 */
@Composable
fun FCareApp(vm: MainViewModel = viewModel(), openFeedback: Boolean = false) {
    val state by vm.profileState.collectAsState()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1200); splashDone = true }

    when (val s = state) {
        // SPL-01·02: 시작 화면을 보여 준 뒤 최초 실행이면 최초 설정, 아니면 홈
        ProfileState.Loading -> SplashScreen()
        else -> if (!splashDone) SplashScreen() else when (s) {
            ProfileState.None -> SetupScreen(vm)
            is ProfileState.NeedPermission -> PermissionScreen(vm) { vm.completeOnboarding() }
            is ProfileState.Ready -> MainTabs(vm, s.profile, openFeedback)
            ProfileState.Loading -> SplashScreen()
        }
    }
}

/** 탭: 선택되면 채운(Rounded) 아이콘, 아니면 윤곽(Outlined) 아이콘 */
private enum class Tab(val label: String, val icon: ImageVector, val iconSelected: ImageVector) {
    HOME("홈", Icons.Outlined.Home, Icons.Rounded.Home),
    FEEDBACK("분석", Icons.Outlined.Search, Icons.Rounded.Search),
    HISTORY("기록", Icons.AutoMirrored.Outlined.ShowChart, Icons.AutoMirrored.Rounded.ShowChart),
    PROFILE("프로필", Icons.Outlined.Person, Icons.Rounded.Person),
}

/** HOM-05 하단 탭: 홈 / 분석 / 기록 / 프로필. 상단바는 없고 각 화면이 큰 제목을 그린다(UI 개편 11-20). */
@Composable
private fun MainTabs(vm: MainViewModel, profile: ProfileEntity, openFeedback: Boolean) {
    var tab by rememberSaveable { mutableStateOf(if (openFeedback) Tab.FEEDBACK else Tab.HOME) }
    var historyDate by remember { mutableStateOf<LocalDate?>(null) }   // 홈 최근 3일 줄 → 기록 탭 그 날짜
    val snackbar = remember { SnackbarHostState() }

    // 개선사항 4: 점수 알림 권한 (Android 13+). 처음 한 번만 물어보고, 이후에는 프로필 설정에서 켠다.
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (vm.shouldAskNotification()) {
            vm.markNotificationAsked()
            notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var showCheck by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (!showCheck) BottomTabs(tab) { tab = it; if (it != Tab.HISTORY) historyDate = null } },
    ) { padding ->
        val m = Modifier.padding(padding)   // Scaffold가 상태 바·하단 탭 높이만큼 여백을 이미 준다
        if (showCheck) {
            BackHandler { showCheck = false }
            Column(m.fillMaxSize()) {
                TextButton({ showCheck = false }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    Text(" 연동 확인 닫기")
                }
                HcCheckScreen(Modifier.fillMaxSize())
            }
        } else when (tab) {
            Tab.HOME -> {
                val steps = vm.steps.collectAsState().value
                // 걸음 타일 "권한 필요 ›" (S6a): 걸음 수 권한만 요청하고 돌아오면 다시 불러온다 (JOURNEY F4)
                val stepsLauncher = rememberLauncherForActivityResult(
                    PermissionController.createRequestPermissionResultContract()) { vm.refresh() }
                HomeScreen(vm, profile, m, snackbar,
                    onOpenFeedback = { tab = Tab.FEEDBACK },
                    onOpenDay = { historyDate = it; tab = Tab.HISTORY },
                    steps = steps,
                    onRequestSteps = { stepsLauncher.launch(setOf(HcPermissions.STEPS)) })
            }
            Tab.FEEDBACK -> FeedbackScreen(vm, m)
            Tab.HISTORY -> HistoryScreen(vm, m, initialDate = historyDate)
            Tab.PROFILE -> ProfileScreen(vm, profile, onOpenCheck = { showCheck = true }, modifier = m)
        }
    }
}

/**
 * 하단 탭 (DESIGN.md Components, 사용자 결정 11-20):
 * 같은 폭 칸 4개 + 칸 사이 세로 1dp 선, 높이 68dp. 선택 칸은 칸 전체를 옅은 회색으로 채우고
 * 채운 아이콘 + 굵은 진한 글자. 탭 전환은 즉시(움직임 없음).
 */
@Composable
private fun BottomTabs(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(TabBg).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Dim.tabbar)) {
            Tab.entries.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.width(Dim.hairline).fillMaxHeight().background(TabDivider))
                val on = t == current
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (on) TabSelectedBg else TabBg)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(t) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    val fg = if (on) TabSelectedFg else TabFg
                    Icon(if (on) t.iconSelected else t.icon, contentDescription = null, tint = fg,
                        modifier = Modifier.size(Dim.iconSm))
                    Spacer(Modifier.height(Dim.stroke))
                    Text(t.label, style = FcText.xs.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium), color = fg)
                }
            }
        }
    }
}
