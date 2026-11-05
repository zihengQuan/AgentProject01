package com.zq.topnews.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.zq.topnews.data.AuthRepository
import com.zq.topnews.data.InMemoryAuthStore
import com.zq.topnews.data.MockAuthRepository
import com.zq.topnews.data.NewsRepository
import com.zq.topnews.ui.auth.AuthScreen
import com.zq.topnews.ui.game.RacingGameScreen
import com.zq.topnews.ui.home.ArticleDetailScreen
import com.zq.topnews.ui.home.HomeFeedScreen
import com.zq.topnews.ui.message.MessageScreen
import com.zq.topnews.ui.profile.ProfileScreen
import com.zq.topnews.ui.theme.TopNewsTheme
import com.zq.topnews.ui.video.VideoScreen

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Filled.Home),
    VIDEO("视频", Icons.Filled.PlayArrow),
    MESSAGE("消息", Icons.Filled.Notifications),
    PROFILE("我的", Icons.Filled.Person),
}

/**
 * TopNews root: a four-tab shell with full-screen article and auth views on top.
 * Navigation is local state; the open article is resolved from the loaded feed.
 */
@Composable
fun TopNewsApp(authRepository: AuthRepository) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    var openArticleId by rememberSaveable { mutableStateOf<String?>(null) }
    var authVisible by rememberSaveable { mutableStateOf(false) }
    var gameVisible by rememberSaveable { mutableStateOf(false) }
    val article = openArticleId?.let { NewsRepository.find(it) }
    val currentUser by authRepository.currentUser.collectAsState()

    // The feed cache is in memory only; after process death the id is stale.
    LaunchedEffect(openArticleId, article) {
        if (openArticleId != null && article == null) openArticleId = null
    }

    BackHandler(enabled = authVisible) {
        authVisible = false
    }

    BackHandler(enabled = article != null) {
        openArticleId = null
    }

    if (authVisible) {
        AuthScreen(
            authRepository = authRepository,
            onBack = { authVisible = false },
            onAuthenticated = { authVisible = false },
        )
        return
    }

    if (article != null) {
        ArticleDetailScreen(item = article, onBack = { openArticleId = null })
        return
    }

    if (gameVisible) {
        RacingGameScreen(onExit = { gameVisible = false })
        return
    }

    val tab = MainTab.valueOf(currentTab)

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { currentTab = entry.name },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (tab) {
            MainTab.HOME -> HomeFeedScreen(
                onOpenArticle = { openArticleId = it.id },
                modifier = contentModifier,
            )
            MainTab.VIDEO -> VideoScreen(modifier = contentModifier)
            MainTab.MESSAGE -> MessageScreen(modifier = contentModifier)
            MainTab.PROFILE -> ProfileScreen(
                user = currentUser,
                onLoginClick = { authVisible = true },
                onLogout = { authRepository.logout() },
                onPlayGame = { gameVisible = true },
                modifier = contentModifier,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopNewsAppPreview() {
    TopNewsTheme {
        TopNewsApp(
            authRepository = remember { MockAuthRepository(InMemoryAuthStore()) },
        )
    }
}
