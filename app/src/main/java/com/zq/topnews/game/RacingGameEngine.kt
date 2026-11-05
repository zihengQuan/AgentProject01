package com.zq.topnews.game

import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/** Which overlay the racing screen should show. */
enum class RacingPhase {
    READY,
    RUNNING,
    PAUSED,
    GAME_OVER,
}

/** A rival car on the track. Coordinates use 0..1 of the screen width/height. */
data class RacingObstacle(
    val id: Int,
    val lane: Int,
    /** Horizontal centre of the car, in screen-width units. */
    val x: Float,
    /** Top edge of the car, in screen-height units. */
    val top: Float,
    /** Index into the renderer's palette. */
    val colorIndex: Int,
)

/** One immutable frame of the game, safe to read from Compose. */
data class RacingSnapshot(
    val phase: RacingPhase,
    val playerX: Float,
    val obstacles: List<RacingObstacle>,
    val score: Int,
    val speed: Float,
    val distance: Float,
    val elapsed: Float,
)

/**
 * Tunable game rules. All distances are fractions of the screen so the same
 * values work on every device. [laneCount], [baseSpeed] and [speedRampSeconds]
 * are the knobs that shape the difficulty curve.
 */
data class RacingConfig(
    val laneCount: Int = 3,
    val roadInset: Float = 0.045f,
    val carWidth: Float = 0.155f,
    val carHeight: Float = 0.16f,
    val playerTop: Float = 0.72f,
    val baseSpeed: Float = 0.58f,
    val maxSpeed: Float = 1.30f,
    val speedRampSeconds: Float = 80f,
    val spawnIntervalStart: Float = 1.05f,
    val spawnIntervalMin: Float = 0.48f,
    val maxObstacles: Int = 9,
    val steerSpeed: Float = 2.1f,
    val scorePerUnit: Float = 120f,
    /** Shrinks both cars slightly so grazes are forgiving. 0.16 = 16%. */
    val collisionForgiveness: Float = 0.16f,
    /** Lanes are kept clear this close to the top edge when choosing a spawn. */
    val topClearZone: Float = 0.36f,
) {
    init {
        require(laneCount >= 1) { "laneCount must be positive" }
        require(roadInset in 0f..0.4f) { "roadInset must stay inside the screen" }
        require(carWidth > 0f && carHeight > 0f) { "cars need a positive size" }
        require(carWidth + roadInset * 2f <= 1f) { "cars must fit on the road" }
        require(playerTop in 0f..1f) { "playerTop must be on screen" }
        require(baseSpeed > 0f && maxSpeed >= baseSpeed) { "speed range is invalid" }
        require(steerSpeed > 0f && scorePerUnit > 0f) { "steering and scoring must move" }
    }
}

/**
 * Deterministic top-down racing simulation, independent of Android and Compose
 * so the rules can be unit tested. The screen feeds elapsed time in and reads
 * [snapshot] back; rendering never mutates the engine.
 */
