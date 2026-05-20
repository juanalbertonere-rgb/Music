package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * A real-time software audio synthesizer and step sequencer.
 * Generates energetic retro-futuristic music on the fly and streams
 * synchronized PCM samples to the canvas wave visualizer.
 */
class SynthEngine {

    // Song struct
    data class Song(
        val name: String,
        val bpm: Int,
        val bassPattern: IntArray,
        val leadPattern: IntArray,
        val genre: String,
        val instrumentBass: OscillatorType = OscillatorType.SAWTOOTH,
        val instrumentLead: OscillatorType = OscillatorType.SQUARE
    )

    enum class OscillatorType {
        SINE, TRIANGLE, SQUARE, SAWTOOTH
    }

    companion object {
        private const val SAMPLE_RATE = 22050 // Fast enough for synth audio, easy on CPU
        private const val BUFFER_SIZE = 512
        
        val SONGS = listOf(
            Song(
                name = "Neon Horizon",
                bpm = 118,
                bassPattern = intArrayOf(
                    40, 40, 40, 40, 43, 43, 43, 43,
                    45, 45, 45, 45, 48, 48, 50, 50
                ),
                leadPattern = intArrayOf(
                    64, 0, 67, 69, 0, 72, 74, 0,
                    69, 72, 69, 67, 64, 67, 69, 72
                ),
                genre = "Synthwave / Cyberpunk",
                instrumentBass = OscillatorType.SAWTOOTH,
                instrumentLead = OscillatorType.SQUARE
            ),
            Song(
                name = "Circuit Rush",
                bpm = 135,
                bassPattern = intArrayOf(
                    36, 36, 48, 36, 36, 36, 48, 36,
                    39, 39, 51, 39, 41, 41, 53, 41
                ),
                leadPattern = intArrayOf(
                    60, 63, 60, 65, 60, 67, 65, 63,
                    67, 70, 67, 72, 67, 70, 67, 63
                ),
                genre = "Techno Industrial",
                instrumentBass = OscillatorType.SQUARE,
                instrumentLead = OscillatorType.SAWTOOTH
            ),
            Song(
                name = "Chiptune Odyssey",
                bpm = 142,
                bassPattern = intArrayOf(
                    48, 48, 52, 52, 55, 55, 59, 59,
                    60, 60, 56, 56, 53, 53, 50, 50
                ),
                leadPattern = intArrayOf(
                    72, 76, 79, 84, 83, 79, 76, 72,
                    74, 77, 81, 79, 76, 79, 76, 72
                ),
                genre = "Retro Arcade 8-Bit",
                instrumentBass = OscillatorType.TRIANGLE,
                instrumentLead = OscillatorType.SQUARE
            ),
            Song(
                name = "Cosmic Dream",
                bpm = 90,
                bassPattern = intArrayOf(
                    45, 0, 45, 0, 41, 0, 41, 0,
                    48, 0, 48, 0, 43, 0, 43, 0
                ),
                leadPattern = intArrayOf(
                    69, 71, 72, 76, 74, 72, 71, 69,
                    72, 74, 76, 79, 77, 76, 74, 72
                ),
                genre = "Ambient Space Chillout",
                instrumentBass = OscillatorType.SINE,
                instrumentLead = OscillatorType.TRIANGLE
            )
        )
    }

    private var audioTrack: AudioTrack? = null
    private var synthesisThread: Thread? = null
    
    // Concurrency safety
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    
    // User adjustable params (Volatile or Atomic for safe updates)
    @Volatile var activeSongIndex: Int = 0
    @Volatile var masterVolume: Float = 0.7f
    @Volatile var bassOscillator: OscillatorType = OscillatorType.SAWTOOTH
    @Volatile var leadOscillator: OscillatorType = OscillatorType.SQUARE
    @Volatile var pitchShiftSemitones: Int = 0 // Allows user to pitch shift manually!
    
    // Playback state feedback
    @Volatile var currentStep: Int = 0
        private set
    @Volatile var currentStepFraction: Float = 0f
        private set
    @Volatile var currentBassNoteFrequency: Float = 0f
        private set
    @Volatile var currentLeadNoteFrequency: Float = 0f
        private set

    // Buffer for Visualizer
    private val visualBufferLock = Any()
    private val visualBuffer = FloatArray(256)
    private var visualIndex = 0

    // Sound generation helpers (continuous phase)
    private var phaseBass = 0.0
    private var phaseLead = 0.0
    private var sampleCounter = 0L

    init {
        // Load default song configuration
        setSong(0)
    }

    fun setSong(index: Int) {
        if (index in SONGS.indices) {
            activeSongIndex = index
            val song = SONGS[index]
            bassOscillator = song.instrumentBass
            leadOscillator = song.instrumentLead
        }
    }

