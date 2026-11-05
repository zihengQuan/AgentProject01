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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.zq.topnews.data.NewsRepository
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
 * TopNews root: a four-tab shell with a full-screen article view on top.
 * Navigation is local state; the open article is resolved from the loaded feed.
 */
@Composable
fun TopNewsApp() {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    var openArticleId by rememberSaveable { mutableStateOf<String?>(null) }
    val article = openArticleId?.let { NewsRepository.find(it) }

    // The feed cache is in memory only; after process death the id is stale.
    LaunchedEffect(openArticleId, article) {
        if (openArticleId != null && article == null) openArticleId = null
    }

    BackHandler(enabled = article != null) {
        openArticleId = null
    }

    if (article != null) {
        ArticleDetailScreen(item = article, onBack = { openArticleId = null })
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
            MainTab.PROFILE -> ProfileScreen(modifier = contentModifier)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopNewsAppPreview() {
    TopNewsTheme {
        TopNewsApp()
    }
}
