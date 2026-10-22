package net.iegy.hisn.ui.game

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.iegy.hisn.R
import kotlin.random.Random

/**
 * Endless runner — comfortable, difficulty-selectable rewrite of the classic offline game.
 *
 * Improvements over the original:
 *  - Three difficulty levels (Easy / Normal / Hard) with different speed ramps & spawn gaps
 *  - Hold-to-jump higher, short tap for a small hop
 *  - Duck (long-press bottom half or second finger) to pass under birds (Normal+)
 *  - Pause button, persistent high scores per difficulty
 *  - Larger hitbox forgiveness on Easy, slower early game, clearer UI
 */
enum class GameDifficulty(val labelAr: String, val labelEn: String) {
    EASY("سهل", "Easy"),
    NORMAL("عادي", "Normal"),
    HARD("صعب", "Hard"),
}

private data class DifficultyConfig(
    val baseSpeed: Float,
    val maxSpeed: Float,
    val speedGainPerDistance: Float,
    val gravity: Float,
    val jumpVelocity: Float,
    val holdJumpBoost: Float,
    val minGap: Float,
    val maxGap: Float,
    val collisionPad: Float,
    val birds: Boolean,
    val scoreDivisor: Float,
)

private fun configFor(d: GameDifficulty) = when (d) {
    GameDifficulty.EASY -> DifficultyConfig(
        baseSpeed = 260f, maxSpeed = 480f, speedGainPerDistance = 1f / 90f,
        gravity = 1550f, jumpVelocity = 560f, holdJumpBoost = 900f,
        minGap = 380f, maxGap = 620f, collisionPad = 14f, birds = false, scoreDivisor = 14f,
    )
    GameDifficulty.NORMAL -> DifficultyConfig(
        baseSpeed = 320f, maxSpeed = 620f, speedGainPerDistance = 1f / 70f,
        gravity = 1700f, jumpVelocity = 600f, holdJumpBoost = 950f,
        minGap = 300f, maxGap = 520f, collisionPad = 10f, birds = true, scoreDivisor = 12f,
    )
    GameDifficulty.HARD -> DifficultyConfig(
        baseSpeed = 380f, maxSpeed = 780f, speedGainPerDistance = 1f / 50f,
        gravity = 1900f, jumpVelocity = 640f, holdJumpBoost = 1000f,
        minGap = 220f, maxGap = 400f, collisionPad = 6f, birds = true, scoreDivisor = 10f,
    )
}

private enum class ObstacleKind { CACTUS, BIRD }

