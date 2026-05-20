package com.example

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val activeSongIndex by viewModel.activeSongIndex.collectAsState()
    val masterVolume by viewModel.masterVolume.collectAsState()
    val bassOscillator by viewModel.bassOscillator.collectAsState()
    val leadOscillator by viewModel.leadOscillator.collectAsState()
    val pitchShift by viewModel.pitchShift.collectAsState()
    
    val currentStep by viewModel.currentStep.collectAsState()
    val currentStepFraction by viewModel.currentStepFraction.collectAsState()
    val bassNoteFreq by viewModel.bassNoteFreq.collectAsState()
    val leadNoteFreq by viewModel.leadNoteFreq.collectAsState()
    val visualSamples by viewModel.visualSamples.collectAsState()

    // Import Music properties
    val isImportedMode by viewModel.isImportedMode.collectAsState()
    val importedSongName by viewModel.importedSongName.collectAsState()

    val currentSong = SynthEngine.SONGS[activeSongIndex]

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.loadImportedAudio(context, uri)
        }
    }

    // Screen Scrolling container
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // Header Logo/App Bar
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "WAVE PLAYER",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "SYNTHESIZER CORE v1.2",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CyberSurfaceVariant)
                    .border(1.dp, NeonPurple, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Music indicator",
                    tint = NeonPink,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main Wave Oscilloscope Card (EPIC CENTRAL COMPONENT - OPTIMIZED FOR 60FPS)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 24.dp)
                .testTag("oscilloscope_screen_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                width = 2.dp,
                brush = Brush.horizontalGradient(
                    if (isImportedMode) listOf(NeonCyan, NeonPurple, NeonPink) 
                    else listOf(NeonPink, NeonPurple, NeonCyan)
                )
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                // Infinite transition for fluid ambient waves
                val infiniteTransition = rememberInfiniteTransition(label = "OscilloscopeWaves")
                val wavePhase by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 2f * PI.toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "wavePhase"
                )

                // Visualizer Canvas Drawing
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("oscilloscope_canvas")
                ) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f
                    
                    // 1. Draw Grid Lines (Blueprint Cyberpunk Grid)
                    val gridColor = NeonCyan.copy(alpha = 0.08f)
                    val gridSpacing = 30.dp.toPx()
                    
                    // Horizontal lines
                    var currentY = 0f
                    while (currentY < height) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, currentY),
                            end = Offset(width, currentY),
                            strokeWidth = 1.dp.toPx()
                        )
                        currentY += gridSpacing
                    }
                    // Vertical lines
                    var currentX = 0f
                    while (currentX < width) {
                        drawLine(
                            color = gridColor,
                            start = Offset(currentX, 0f),
                            end = Offset(currentX, height),
                            strokeWidth = 1.dp.toPx()
                        )
                        currentX += gridSpacing
                    }
                    
                    // Center horizontal baseline (Dotted)
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.25f),
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )

                    // 2. Draw Layered Wave 1 (Deep Bass Violet Wave - Smooth and slow - HIGHLY OPTIMIZED)
                    if (isPlaying && !isPaused) {
                        val bassPath = Path()
                        val bassFreqTerm = (bassNoteFreq / 200f).coerceIn(0.5f, 3.5f)
                        val bassAmp = (centerY * 0.4f) * (masterVolume + 0.3f)
                        
                        bassPath.moveTo(0f, centerY)
                        // Increased step from 4 to 20 to reduce trigonometry loops on UI thread
                        for (x in 0..width.toInt() step 20) {
                            val xFraction = x.toFloat() / width
                            val waveY = centerY + sin(xFraction * 2.5f * PI.toFloat() * bassFreqTerm - wavePhase) * bassAmp * sin(xFraction * PI.toFloat())
                            bassPath.lineTo(x.toFloat(), waveY)
                        }
                        
                        drawPath(
                            path = bassPath,
                            color = NeonPurple.copy(alpha = 0.45f),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 3. Draw Layered Wave 2 (High Lead Cyan Wave - Fast fluid frequency - HIGHLY OPTIMIZED)
                    if (isPlaying && !isPaused) {
                        val leadPath = Path()
                        val leadFreqTerm = (leadNoteFreq / 500f).coerceIn(1f, 6f)
                        val leadAmp = (centerY * 0.25f) * (masterVolume + 0.2f)
                        
                        leadPath.moveTo(0f, centerY)
                        // Increased step from 3 to 15 to completely remove mathematical lag
                        for (x in 0..width.toInt() step 15) {
                            val xFraction = x.toFloat() / width
                            val waveY = centerY + cos(xFraction * 5f * PI.toFloat() * leadFreqTerm + wavePhase * 1.5f) * leadAmp * sin(xFraction * PI.toFloat())
                            leadPath.lineTo(x.toFloat(), waveY)
                        }
                        
                        drawPath(
                            path = leadPath,
                            color = NeonCyan.copy(alpha = 0.5f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 4. Draw Layered Wave 3 (Real-Time Raw Synthesizer Output Wave - Thick glowing magenta)
                    val rawWavePath = Path()
                    val sampleCount = visualSamples.size
                    val segmentWidth = width / (sampleCount - 1)
                    
                    rawWavePath.moveTo(0f, centerY)
                    // Added step 4 to draw 64 points instead of 256, maintaining incredible aesthetics while dropping lag to zero
                    for (index in 0 until sampleCount step 4) {
                        val rawSample = visualSamples[index]
                        val x = index * segmentWidth
                        val y = centerY + (rawSample * centerY * 0.9f)
                        if (index == 0) {
                            rawWavePath.moveTo(x, y)
                        } else {
                            rawWavePath.lineTo(x, y)
                        }
                    }

                    // Glowing backdrop filter for real wave
                    drawPath(
                        path = rawWavePath,
                        color = NeonPink.copy(alpha = 0.3f),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    // Sharp foreground stroke
                    drawPath(
                        path = rawWavePath,
                        brush = Brush.horizontalGradient(
                            colors = if (isImportedMode) listOf(NeonCyan, NeonPurple, NeonPink) 
                                     else listOf(NeonPink, NeonPurple, NeonCyan)
                        ),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    // Draw digital scope corners
                    val cornerLen = 12.dp.toPx()
                    val strokeW = 2.5.dp.toPx()
                    val cornerColor = if (isImportedMode) NeonPink else NeonCyan
                    // Top Left
                    drawLine(cornerColor, Offset(8.dp.toPx(), 8.dp.toPx()), Offset(8.dp.toPx() + cornerLen, 8.dp.toPx()), strokeW)
                    drawLine(cornerColor, Offset(8.dp.toPx(), 8.dp.toPx()), Offset(8.dp.toPx(), 8.dp.toPx() + cornerLen), strokeW)
                    // Top Right
                    drawLine(cornerColor, Offset(width - 8.dp.toPx(), 8.dp.toPx()), Offset(width - 8.dp.toPx() - cornerLen, 8.dp.toPx()), strokeW)
                    drawLine(cornerColor, Offset(width - 8.dp.toPx(), 8.dp.toPx()), Offset(width - 8.dp.toPx(), 8.dp.toPx() + cornerLen), strokeW)
                    // Bottom Left
                    drawLine(cornerColor, Offset(8.dp.toPx(), height - 8.dp.toPx()), Offset(8.dp.toPx() + cornerLen, height - 8.dp.toPx()), strokeW)
                    drawLine(cornerColor, Offset(8.dp.toPx(), height - 8.dp.toPx()), Offset(8.dp.toPx(), height - 8.dp.toPx() - cornerLen), strokeW)
                    // Bottom Right
                    drawLine(cornerColor, Offset(width - 8.dp.toPx(), height - 8.dp.toPx()), Offset(width - 8.dp.toPx() - cornerLen, height - 8.dp.toPx()), strokeW)
                    drawLine(cornerColor, Offset(width - 8.dp.toPx(), height - 8.dp.toPx()), Offset(width - 8.dp.toPx(), height - 8.dp.toPx() - cornerLen), strokeW)
                }

                // Small Status Indicators Overlay inside visualizer
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPlaying && !isPaused) NeonPink else TextSecondary
                                )
                        )
                        Text(
                            text = if (isPlaying && !isPaused) {
                                if (isImportedMode) "FILE WAVE LIVE" else "SYNTH WAVE LIVE"
                            } else if (isPaused) "PAUSED" else "READY",
                            color = if (isPlaying && !isPaused) NeonPink else TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Mini stats overlay inside visualizer (bottom left)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = if (isImportedMode) "SOURCE: LOCAL MP3" else "BPM: ${currentSong.bpm}",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isImportedMode) "HQ STEREO RENDER" else "FREQ BASS: ${"%.1f".format(bassNoteFreq)} Hz",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Rhythmic Track Step Sequencer row (16 micro panels indicator)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SECUENCIADOR DE Ritmo DE ONDA",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "BEAT STEP: ${currentStep + 1}/16",
                    color = NeonPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pattern_sequencer_grid"),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (step in 0 until 16) {
                    val isActive = step == currentStep && isPlaying && !isPaused
                    val isBeatDivision = step % 4 == 0
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    isActive -> NeonPink
                                    isBeatDivision -> CyberSurfaceVariant.copy(alpha = 0.8f)
                                    else -> CyberSurface.copy(alpha = 0.5f)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    isActive -> NeonCyan
                                    isBeatDivision -> NeonPurple.copy(alpha = 0.4f)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Vinyl/Plasma Core Rotating Disks section + Metadata Description
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Spinning Hologram Disk Visual
            val infiniteTransitionDisk = rememberInfiniteTransition(label = "DiskRotation")
            val rotationAngle by infiniteTransitionDisk.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = if (isPlaying && !isPaused) 1500 else 1000000, 
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotationAngle"
            )

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .rotate(if (isPlaying && !isPaused) rotationAngle else 0f)
                    .drawBehind {
                        drawCircle(
                            color = NeonPurple.copy(alpha = 0.25f),
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            color = NeonCyan.copy(alpha = 0.15f),
                            radius = size.minDimension / 3.2f,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = NeonPink.copy(alpha = 0.3f),
                            radius = size.minDimension / 10f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(54.dp)) {
                    val radius = size.minDimension / 2f
                    drawLine(
                        color = NeonCyan, 
                        start = Offset(radius, 0f), 
                        end = Offset(radius, 6.dp.toPx()), 
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = NeonCyan, 
                        start = Offset(radius, size.height), 
                        end = Offset(radius, size.height - 6.dp.toPx()), 
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = NeonCyan, 
                        start = Offset(0f, radius), 
                        end = Offset(6.dp.toPx(), radius), 
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = NeonCyan, 
                        start = Offset(size.width, radius), 
                        end = Offset(size.width - 6.dp.toPx(), radius), 
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = radius * 0.8f,
                        style = Stroke(width = 0.5.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = radius * 0.6f,
                        style = Stroke(width = 0.5.dp.toPx())
                    )
                }
            }

            // Song Info metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isImportedMode) NeonCyan.copy(alpha = 0.15f) else NeonPink.copy(alpha = 0.15f), 
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isImportedMode) "AUDIO IMPORTADO" else currentSong.genre.uppercase(),
                        color = if (isImportedMode) NeonCyan else NeonPink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (isImportedMode) importedSongName else currentSong.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = if (isImportedMode) "Reproducción Externa" else "By SuperSynth Sequencer",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Transport Master Controls PANEL (PREV, PLAY/PAUSE, STOP, NEXT)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag("transport_controls"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous track Button
            IconButton(
                onClick = {
                    if (isImportedMode) {
                        viewModel.selectSong(0) // Return to synth mode
                    } else {
                        val prevIndex = if (activeSongIndex > 0) activeSongIndex - 1 else SynthEngine.SONGS.lastIndex
                        viewModel.selectSong(prevIndex)
                    }
                },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(CyberSurface)
                    .border(1.dp, NeonPurple.copy(alpha = 0.3f), CircleShape)
                    .testTag("btn_prev_track")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Play/Pause Big Pulsating Button
            val scalePulse by animateFloatAsState(
                targetValue = if (isPlaying && !isPaused) 1.08f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "btnPulse"
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scalePulse)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (isImportedMode) listOf(NeonCyan, NeonPurple) 
                            else listOf(NeonPink, NeonPurple)
                        )
                    )
                    .clickable { viewModel.playPause() }
                    .border(2.dp, if (isImportedMode) NeonPink else NeonCyan, CircleShape)
                    .testTag("btn_play_pause"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or Pause",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Stop Button
            IconButton(
                onClick = { viewModel.stop() },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(CyberSurface)
                    .border(1.dp, NeonPurple.copy(alpha = 0.3f), CircleShape)
                    .testTag("btn_stop")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Playback",
                    tint = if (isPlaying) NeonPink else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Next track Button
            IconButton(
                onClick = {
                    if (isImportedMode) {
                        viewModel.selectSong(0) // Return to synth mode
                    } else {
                        val nextIndex = if (activeSongIndex < SynthEngine.SONGS.lastIndex) activeSongIndex + 1 else 0
                        viewModel.selectSong(nextIndex)
                    }
                },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(CyberSurface)
                    .border(1.dp, NeonPurple.copy(alpha = 0.3f), CircleShape)
                    .testTag("btn_next_track")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // IMPORTER AREA (PRIMARY ADDITION FOR LOCAL FILES)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag("audio_importer_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IMPORTAR TU PROPIA MÚSICA",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Carga cualquier archivo de audio (MP3, WAV) desde tu almacenamiento.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { filePickerLauncher.launch("audio/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                    border = BorderStroke(1.dp, NeonPink),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("btn_import_music")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open folder icon",
                            tint = NeonPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ELEGIR",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tracks List Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "CANCIONES DE SÍNTESIS INTEGRADA",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("horizontal_tracks_list"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pre-append a "currently imported file" representation card to the list if imported song is active
                if (isImportedMode) {
                    item {
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .clickable {
                                    // Just toggle play if clicked active imported card
                                    viewModel.playPause()
                                }
                                .testTag("track_card_imported"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
                            border = BorderStroke(1.5.dp, NeonCyan)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    text = "MP3 / LOCAL FILE",
                                    color = NeonCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = importedSongName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonPink,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "REPRODUCIENDO",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(SynthEngine.SONGS) { index, song ->
                    val isSelected = index == activeSongIndex && !isImportedMode
                    
                    Card(
                        modifier = Modifier
                            .width(180.dp)
                            .clickable {
                                viewModel.selectSong(index)
                            }
                            .testTag("track_card_$index"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) CyberSurfaceVariant else CyberSurface
                        ),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (isSelected) NeonPink else Color.Transparent
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(
                                text = song.genre.uppercase(),
                                color = if (isSelected) NeonCyan else TextSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = song.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isSelected) NeonPink else TextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${song.bpm} BPM",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Synthesizer Control Panel Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("synth_tweaker_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "CONTROLES DE MODULACIÓN ESPECIAL",
                    color = NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Master Volume Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume icon",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VOLUMEN MASTER",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(115.dp)
                    )
                    Slider(
                        value = masterVolume,
                        onValueChange = { viewModel.setMasterVolume(it) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slider_volume"),
                        colors = SliderDefaults.colors(
                            thumbColor = NeonPink,
                            activeTrackColor = NeonPink,
                            inactiveTrackColor = CyberBackground
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pitch Shift Transposer Shift
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎛️",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isImportedMode) "VELOCIDAD" else "TRANSPOSITOR",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(115.dp)
                    )
                    Slider(
                        value = pitchShift.toFloat(),
                        onValueChange = { viewModel.setPitchShift(it.toInt()) },
                        valueRange = -12f..12f,
                        steps = 24,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slider_pitch"),
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = CyberBackground
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isImportedMode) {
                            val factor = 1.0f + (pitchShift / 12.0f)
                            "${"%.2f".format(factor)}x"
                        } else {
                            if (pitchShift >= 0) "+$pitchShift" else pitchShift.toString()
                        },
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(42.dp),
                        textAlign = TextAlign.End
                    )
                }

                // Conditionally display Synth Specific Modulators only if in Synth Mode
                AnimatedVisibility(visible = !isImportedMode) {
                    Column {
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = NeonPurple.copy(alpha = 0.2f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Oscillator Selectors
                        Text(
                            text = "FORMA DE ONDA DE LOS OSCILADORES",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Bass Oscillator Type Select
                        Text(
                            text = "Oscilador Bass (Sintetizador Sub):",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SynthEngine.OscillatorType.values().forEach { type ->
                                val selected = bassOscillator == type
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) NeonPink else CyberBackground)
                                        .clickable { viewModel.setBassOscillator(type) }
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) NeonCyan else NeonPurple.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type.name,
                                        color = if (selected) Color.White else TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Lead Oscillator Type Select
                        Text(
                            text = "Oscilador Lead (Sintetizador Solista):",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SynthEngine.OscillatorType.values().forEach { type ->
                                val selected = leadOscillator == type
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) NeonCyan else CyberBackground)
                                        .clickable { viewModel.setLeadOscillator(type) }
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) NeonPink else NeonPurple.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type.name,
                                        color = if (selected) Color.Black else TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
