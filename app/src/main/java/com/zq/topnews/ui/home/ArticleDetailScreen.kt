package com.zq.topnews.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zq.topnews.data.NewsItem
import com.zq.topnews.data.NewsRepository
import com.zq.topnews.ui.components.ToneBox
import com.zq.topnews.ui.components.toUserMessage

/** Full article view opened from the news feed; the body is loaded on demand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    item: NewsItem,
    onBack: () -> Unit,
) {
    var retryKey by remember(item.id) { mutableIntStateOf(0) }
    var body by remember(item.id) {
        mutableStateOf<ArticleBody>(
            item.paragraphs.takeIf { it.isNotEmpty() }
                ?.let { ArticleBody.Ready(it) }
                ?: ArticleBody.Loading,
        )
    }

    LaunchedEffect(item.id, retryKey) {
        if (retryKey == 0 && body is ArticleBody.Ready) return@LaunchedEffect
        body = ArticleBody.Loading
        body = try {
            ArticleBody.Ready(NewsRepository.article(item.id, forceRefresh = retryKey > 0))
        } catch (error: Exception) {
            ArticleBody.Failed(error.toUserMessage())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Share, contentDescription = "分享")
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.FavoriteBorder, contentDescription = "收藏")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = { CommentBar() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${item.source}  ${item.time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "关注",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            ToneBox(
                tone = item.tone,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                label = item.category,
                cornerRadius = 10,
            )
            Spacer(Modifier.height(18.dp))
            when (val current = body) {
                ArticleBody.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is ArticleBody.Failed -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "正文加载失败",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { retryKey++ }) {
                        Text("重试")
                    }
                }

                is ArticleBody.Ready -> Paragraphs(
                    paragraphs = current.paragraphs.ifEmpty {
                        listOf(item.summary.ifBlank { "该文章暂无正文内容，请稍后重试。" })
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "内容来源：${item.source}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

private sealed interface ArticleBody {
    data object Loading : ArticleBody
    data class Ready(val paragraphs: List<String>) : ArticleBody
    data class Failed(val message: String) : ArticleBody
}

@Composable
private fun Paragraphs(paragraphs: List<String>) {
    paragraphs.forEach { paragraph ->
        Text(
            text = paragraph,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 27.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun CommentBar() {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "  写评论…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = {}) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = "点赞",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "转发",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