class RacingGameEngine(
    val config: RacingConfig = RacingConfig(),
    private val random: Random = Random.Default,
) {
    private class ObstacleState(
        val id: Int,
        val lane: Int,
        val x: Float,
        var top: Float,
        val colorIndex: Int,
    )

    private companion object {
        const val FIXED_STEP = 1f / 60f
        const val MAX_FRAME_DELTA = 0.1f
        const val ENEMY_COLOR_COUNT = 5
        const val CULL_BELOW = 1.06f
        const val RETRY_SPAWN_DELAY = 0.12f
        const val SPAWN_JITTER_MIN = 0.85f
        const val SPAWN_JITTER_MAX = 1.15f
    }

    private var phaseState = RacingPhase.READY
    private var playerX = 0.5f
    private var targetX = 0.5f
    private val obstacles = mutableListOf<ObstacleState>()
    private var nextObstacleId = 0
    private var distance = 0f
    private var elapsed = 0f
    private var speed = config.baseSpeed
    private var spawnTimer = config.spawnIntervalStart
    private var accumulator = 0f

    val phase: RacingPhase get() = phaseState

    /** Horizontal travel limits that keep the whole car on the asphalt. */
    private val minPlayerX: Float
        get() = config.roadInset + config.carWidth / 2f
    private val maxPlayerX: Float
        get() = 1f - config.roadInset - config.carWidth / 2f

    /** Starts a fresh run from any phase, clearing the track and score. */
    fun start() {
        phaseState = RacingPhase.RUNNING
        playerX = 0.5f
        targetX = 0.5f
        obstacles.clear()
        nextObstacleId = 0
        distance = 0f
        elapsed = 0f
        speed = config.baseSpeed
        spawnTimer = config.spawnIntervalStart
        accumulator = 0f
    }

    fun pause() {
        if (phaseState == RacingPhase.RUNNING) phaseState = RacingPhase.PAUSED
    }

    fun resume() {
        if (phaseState == RacingPhase.PAUSED) phaseState = RacingPhase.RUNNING
    }

    fun togglePause() {
        when (phaseState) {
            RacingPhase.RUNNING -> pause()
            RacingPhase.PAUSED -> resume()
            else -> Unit
        }
    }

    /** Requests a steering target; the car eases toward it on later frames. */
    fun steerTo(x: Float) {
        if (phaseState != RacingPhase.RUNNING) return
        targetX = x.coerceIn(minPlayerX, maxPlayerX)
    }

    /**
     * Advances the simulation with a wall-clock delta. The simulation always
     * takes fixed 1/60 s steps so behaviour does not depend on frame rate.
     */
    fun advance(deltaSeconds: Float) {
        if (phaseState != RacingPhase.RUNNING || deltaSeconds <= 0f) return
        accumulator = (accumulator + deltaSeconds).coerceAtMost(MAX_FRAME_DELTA)
        while (accumulator >= FIXED_STEP) {
            step(FIXED_STEP)
            accumulator -= FIXED_STEP
            if (phaseState != RacingPhase.RUNNING) {
                accumulator = 0f
                break
            }
        }
    }

    fun snapshot(): RacingSnapshot = RacingSnapshot(
        phase = phaseState,
        playerX = playerX,
        obstacles = obstacles.map {
            RacingObstacle(id = it.id, lane = it.lane, x = it.x, top = it.top, colorIndex = it.colorIndex)
        },
        score = score(),
        speed = speed,
        distance = distance,
        elapsed = elapsed,
    )

    private fun step(dt: Float) {
        elapsed += dt
        val ramp = (elapsed / config.speedRampSeconds).coerceIn(0f, 1f)
        speed = config.baseSpeed + (config.maxSpeed - config.baseSpeed) * ramp
        distance += speed * dt

        movePlayer(dt)
        updateSpawn(dt)
        moveObstacles(dt)

        if (obstacles.any(::collidesWithPlayer)) {
            phaseState = RacingPhase.GAME_OVER
        }
    }

    private fun movePlayer(dt: Float) {
        val delta = targetX - playerX
        val maxTravel = config.steerSpeed * dt
        playerX = if (abs(delta) <= maxTravel) {
            targetX
        } else {
            playerX + maxTravel * delta.sign
        }
    }

    private fun moveObstacles(dt: Float) {
        val travel = speed * dt
        for (obstacle in obstacles) obstacle.top += travel
        obstacles.removeAll { it.top > CULL_BELOW }
    }

    private fun updateSpawn(dt: Float) {
        spawnTimer -= dt
        if (spawnTimer > 0f) return
        if (obstacles.size >= config.maxObstacles) {
            spawnTimer = RETRY_SPAWN_DELAY
            return
        }

        // Keep the top of the track readable: never drop a car onto a lane that
        // already has traffic near its entry point, and prefer the quieter lanes.
        val candidates = (0 until config.laneCount).filter { lane ->
            obstacles.none { it.lane == lane && it.top < config.topClearZone }
        }
        if (candidates.isEmpty()) {
            spawnTimer = RETRY_SPAWN_DELAY
            return
        }

        val lane = candidates[random.nextInt(candidates.size)]
        obstacles += ObstacleState(
            id = nextObstacleId++,
            lane = lane,
            x = laneCenter(lane),
            top = -config.carHeight,
            colorIndex = random.nextInt(ENEMY_COLOR_COUNT),
        )

        val ramp = (elapsed / config.speedRampSeconds).coerceIn(0f, 1f)
        val interval = config.spawnIntervalStart +
            (config.spawnIntervalMin - config.spawnIntervalStart) * ramp
        spawnTimer = interval * (SPAWN_JITTER_MIN + random.nextFloat() * (SPAWN_JITTER_MAX - SPAWN_JITTER_MIN))
    }

    private fun laneCenter(lane: Int): Float {
        val trackWidth = 1f - config.roadInset * 2f
        return config.roadInset + trackWidth * (lane + 0.5f) / config.laneCount
    }

    private fun collidesWithPlayer(obstacle: ObstacleState): Boolean {
        val usableWidth = config.carWidth * (1f - config.collisionForgiveness)
        val usableHeight = config.carHeight * (1f - config.collisionForgiveness)
        val horizontalGap = abs(playerX - obstacle.x)
        val verticalGap = abs(config.playerTop - obstacle.top)
        return horizontalGap < usableWidth && verticalGap < usableHeight
    }

    private fun score(): Int = (distance * config.scorePerUnit).toInt().coerceAtLeast(0)
}