private data class Obstacle(
    val x: Float,
    val width: Float,
    val height: Float,
    val kind: ObstacleKind,
    /** Ground y=0; birds float at [airY] (bottom of hitbox above ground). */
    val airY: Float = 0f,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DinoGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("hisn_dino", Context.MODE_PRIVATE)
    }

    var difficulty by remember {
        mutableStateOf(
            runCatching { GameDifficulty.valueOf(prefs.getString("difficulty", "EASY")!!) }
                .getOrDefault(GameDifficulty.EASY),
        )
    }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("sound", true)) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember {
        mutableIntStateOf(prefs.getInt("hi_${difficulty.name}", 0))
    }

    var dinoY by remember { mutableFloatStateOf(0f) }
    var velocity by remember { mutableFloatStateOf(0f) }
    var ducking by remember { mutableStateOf(false) }
    var holdingJump by remember { mutableStateOf(false) }
    var obstacles by remember { mutableStateOf(listOf<Obstacle>()) }
    var distance by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(configFor(difficulty).baseSpeed) }
    var nextSpawnAt by remember { mutableFloatStateOf(500f) }
    var frame by remember { mutableIntStateOf(0) }
    var clouds by remember {
        mutableStateOf(List(4) { i ->
            Offset(120f + i * 220f, 40f + Random.nextFloat() * 80f)
        })
    }

    val cfg = configFor(difficulty)
    val groundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val dinoColor = MaterialTheme.colorScheme.primary
    val cactusColor = MaterialTheme.colorScheme.tertiary
    val birdColor = MaterialTheme.colorScheme.secondary
    val skyColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current ==
        androidx.compose.ui.unit.LayoutDirection.Rtl

    val tones = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 65)
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                tones?.release()
            } catch (_: Exception) {
            }
        }
    }

    fun beep(type: Int, durationMs: Int = 70) {
        if (!soundOn) return
        try {
            tones?.startTone(type, durationMs)
        } catch (_: Exception) {
        }
    }

    fun persistHigh(sc: Int) {
        val key = "hi_${difficulty.name}"
        val prev = prefs.getInt(key, 0)
        if (sc > prev) {
            prefs.edit().putInt(key, sc).apply()
            highScore = sc
        }
    }

    fun reset() {
        val c = configFor(difficulty)
        dinoY = 0f
        velocity = 0f
        ducking = false
        holdingJump = false
        obstacles = emptyList()
        distance = 0f
        speed = c.baseSpeed
        nextSpawnAt = c.minGap + Random.nextFloat() * 80f
        score = 0
        gameOver = false
        paused = false
        running = true
        highScore = prefs.getInt("hi_${difficulty.name}", 0)
        beep(ToneGenerator.TONE_PROP_BEEP, 40)
    }

    fun startJump() {
        when {
            gameOver || !running -> reset()
            paused -> {
                paused = false
            }
            dinoY <= 1.5f && !ducking -> {
                velocity = configFor(difficulty).jumpVelocity
                holdingJump = true
                beep(ToneGenerator.TONE_DTMF_1, 35)
            }
        }
    }

    fun endJump() {
        holdingJump = false
        // Cut upward velocity for short hops (variable jump height).
        if (velocity > 180f) velocity *= 0.55f
    }

    LaunchedEffect(running, gameOver, paused, difficulty) {
        if (!running || gameOver || paused) return@LaunchedEffect
        var acc = 0f
        val step = 1f / 60f
        var lastScoreBucket = 0
        while (true) {
            withFrameMillis {
                acc = (acc + step).coerceAtMost(0.08f)
                var steps = 0
                val c = configFor(difficulty)
                while (acc >= step && steps < 4) {
                    acc -= step
                    steps++

                    // Variable jump: hold adds a bit of anti-gravity near the start of the jump.
                    if (holdingJump && velocity > 0f && dinoY < 90f) {
                        velocity += c.holdJumpBoost * step * 0.35f
                    }
                    velocity -= c.gravity * step
                    dinoY += velocity * step
                    if (dinoY < 0f) {
                        dinoY = 0f
                        velocity = 0f
                    }

                    distance += speed * step
                    speed = (c.baseSpeed + distance * c.speedGainPerDistance).coerceAtMost(c.maxSpeed)

                    val newScore = (distance / c.scoreDivisor).toInt()
                    if (newScore != score) {
                        score = newScore
                        val bucket = newScore / 100
                        if (bucket > lastScoreBucket) {
                            lastScoreBucket = bucket
                            beep(ToneGenerator.TONE_PROP_ACK, 50)
                        }
                    }

                    // Clouds drift slowly
                    clouds = clouds.map { o ->
                        val nx = o.x - speed * step * 0.15f
                        if (nx < -80f) Offset(1000f + Random.nextFloat() * 120f, 30f + Random.nextFloat() * 90f)
                        else Offset(nx, o.y)
                    }

                    if (distance >= nextSpawnAt) {
                        val roll = Random.nextFloat()
                        val kind = if (c.birds && roll > 0.72f) ObstacleKind.BIRD else ObstacleKind.CACTUS
                        val (w, h, air) = when (kind) {
                            ObstacleKind.CACTUS -> {
                                val hh = 36f + Random.nextFloat() * 48f
                                Triple(22f + Random.nextFloat() * 28f, hh, 0f)
                            }
                            ObstacleKind.BIRD -> {
                                // Two altitudes: low (must duck) or high (must jump).
                                val low = Random.nextBoolean()
                                Triple(40f, 28f, if (low) 38f else 78f)
                            }
                        }
                        obstacles = obstacles + Obstacle(x = 1000f, width = w, height = h, kind = kind, airY = air)
                        val gap = c.minGap + Random.nextFloat() * (c.maxGap - c.minGap)
                        nextSpawnAt = distance + gap - (speed - c.baseSpeed) * 0.12f
                    }

                    if (obstacles.isNotEmpty()) {
                        val moved = ArrayList<Obstacle>(obstacles.size)
                        for (o in obstacles) {
                            val nx = o.x - speed * step
                            if (nx + o.width > -40f) moved.add(o.copy(x = nx))
                        }
                        obstacles = moved
                    }

                    // Collision
                    val dinoH = if (ducking) 28f else 48f
                    val dinoW = if (ducking) 52f else 40f
                    val dinoLeft = 78f
                    val dinoRight = dinoLeft + dinoW
                    val dinoBottom = dinoY
                    val dinoTop = dinoY + dinoH
                    val pad = c.collisionPad
                    for (o in obstacles) {
                        val oBottom = o.airY
                        val oTop = o.airY + o.height
                        if (dinoRight > o.x + pad &&
                            dinoLeft < o.x + o.width - pad &&
                            dinoTop > oBottom + pad * 0.5f &&
                            dinoBottom < oTop - pad * 0.5f
                        ) {
                            gameOver = true
                            running = false
                            persistHigh(score)
                            beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 110)
                            return@withFrameMillis
                        }
                    }
                }
                frame++
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.game_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (running && !gameOver) {
                        IconButton(onClick = { paused = !paused }) {
                            Icon(
                                if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null,
                            )
                        }
                    }
                    IconButton(onClick = {
                        soundOn = !soundOn
                        prefs.edit().putBoolean("sound", soundOn).apply()
                    }) {
                        Icon(
                            if (soundOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(skyColor),
        ) {
            // Difficulty chips (only when not mid-run, or always allow change on game over)
            if (!running || gameOver) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    GameDifficulty.entries.forEach { d ->
                        FilterChip(
                            selected = difficulty == d,
                            onClick = {
                                difficulty = d
                                prefs.edit().putString("difficulty", d.name).apply()
                                highScore = prefs.getInt("hi_${d.name}", 0)
                            },
                            label = {
                                Text(
                                    if (isRtl) d.labelAr else d.labelEn,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.game_score, score),
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Text(
                    text = stringResource(R.string.game_high_score, highScore),
                    color = textColor.copy(alpha = 0.75f),
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(running, gameOver, paused) {
                        detectTapGestures(
                            onPress = { offset ->
                                val h = size.height.toFloat()
                                // Bottom 35% of screen → duck while held (when on ground).
                                if (offset.y > h * 0.65f && running && !gameOver && !paused && dinoY <= 1.5f) {
                                    ducking = true
                                    try {
                                        awaitRelease()
                                    } finally {
                                        ducking = false
                                    }
                                } else {
                                    startJump()
                                    try {
                                        awaitRelease()
                                    } finally {
                                        endJump()
                                    }
                                }
                            },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val scale = size.height / 220f
                    val groundY = size.height * 0.78f

                    // Sky gradient strip
                    drawRect(skyColor)

                    // Clouds
                    for (cl in clouds) {
                        val cx = cl.x * (size.width / 1000f)
                        val cy = cl.y * scale
                        drawRoundRect(
                            Color.Gray.copy(alpha = 0.18f),
                            Offset(cx, cy),
                            Size(70f * scale, 22f * scale),
                            CornerRadius(12f, 12f),
                        )
                    }

                    // Ground
                    drawRect(
                        groundColor,
                        topLeft = Offset(0f, groundY),
                        size = Size(size.width, size.height - groundY),
                    )
                    // Ground line ticks
                    val tick = ((distance * 0.4f) % 40f)
                    var tx = -tick
                    while (tx < size.width) {
                        drawRect(
                            groundColor.copy(alpha = 0.35f),
                            Offset(tx, groundY),
                            Size(18f, 3f),
                        )
                        tx += 40f
                    }

                    // Obstacles
                    for (o in obstacles) {
                        val ox = o.x * (size.width / 1000f)
                        val ow = o.width * scale
                        val oh = o.height * scale
                        when (o.kind) {
                            ObstacleKind.CACTUS -> {
                                val top = groundY - oh
                                drawRoundRect(
                                    cactusColor,
                                    Offset(ox, top),
                                    Size(ow, oh),
                                    CornerRadius(4f, 4f),
                                )
                                // Arms
                                if (ow > 28f * scale) {
                                    drawRoundRect(
                                        cactusColor,
                                        Offset(ox - 10f * scale, top + oh * 0.35f),
                                        Size(12f * scale, oh * 0.25f),
                                        CornerRadius(3f, 3f),
                                    )
                                }
                            }
                            ObstacleKind.BIRD -> {
                                val bottom = groundY - o.airY * scale
                                val top = bottom - oh
                                // Wings flap by frame
                                val wing = if ((frame / 8) % 2 == 0) -6f else 6f
                                drawRoundRect(
                                    birdColor,
                                    Offset(ox, top),
                                    Size(ow, oh * 0.55f),
                                    CornerRadius(8f, 8f),
                                )
                                drawRoundRect(
                                    birdColor.copy(alpha = 0.85f),
                                    Offset(ox + ow * 0.15f, top + wing * scale),
                                    Size(ow * 0.7f, oh * 0.35f),
                                    CornerRadius(6f, 6f),
                                )
                            }
                        }
                    }

                    // Dino
                    val dw = (if (ducking) 52f else 40f) * scale
                    val dh = (if (ducking) 28f else 48f) * scale
                    val dx = 78f * (size.width / 1000f)
                    val dy = groundY - dinoY * scale - dh
                    val legPhase = if (dinoY > 1f) 0 else (frame / 6) % 2

                    drawRoundRect(
                        dinoColor,
                        Offset(dx, dy),
                        Size(dw, dh * 0.72f),
                        CornerRadius(6f, 6f),
                    )
                    // Head
                    if (!ducking) {
                        drawRoundRect(
                            dinoColor,
                            Offset(dx + dw * 0.45f, dy - dh * 0.22f),
                            Size(dw * 0.55f, dh * 0.38f),
                            CornerRadius(5f, 5f),
                        )
                        drawCircle(
                            Color.Black,
                            radius = 2.2f * scale,
                            center = Offset(dx + dw * 0.88f, dy - dh * 0.05f),
                        )
                    } else {
                        drawCircle(
                            Color.Black,
                            radius = 2f * scale,
                            center = Offset(dx + dw * 0.85f, dy + dh * 0.25f),
                        )
                    }
                    // Legs
                    if (!ducking) {
                        val legY = dy + dh * 0.68f
                        val legH = dh * 0.32f
                        if (legPhase == 0) {
                            drawRoundRect(dinoColor, Offset(dx + 4f * scale, legY), Size(dw * 0.28f, legH), CornerRadius(2f, 2f))
                            drawRoundRect(dinoColor, Offset(dx + dw * 0.5f, legY + 3f * scale), Size(dw * 0.28f, legH * 0.85f), CornerRadius(2f, 2f))
                        } else {
                            drawRoundRect(dinoColor, Offset(dx + 4f * scale, legY + 3f * scale), Size(dw * 0.28f, legH * 0.85f), CornerRadius(2f, 2f))
                            drawRoundRect(dinoColor, Offset(dx + dw * 0.5f, legY), Size(dw * 0.28f, legH), CornerRadius(2f, 2f))
                        }
                    }
                }

                if (!running && !gameOver) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.game_tap_start),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.game_hint_v2),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
                if (paused && running) {
                    Surface(
                        Modifier.align(Alignment.Center),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    ) {
                        Text(
                            stringResource(R.string.game_paused),
                            Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                    }
                }
                if (gameOver) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.game_over),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                        Text(
                            stringResource(R.string.game_score, score),
                            modifier = Modifier.padding(top = 6.dp),
                            color = textColor.copy(alpha = 0.9f),
                        )
                        Text(
                            stringResource(R.string.game_tap_restart),
                            modifier = Modifier.padding(top = 10.dp),
                            color = textColor.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // Control hints bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HintChip(stringResource(R.string.game_hint_jump))
                HintChip(stringResource(R.string.game_hint_duck))
                HintChip(stringResource(R.string.game_hint_hold))
            }
        }
    }
}

@Composable
private fun HintChip(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
