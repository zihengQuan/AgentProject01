package com.zq.topnews.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.zq.topnews.game.RacingConfig
import com.zq.topnews.game.RacingGameEngine
import com.zq.topnews.game.RacingPhase
import com.zq.topnews.game.RacingSnapshot
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private const val PREFERENCES_NAME = "racing_game"
private const val KEY_BEST_SCORE = "best_score"

private object TrackColors {
    val Night = Color(0xFF070B14)
    val GrassA = Color(0xFF0A1A12)
    val GrassB = Color(0xFF0F2418)
    val GrassStripe = Color(0xFF143021)
    val RoadTop = Color(0xFF2B303C)
    val RoadBottom = Color(0xFF1C2029)
    val Line = Color(0xFFF7F2E3)
    val CurbRed = Color(0xFFD63B36)
    val Player = Color(0xFFE53935)
    val Gold = Color(0xFFFFC94D)
    val TextMuted = Color(0xFFB8C0D4)
    val Panel = Color(0xCC0D1424)
    val Card = Color(0xFF151C2C)
}

private val EnemyColors = listOf(
    Color(0xFF2F80ED),
    Color(0xFFF2C94C),
    Color(0xFF9B51E0),
    Color(0xFF27AE60),
    Color(0xFFFF8A3D),
)

/**
 * A self-contained, offline racing mini-game: steer the red car, dodge traffic
 * and build the highest score. Rendering is pure Compose Canvas; all rules live
 * in [RacingGameEngine] so they can be tested without Android.
 */
@Composable
fun RacingGameScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var bestScore by rememberSaveable {
        mutableIntStateOf(preferences.getInt(KEY_BEST_SCORE, 0))
    }
    var isNewRecord by remember { mutableStateOf(false) }
    val engine = remember { RacingGameEngine() }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }

    // The rest of the app is light; give the game white system-bar icons and
    // hand the previous appearance back when the screen is left.
    UseDarkSystemBars()

    LaunchedEffect(engine) {
        var lastFrame = 0L
        while (isActive) {
            withFrameNanos { now ->
                val running = engine.phase == RacingPhase.RUNNING
                if (lastFrame != 0L && running) {
                    val delta = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    engine.advance(delta)
                    val next = engine.snapshot()
                    snapshot = next
                    if (next.phase == RacingPhase.GAME_OVER && next.score > bestScore) {
                        bestScore = next.score
                        isNewRecord = true
                        preferences.edit { putInt(KEY_BEST_SCORE, bestScore) }
                    }
                }
                lastFrame = now
            }
        }
    }

    val startRun = {
        isNewRecord = false
        engine.start()
        snapshot = engine.snapshot()
    }
    val pauseRun = {
        engine.pause()
        snapshot = engine.snapshot()
    }
    val resumeRun = {
        engine.resume()
        snapshot = engine.snapshot()
    }

    BackHandler {
        when (engine.phase) {
            RacingPhase.RUNNING -> pauseRun()
            RacingPhase.READY, RacingPhase.PAUSED, RacingPhase.GAME_OVER -> onExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackColors.Night),
    ) {
        GameCanvas(
            snapshot = snapshot,
            config = engine.config,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(engine) {
                    detectTapGestures { offset ->
                        engine.steerTo(offset.x / size.width.toFloat())
                    }
                }
                .pointerInput(engine) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            engine.steerTo(offset.x / size.width.toFloat())
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            engine.steerTo(change.position.x / size.width.toFloat())
                        },
                    )
                },
        )

        TopHud(
            modifier = Modifier.align(Alignment.TopCenter),
            score = snapshot.score,
            bestScore = bestScore,
            showPause = snapshot.phase == RacingPhase.RUNNING,
            onClose = onExit,
            onPause = pauseRun,
        )

        if (snapshot.phase == RacingPhase.RUNNING || snapshot.phase == RacingPhase.PAUSED) {
            SpeedBadge(
                speed = snapshot.speed,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        when (snapshot.phase) {
            RacingPhase.READY -> StartOverlay(
                bestScore = bestScore,
                onStart = startRun,
                onExit = onExit,
            )

            RacingPhase.PAUSED -> PauseOverlay(
                score = snapshot.score,
                onResume = resumeRun,
                onRestart = startRun,
                onExit = onExit,
            )

            RacingPhase.GAME_OVER -> GameOverOverlay(
                score = snapshot.score,
                bestScore = bestScore,
                isNewRecord = isNewRecord,
                onRestart = startRun,
                onExit = onExit,
            )

            RacingPhase.RUNNING -> Unit
        }
    }
}

