package com.zq.topnews.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class RacingGameEngineTest {

    private fun engine(
        config: RacingConfig = RacingConfig(),
        seed: Int = 7,
    ): RacingGameEngine = RacingGameEngine(config = config, random = Random(seed))

    /** Advances in small steps because [RacingGameEngine.advance] caps one call. */
    private fun runFor(engine: RacingGameEngine, seconds: Float, step: Float = 0.1f) {
        var remaining = seconds
        while (remaining > 0f) {
            engine.advance(minOf(step, remaining))
            remaining -= step
        }
    }

    @Test
    fun `new engine waits on the start line`() {
        val snapshot = engine().snapshot()

        assertEquals(RacingPhase.READY, snapshot.phase)
        assertEquals(0, snapshot.score)
        assertTrue(snapshot.obstacles.isEmpty())
        assertEquals(0f, snapshot.distance, 0.0001f)
    }

    @Test
    fun `start resets the run to the middle lane`() {
        val game = engine()
        runFor(game, 2f)
        game.start()

        val snapshot = game.snapshot()
        assertEquals(RacingPhase.RUNNING, snapshot.phase)
        assertEquals(0, snapshot.score)
        assertEquals(0.5f, snapshot.playerX, 0.0001f)
        assertEquals(game.config.baseSpeed, snapshot.speed, 0.0001f)
        assertTrue(snapshot.obstacles.isEmpty())
    }

    @Test
    fun `driving forward increases distance and score`() {
        val game = engine()
        game.start()
        runFor(game, 5f)

        val snapshot = game.snapshot()
        assertTrue(snapshot.distance > 0f)
        assertTrue(snapshot.score > 0)
        assertTrue(snapshot.speed > game.config.baseSpeed)
    }

    @Test
    fun `speed ramps up but never passes the configured maximum`() {
        val config = RacingConfig(spawnIntervalStart = 999f, spawnIntervalMin = 999f, speedRampSeconds = 10f)
        val game = engine(config)
        game.start()
        runFor(game, 12f)

        val snapshot = game.snapshot()
        assertEquals(RacingPhase.RUNNING, snapshot.phase)
        assertEquals(config.maxSpeed, snapshot.speed, 0.001f)
    }

    @Test
    fun `steering is clamped to the asphalt`() {
        val game = engine(RacingConfig(spawnIntervalStart = 999f, spawnIntervalMin = 999f))
        game.start()

        game.steerTo(-10f)
        runFor(game, 2f)
        val left = game.snapshot().playerX
        assertEquals(game.config.roadInset + game.config.carWidth / 2f, left, 0.0005f)

        game.steerTo(10f)
        runFor(game, 2f)
        val right = game.snapshot().playerX
        assertEquals(1f - game.config.roadInset - game.config.carWidth / 2f, right, 0.0005f)
    }

    @Test
    fun `pause freezes the simulation until resume`() {
        val game = engine()
        game.start()
        runFor(game, 1f)
        val before = game.snapshot()

        game.pause()
        runFor(game, 2f)
        val paused = game.snapshot()

        assertEquals(RacingPhase.PAUSED, paused.phase)
        assertEquals(before.score, paused.score)
        assertEquals(before.distance, paused.distance, 0.0001f)

        game.resume()
        runFor(game, 1f)
        val resumed = game.snapshot()
        assertEquals(RacingPhase.RUNNING, resumed.phase)
        assertTrue(resumed.distance > before.distance)
    }

    @Test
    fun `a car in the player lane ends the run and freezes the score`() {
        // One lane and frequent traffic make a collision unavoidable.
        val game = engine(
            RacingConfig(laneCount = 1, spawnIntervalStart = 0.3f, spawnIntervalMin = 0.3f),
        )
        game.start()
        runFor(game, 10f)

        val crashed = game.snapshot()
        assertEquals(RacingPhase.GAME_OVER, crashed.phase)

        runFor(game, 5f)
        val after = game.snapshot()
        assertEquals(RacingPhase.GAME_OVER, after.phase)
        assertEquals(crashed.score, after.score)
        assertEquals(crashed.distance, after.distance, 0.0001f)
    }

    @Test
    fun `restart clears the crashed run`() {
        val game = engine(
            RacingConfig(laneCount = 1, spawnIntervalStart = 0.3f, spawnIntervalMin = 0.3f),
        )
        game.start()
        runFor(game, 10f)
        assertEquals(RacingPhase.GAME_OVER, game.snapshot().phase)

        game.start()
        val restarted = game.snapshot()
        assertEquals(RacingPhase.RUNNING, restarted.phase)
        assertEquals(0, restarted.score)
        assertTrue(restarted.obstacles.isEmpty())
    }

    @Test
    fun `obstacles spawn at lane centres with unique ids`() {
        val game = engine()
        game.start()
        val config = game.config
        val trackWidth = 1f - config.roadInset * 2f
        val laneCentres = (0 until config.laneCount).map { lane ->
            config.roadInset + trackWidth * (lane + 0.5f) / config.laneCount
        }
        val seen = LinkedHashMap<Int, RacingObstacle>()

        var elapsed = 0f
        while (game.phase == RacingPhase.RUNNING && elapsed < 8f) {
            game.advance(0.1f)
            elapsed += 0.1f
            game.snapshot().obstacles.forEach { seen[it.id] = it }
        }

        assertTrue(seen.isNotEmpty())
        seen.values.forEach { obstacle ->
            assertTrue(obstacle.lane in laneCentres.indices)
            assertTrue(abs(obstacle.x - laneCentres[obstacle.lane]) < 0.0005f)
        }
    }

    @Test
    fun `spawning always leaves a lane open near the entry point`() {
        val game = engine()
        game.start()
        val config = game.config

        var elapsed = 0f
        while (game.phase == RacingPhase.RUNNING && elapsed < 15f) {
            game.advance(0.1f)
            elapsed += 0.1f
            val nearEntry = game.snapshot().obstacles.filter {
                it.top > -config.carHeight && it.top < config.topClearZone
            }
            assertTrue(
                "blocked lanes: ${nearEntry.map { it.lane }}",
                nearEntry.map { it.lane }.distinct().size < config.laneCount,
            )
        }
        assertTrue(elapsed > 0f)
    }

    @Test
    fun `steering into an empty part of the road avoids traffic`() {
        // One lane of traffic, but the car can still sit beside it at the edge.
        val game = engine(
            RacingConfig(laneCount = 1, spawnIntervalStart = 0.3f, spawnIntervalMin = 0.3f),
        )
        game.start()
        game.steerTo(0f)
        runFor(game, 10f)

        assertEquals(RacingPhase.RUNNING, game.snapshot().phase)
    }

    @Test
    fun `steering is ignored before the run starts`() {
        val game = engine()
        game.steerTo(0.9f)

        assertEquals(0.5f, game.snapshot().playerX, 0.0001f)
    }

    @Test
    fun `tap steering moves toward the requested side`() {
        val game = engine()
        game.start()
        game.steerTo(0.85f)
        runFor(game, 0.5f)

        assertNotEquals(0.5f, game.snapshot().playerX)
        assertTrue(game.snapshot().playerX > 0.5f)
    }
}
