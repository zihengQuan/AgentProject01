package com.zq.topnews.ui.video

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zq.topnews.data.FeedVideo
import com.zq.topnews.data.VideoRepository
import com.zq.topnews.ui.components.NetworkErrorState
import com.zq.topnews.ui.components.ToneBox
import com.zq.topnews.ui.components.toUserMessage
import java.util.Locale

/** Two-column video channel backed by Bilibili's public popular feed. */
@Composable
fun VideoScreen(modifier: Modifier = Modifier) {
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var state by remember {
        mutableStateOf<VideoUiState>(
            VideoRepository.cached()
                ?.let { VideoUiState.Ready(it) }
                ?: VideoUiState.Loading,
        )
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey == 0 && VideoRepository.cached() != null) return@LaunchedEffect
        state = VideoUiState.Loading
        state = try {
            VideoUiState.Ready(VideoRepository.popular(forceRefresh = refreshKey > 0))
        } catch (error: Exception) {
            VideoUiState.Failed(error.toUserMessage())
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        VideoHeader(
            refreshing = state is VideoUiState.Loading,
            onRefresh = { refreshKey++ },
        )
        when (val current = state) {
            VideoUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is VideoUiState.Failed -> NetworkErrorState(
                title = "视频加载失败",
                message = current.message,
                onRetry = { refreshKey++ },
            )

            is VideoUiState.Ready -> {
                if (current.videos.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "暂无视频",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    VideoGrid(videos = current.videos)
                }
            }
        }
    }
}

private sealed interface VideoUiState {
    data object Loading : VideoUiState
    data class Ready(val videos: List<FeedVideo>) : VideoUiState
    data class Failed(val message: String) : VideoUiState
}

@Composable
private fun VideoHeader(refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "视频",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "哔哩哔哩 · 热门",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "刷新",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VideoGrid(videos: List<FeedVideo>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(videos, key = { it.bvid }) { video ->
            VideoCard(video)
        }
    }
}

@Composable
private fun VideoCard(video: FeedVideo) {
    val context = LocalContext.current
    val request = remember(video.coverUrl) {
        ImageRequest.Builder(context)
            .data(video.coverUrl)
            .addHeader("Referer", "https://www.bilibili.com/")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36")
            .crossfade(true)
            .build()
    }

    Column(
        modifier = Modifier.clickable {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, video.pageUrl.toUri()))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "当前设备没有可用的浏览器", Toast.LENGTH_SHORT).show()
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            ToneBox(
                tone = Math.floorMod(video.bvid.hashCode(), 8),
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 10,
            )
            AsyncImage(
                model = request,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = formatDuration(video.durationSeconds),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${video.author} · ${formatViews(video.views)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatViews(views: Long): String = when {
    views >= 100_000_000 ->
        String.format(Locale.CHINA, "%.1f 亿次播放", views / 100_000_000.0)
    views >= 10_000 ->
        String.format(Locale.CHINA, "%.1f 万次播放", views / 10_000.0)
    else -> "$views 次播放"
}

internal fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", minutes, secs)
    }
}