@Composable
private fun UseDarkSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatusBars = controller?.isAppearanceLightStatusBars
        val lightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (lightStatusBars != null) controller.isAppearanceLightStatusBars = lightStatusBars
            if (lightNavigationBars != null) {
                controller.isAppearanceLightNavigationBars = lightNavigationBars
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun TopHud(
    modifier: Modifier,
    score: Int,
    bestScore: Int,
    showPause: Boolean,
    onClose: () -> Unit,
    onPause: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.Filled.Close,
            description = "退出游戏",
            onClick = onClose,
        )
        Spacer(Modifier.weight(1f))
        ScorePanel(score = score, bestScore = bestScore)
        Spacer(Modifier.weight(1f))
        if (showPause) {
            PauseButton(onClick = onPause)
        } else {
            Spacer(Modifier.size(44.dp))
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PauseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(15.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun ScorePanel(score: Int, bestScore: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TrackColors.Panel)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "分数",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.55f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = score.toString(),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(Color.White.copy(alpha = 0.12f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "最佳",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.55f),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = bestScore.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TrackColors.Gold,
        )
    }
}

@Composable
private fun SpeedBadge(speed: Float, modifier: Modifier) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 18.dp, bottom = 18.dp)
            .clip(RoundedCornerShape(50))
            .background(TrackColors.Panel)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(TrackColors.Player),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${(speed * 160f).roundToInt()} km/h",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun StartOverlay(
    bestScore: Int,
    onStart: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF20A0E18), Color(0xF5131B2E)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(TrackColors.Player, Color(0xFFFF8A65)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "极速飞车",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFF6B6B), TrackColors.Gold),
                    ),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "3 车道 · 无尽挑战",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InstructionRow("1", "左右拖动画面，控制赛车变道")
                InstructionRow("2", "躲开前方车辆，撞车即结束")
                InstructionRow("3", "车速会越来越快，挑战最高分")
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TrackColors.Player,
                    contentColor = Color.White,
                ),
            ) {
                Text(text = "开始游戏", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            if (bestScore > 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "历史最高分 $bestScore",
                    fontSize = 13.sp,
                    color = TrackColors.Gold,
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onExit) {
                Text(text = "返回 TopNews", color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun InstructionRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(TrackColors.Player.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun PauseOverlay(
    score: Int,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TrackColors.Card)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "已暂停",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "当前分数 $score",
                fontSize = 13.sp,
                color = TrackColors.TextMuted,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TrackColors.Player,
                    contentColor = Color.White,
                ),
            ) {
                Text(text = "继续游戏", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text(text = "重新开始")
            }
            TextButton(onClick = onExit) {
                Text(text = "退出游戏", color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun GameOverOverlay(
    score: Int,
    bestScore: Int,
    isNewRecord: Boolean,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TrackColors.Card)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "撞车了！",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            if (isNewRecord) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(TrackColors.Gold.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "新纪录",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TrackColors.Gold,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "本次得分",
                        fontSize = 12.sp,
                        color = TrackColors.TextMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = score.toString(),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = TrackColors.Gold,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(46.dp)
                        .background(Color.White.copy(alpha = 0.10f)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "历史最高",
                        fontSize = 12.sp,
                        color = TrackColors.TextMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = bestScore.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TrackColors.Player,
                    contentColor = Color.White,
                ),
            ) {
                Text(text = "再来一局", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text(text = "返回 TopNews")
            }
        }
    }
}

@Composable
private fun GameCanvas(
    snapshot: RacingSnapshot,
    config: RacingConfig,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        drawTrack(snapshot, config)

        val carWidth = config.carWidth * size.width
        val carHeight = config.carHeight * size.height
        snapshot.obstacles
            .sortedBy { it.top }
            .forEach { obstacle ->
                drawCar(
                    centerX = obstacle.x * size.width,
                    top = obstacle.top * size.height,
                    carWidth = carWidth,
                    carHeight = carHeight,
                    body = EnemyColors[obstacle.colorIndex.mod(EnemyColors.size)],
                    isPlayer = false,
                )
            }

        drawPlayer(snapshot, config)
    }
}

private fun DrawScope.drawTrack(snapshot: RacingSnapshot, config: RacingConfig) {
    val width = size.width
    val height = size.height
    val roadLeft = config.roadInset * width
    val roadWidth = width - roadLeft * 2f
    val travel = snapshot.distance

    drawRect(
        brush = Brush.verticalGradient(
            listOf(TrackColors.GrassA, TrackColors.GrassB),
        ),
    )

    // Mowing stripes on the grass give the scrolling road a speed reference.
    val stripeHeight = height * 0.09f
    val stripeOffset = (travel * height * 0.7f) % (stripeHeight * 2f)
    var stripeY = -stripeHeight * 2f + stripeOffset
    while (stripeY < height) {
        drawRect(TrackColors.GrassStripe, Offset(0f, stripeY), Size(roadLeft, stripeHeight))
        drawRect(
            TrackColors.GrassStripe,
            Offset(roadLeft + roadWidth, stripeY),
            Size(roadLeft, stripeHeight),
        )
        stripeY += stripeHeight * 2f
    }

    // Roadside lamps scroll with the track and add depth to the night scene.
    val lampSpacing = height * 0.24f
    val lampOffset = (travel * height * 0.9f) % lampSpacing
    var lampY = -lampSpacing + lampOffset
    while (lampY < height) {
        val lampCenterLeft = Offset(roadLeft * 0.5f, lampY)
        val lampCenterRight = Offset(roadLeft + roadWidth + roadLeft * 0.5f, lampY)
        listOf(lampCenterLeft, lampCenterRight).forEach { center ->
            drawCircle(Color(0xFFFFD27A).copy(alpha = 0.06f), width * 0.035f, center)
            drawCircle(Color(0xFFFFD27A).copy(alpha = 0.35f), width * 0.007f, center)
        }
        lampY += lampSpacing
    }

    drawRect(
        brush = Brush.verticalGradient(listOf(TrackColors.RoadTop, TrackColors.RoadBottom)),
        topLeft = Offset(roadLeft, 0f),
        size = Size(roadWidth, height),
    )

    // A faint centre seam keeps the wide asphalt from looking flat.
    drawRect(
        Color.White.copy(alpha = 0.025f),
        Offset(roadLeft + roadWidth / 2f - 1f, 0f),
        Size(2f, height),
    )

    // Scrolling red/white rumble strips along both edges.
    val blockHeight = height * 0.032f
    val curbWidth = width * 0.013f
    val curbTravel = travel * height * 1.15f
    var curbIndex = 0
    var curbY = -(curbTravel % (blockHeight * 2f))
    while (curbY < height) {
        val color = if (curbIndex % 2 == 0) TrackColors.CurbRed else TrackColors.Line
        drawRect(color, Offset(roadLeft - curbWidth, curbY), Size(curbWidth, blockHeight))
        drawRect(
            color,
            Offset(roadLeft + roadWidth, curbY),
            Size(curbWidth, blockHeight),
        )
        curbY += blockHeight
        curbIndex++
    }

    // Dashed lane dividers.
    val dashHeight = height * 0.048f
    val dashPeriod = dashHeight * 2f
    val dashOffset = (travel * height * 1.15f) % dashPeriod
    for (lane in 1 until config.laneCount) {
        val laneX = roadLeft + roadWidth * lane / config.laneCount
        var dashY = -dashPeriod + dashOffset
        while (dashY < height) {
            drawRoundRect(
                color = TrackColors.Line.copy(alpha = 0.62f),
                topLeft = Offset(laneX - width * 0.003f, dashY),
                size = Size(width * 0.006f, dashHeight),
                cornerRadius = CornerRadius(width * 0.003f),
            )
            dashY += dashPeriod
        }
    }

    // Solid edge lines just inside the rumble strips.
    drawRect(
        TrackColors.Line.copy(alpha = 0.30f),
        Offset(roadLeft + curbWidth, 0f),
        Size(width * 0.0035f, height),
    )
    drawRect(
        TrackColors.Line.copy(alpha = 0.30f),
        Offset(roadLeft + roadWidth - curbWidth - width * 0.0035f, 0f),
        Size(width * 0.0035f, height),
    )
}

private fun DrawScope.drawPlayer(snapshot: RacingSnapshot, config: RacingConfig) {
    val width = size.width
    val height = size.height
    val carWidth = config.carWidth * width
    val carHeight = config.carHeight * height
    val centerX = snapshot.playerX * width
    val top = config.playerTop * height

    // Warm glow gives the player car presence at night.
    drawOval(
        color = TrackColors.Player.copy(alpha = 0.16f),
        topLeft = Offset(centerX - carWidth * 0.75f, top - carHeight * 0.05f),
        size = Size(carWidth * 1.5f, carHeight * 1.2f),
    )

    drawCar(
        centerX = centerX,
        top = top,
        carWidth = carWidth,
        carHeight = carHeight,
        body = TrackColors.Player,
        isPlayer = true,
    )
}

private fun DrawScope.drawCar(
    centerX: Float,
    top: Float,
    carWidth: Float,
    carHeight: Float,
    body: Color,
    isPlayer: Boolean,
) {
    val left = centerX - carWidth / 2f
    val bodyRadius = CornerRadius(carWidth * 0.16f)

    // Wheels peek out from under the body on all four corners.
    val wheelWidth = carWidth * 0.16f
    val wheelHeight = carHeight * 0.19f
    val wheelColor = Color(0xFF0B0B10)
    listOf(
        Offset(left - wheelWidth * 0.30f, top + carHeight * 0.13f),
        Offset(left + carWidth - wheelWidth * 0.70f, top + carHeight * 0.13f),
        Offset(left - wheelWidth * 0.30f, top + carHeight * 0.66f),
        Offset(left + carWidth - wheelWidth * 0.70f, top + carHeight * 0.66f),
    ).forEach { wheelTopLeft ->
        drawRoundRect(
            color = wheelColor,
            topLeft = wheelTopLeft,
            size = Size(wheelWidth, wheelHeight),
            cornerRadius = CornerRadius(wheelWidth * 0.35f),
        )
    }

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(left + carWidth * 0.04f, top + carHeight * 0.06f),
        size = Size(carWidth, carHeight),
        cornerRadius = bodyRadius,
    )

    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                lerp(body, Color.White, 0.32f),
                body,
                lerp(body, Color.Black, 0.32f),
            ),
            startY = top,
            endY = top + carHeight,
        ),
        topLeft = Offset(left, top),
        size = Size(carWidth, carHeight),
        cornerRadius = bodyRadius,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.18f),
        topLeft = Offset(left, top),
        size = Size(carWidth, carHeight),
        cornerRadius = bodyRadius,
        style = Stroke(width = 1.2f),
    )

    // Racing stripe.
    drawRoundRect(
        color = Color.White.copy(alpha = if (isPlayer) 0.55f else 0.28f),
        topLeft = Offset(centerX - carWidth * 0.035f, top + carHeight * 0.10f),
        size = Size(carWidth * 0.07f, carHeight * 0.62f),
        cornerRadius = CornerRadius(carWidth * 0.03f),
    )

    // Windshield, glare and rear window.
    drawRoundRect(
        color = Color(0xFF0F1828),
        topLeft = Offset(left + carWidth * 0.17f, top + carHeight * 0.25f),
        size = Size(carWidth * 0.66f, carHeight * 0.23f),
        cornerRadius = CornerRadius(carWidth * 0.09f),
    )
    drawRoundRect(
        color = Color(0xFF33445C),
        topLeft = Offset(left + carWidth * 0.21f, top + carHeight * 0.27f),
        size = Size(carWidth * 0.58f, carHeight * 0.09f),
        cornerRadius = CornerRadius(carWidth * 0.05f),
    )
    drawRoundRect(
        color = Color(0xFF0D1522),
        topLeft = Offset(left + carWidth * 0.20f, top + carHeight * 0.57f),
        size = Size(carWidth * 0.60f, carHeight * 0.16f),
        cornerRadius = CornerRadius(carWidth * 0.07f),
    )

    // Rear spoiler.
    drawRoundRect(
        color = lerp(body, Color.Black, 0.45f),
        topLeft = Offset(left - carWidth * 0.03f, top + carHeight * 0.86f),
        size = Size(carWidth * 1.06f, carHeight * 0.09f),
        cornerRadius = CornerRadius(carWidth * 0.04f),
    )

    // Headlights and tail lights.
    val headlight = Color(0xFFFFF3B0)
    drawRoundRect(
        color = headlight,
        topLeft = Offset(left + carWidth * 0.12f, top + carHeight * 0.045f),
        size = Size(carWidth * 0.17f, carHeight * 0.055f),
        cornerRadius = CornerRadius(carWidth * 0.03f),
    )
    drawRoundRect(
        color = headlight,
        topLeft = Offset(left + carWidth * 0.71f, top + carHeight * 0.045f),
        size = Size(carWidth * 0.17f, carHeight * 0.055f),
        cornerRadius = CornerRadius(carWidth * 0.03f),
    )
    val tailLight = Color(0xFFFF5252)
    drawRoundRect(
        color = tailLight,
        topLeft = Offset(left + carWidth * 0.12f, top + carHeight * 0.925f),
        size = Size(carWidth * 0.17f, carHeight * 0.045f),
        cornerRadius = CornerRadius(carWidth * 0.02f),
    )
    drawRoundRect(
        color = tailLight,
        topLeft = Offset(left + carWidth * 0.71f, top + carHeight * 0.925f),
        size = Size(carWidth * 0.17f, carHeight * 0.045f),
        cornerRadius = CornerRadius(carWidth * 0.02f),
    )
}
