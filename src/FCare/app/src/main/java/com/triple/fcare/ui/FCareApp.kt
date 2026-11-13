package com.triple.fcare.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.triple.fcare.data.ProfileEntity
import kotlinx.coroutines.delay

/**
 * 앱 화면 흐름 (분석서 화면 구성):
 * 시작 → 최초 설정 → Health Connect 권한 요청 → 홈 / 기록 / 프로필(하단 탭)
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

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    HOME("홈", "FCare", Icons.Filled.Home),
    FEEDBACK("피드백", "오늘의 피드백", Icons.Filled.Favorite),   // 개선사항 6: 피드백 전용 탭
    HISTORY("기록", "기록", Icons.Filled.DateRange),
    PROFILE("프로필", "프로필 · 설정", Icons.Filled.Person),
}

/** HOM-05 하단 탭: 홈 / 피드백 / 기록 / 프로필 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(vm: MainViewModel, profile: ProfileEntity, openFeedback: Boolean) {
    var tab by rememberSaveable { mutableStateOf(if (openFeedback) Tab.FEEDBACK else Tab.HOME) }

    // 개선사항 4: 점수 알림 권한 (Android 13+). 처음 한 번만 물어보고, 이후에는 프로필 설정에서 켠다.
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (vm.shouldAskNotification()) {
            vm.markNotificationAsked()
            notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var showCheck by rememberSaveable { mutableStateOf(false) }
    val refreshing by vm.refreshing.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(if (showCheck) "연동 확인" else tab.title, fontWeight = FontWeight.Bold,
                        color = if (tab == Tab.HOME && !showCheck) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                },
                navigationIcon = {
                    if (showCheck) IconButton({ showCheck = false }) { Icon(Icons.Filled.ArrowBack, contentDescription = "돌아가기") }
                },
                actions = {
                    // [다시 불러오기] (HOM-07)
                    if (tab == Tab.HOME && !showCheck) {
                        if (refreshing) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        else IconButton({ vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "다시 불러오기") }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && !showCheck,
                        onClick = { tab = t; showCheck = false },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        if (showCheck) {
            HcCheckScreen(m.fillMaxSize())
        } else when (tab) {
            Tab.HOME -> HomeScreen(vm, profile, m, onOpenFeedback = { tab = Tab.FEEDBACK })
            Tab.FEEDBACK -> FeedbackScreen(vm, m)
            Tab.HISTORY -> HistoryScreen(vm, m)
            Tab.PROFILE -> ProfileScreen(vm, profile, onOpenCheck = { showCheck = true }, modifier = m)
        }
    }
}