    fun start() {
        if (isRunning.get()) return
        
        isRunning.set(true)
        isPaused.set(false)
        sampleCounter = 0
        phaseBass = 0.0
        phaseLead = 0.0

        // Create AudioTrack with Low Latency attributes
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSize.coerceAtLeast(BUFFER_SIZE * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        // Launch the Synth background thread
        synthesisThread = Thread {
            synthesisLoop()
        }.apply {
            name = "SynthEnginePlaybackThread"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun pause() {
        if (isRunning.get()) {
            isPaused.set(!isPaused.get())
            if (isPaused.get()) {
                audioTrack?.pause()
            } else {
                audioTrack?.play()
            }
        }
    }

    fun stop() {
        if (!isRunning.get()) return
        
        isRunning.set(false)
        isPaused.set(false)
        
        try {
            synthesisThread?.join(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        synthesisThread = null

        audioTrack?.apply {
            try {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                // Ignore transient cleanup errors
            }
        }
        audioTrack = null
    }

    private fun midiToFreq(note: Int): Float {
        if (note <= 0) return 0f
        val shiftedNote = note + pitchShiftSemitones
        return 440f * Math.pow(2.0, (shiftedNote - 69) / 12.0).toFloat()
    }

    private fun sampleOscillator(type: OscillatorType, phase: Double): Double {
        val normalizedPhase = (phase % (2.0 * PI))
        return when (type) {
            OscillatorType.SINE -> sin(phase)
            OscillatorType.TRIANGLE -> {
                // Shifts from -1 to 1 symmetrically
                val fraction = normalizedPhase / (2.0 * PI)
                if (fraction < 0.5) {
                    -1.0 + 4.0 * fraction
                } else {
                    3.0 - 4.0 * fraction
                }
            }
            OscillatorType.SQUARE -> {
                if (normalizedPhase < PI) 1.0 else -1.0
            }
            OscillatorType.SAWTOOTH -> {
                -1.0 + 2.0 * (normalizedPhase / (2.0 * PI))
            }
        }
    }

    private fun synthesisLoop() {
        val writeBuffer = ShortArray(BUFFER_SIZE)
        
        while (isRunning.get()) {
            if (isPaused.get()) {
                try {
                    Thread.sleep(20)
                } catch (e: InterruptedException) {
                    break
                }
                continue
            }

            val song = SONGS[activeSongIndex]
            val bpm = song.bpm
            
            // Steps sequence structure
            // 1 beat = 4 steps (1/16th notes sequencer)
            // step duration in samples = (60sec / bpm / 4) * sampleRate
            val stepDurationInSec = 60f / bpm / 4f
            val samplesPerStep = (stepDurationInSec * SAMPLE_RATE).toInt().coerceAtLeast(1000)
            
            for (i in 0 until BUFFER_SIZE) {
                // Calculate current global step in pattern and subdivision
                val currentGlobalSample = sampleCounter + i
                val stepIndex = ((currentGlobalSample / samplesPerStep) % 16).toInt()
                val sampleInStep = (currentGlobalSample % samplesPerStep).toInt()
                
                currentStep = stepIndex
                currentStepFraction = sampleInStep.toFloat() / samplesPerStep.toFloat()

                // Read active notes
                val bassNoteMidi = song.bassPattern[stepIndex]
                val leadNoteMidi = song.leadPattern[stepIndex]

                val bassFreq = midiToFreq(bassNoteMidi)
                val leadFreq = midiToFreq(leadNoteMidi)
                
                currentBassNoteFrequency = bassFreq
                currentLeadNoteFrequency = leadFreq

                // Generate envelope to smooth out sound start and end (prevent clicks)
                // Linear attack, exponential decay
                val timeInStep = sampleInStep.toFloat() / SAMPLE_RATE
                
                // Bass envelope
                val bassEnvelope = if (bassNoteMidi > 0) {
                    val attack = (timeInStep / 0.005f).coerceAtMost(1.0f) // 5ms attack
                    val decay = exp(-4.0f * timeInStep) // exponential decay
                    attack * decay
                } else 0f

                // Lead envelope
                val leadEnvelope = if (leadNoteMidi > 0) {
                    val attack = (timeInStep / 0.008f).coerceAtMost(1.0f) // 8ms attack
                    val decay = exp(-8.0f * timeInStep) // Decay faster for retro feel
                    attack * decay
                } else 0f

                // Accumulate continuous phases
                val deltaBassPhase = (2.0 * PI * bassFreq) / SAMPLE_RATE
                phaseBass += deltaBassPhase
                val bassVal = if (bassFreq > 0) sampleOscillator(bassOscillator, phaseBass) * bassEnvelope else 0.0

                val deltaLeadPhase = (2.0 * PI * leadFreq) / SAMPLE_RATE
                phaseLead += deltaLeadPhase
                val leadVal = if (leadFreq > 0) sampleOscillator(leadOscillator, phaseLead) * leadEnvelope else 0.0

                // Sum tracks, attenuate to prevent distortion, apply volume
                val mixedVal = (bassVal * 0.45 + leadVal * 0.35) * masterVolume
                
                // Keep peak in bounds and scale to 16-bit PCM Signed Short
                val shortVal = (mixedVal.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                writeBuffer[i] = shortVal

                // Push samples into Visualizer Buffer
                synchronized(visualBufferLock) {
                    visualBuffer[visualIndex] = mixedVal.toFloat()
                    visualIndex = (visualIndex + 1) % visualBuffer.size
                }
            }

            sampleCounter += BUFFER_SIZE

            // Write PCM to output audio track
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.write(writeBuffer, 0, BUFFER_SIZE)
                }
            }
        }
    }

    /**
     * Retrieve a continuous replica of the visual buffer for UI drawings.
     */
    fun getVisualSamples(): FloatArray {
        synchronized(visualBufferLock) {
            val result = FloatArray(visualBuffer.size)
            // Rearrange ring buffer so that they represent a continuous time-series stream
            for (i in visualBuffer.indices) {
                result[i] = visualBuffer[(visualIndex + i) % visualBuffer.size]
            }
            return result
        }
    }
}
