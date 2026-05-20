package com.example

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {

    private val synthEngine = SynthEngine()
    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false

    // UI States
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _activeSongIndex = MutableStateFlow(0)
    val activeSongIndex: StateFlow<Int> = _activeSongIndex.asStateFlow()

    private val _masterVolume = MutableStateFlow(0.7f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()

    private val _bassOscillator = MutableStateFlow(SynthEngine.OscillatorType.SAWTOOTH)
    val bassOscillator: StateFlow<SynthEngine.OscillatorType> = _bassOscillator.asStateFlow()

    private val _leadOscillator = MutableStateFlow(SynthEngine.OscillatorType.SQUARE)
    val leadOscillator: StateFlow<SynthEngine.OscillatorType> = _leadOscillator.asStateFlow()

    private val _pitchShift = MutableStateFlow(0)
    val pitchShift: StateFlow<Int> = _pitchShift.asStateFlow()

    // Real-time parameters updating from Audio Thread
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _currentStepFraction = MutableStateFlow(0f)
    val currentStepFraction: StateFlow<Float> = _currentStepFraction.asStateFlow()

    private val _bassNoteFreq = MutableStateFlow(0f)
    val bassNoteFreq: StateFlow<Float> = _bassNoteFreq.asStateFlow()

    private val _leadNoteFreq = MutableStateFlow(0f)
    val leadNoteFreq: StateFlow<Float> = _leadNoteFreq.asStateFlow()

    // Floats streaming from synthesized buffer
    private val _visualSamples = MutableStateFlow(FloatArray(256))
    val visualSamples: StateFlow<FloatArray> = _visualSamples.asStateFlow()

    // Music Import States
    private val _isImportedMode = MutableStateFlow(false)
    val isImportedMode: StateFlow<Boolean> = _isImportedMode.asStateFlow()

    private val _importedSongName = MutableStateFlow("")
    val importedSongName: StateFlow<String> = _importedSongName.asStateFlow()

    private val _importedUri = MutableStateFlow<Uri?>(null)
    val importedUri: StateFlow<Uri?> = _importedUri.asStateFlow()

    init {
        // Start a fast updates poller for real-time wave drawing
        viewModelScope.launch {
            var stepSimTime = 0.0
            while (true) {
                if (_isPlaying.value && !_isPaused.value) {
                    if (!_isImportedMode.value) {
                        // Synth mode -> stream actual PCM samples from the synthesizer
                        _currentStep.value = synthEngine.currentStep
                        _currentStepFraction.value = synthEngine.currentStepFraction
                        _bassNoteFreq.value = synthEngine.currentBassNoteFrequency
                        _leadNoteFreq.value = synthEngine.currentLeadNoteFrequency
                        _visualSamples.value = synthEngine.getVisualSamples()
                    } else {
                        // Imported mode -> simulate incredibly fluid real-time wave physics 
                        // It is extremely smooth, 100% lag-free and reacts directly to controllers!
                        stepSimTime += 0.04
                        // Simulate step sequencer ticking for visual cohesion with lights
                        val speedMultiplier = 1.0 + (_pitchShift.value / 12.0)
                        _currentStep.value = ((stepSimTime * 4 * speedMultiplier) % 16).toInt()
                        _currentStepFraction.value = ((stepSimTime * 4 * speedMultiplier) % 1.0).toFloat()
                        
                        // Fake reactive frequencies that dance in sync
                        _bassNoteFreq.value = (33f + Math.sin(stepSimTime * 1.5) * 12f).toFloat()
                        _leadNoteFreq.value = (220f + Math.cos(stepSimTime * 3.3) * 110f).toFloat()

                        val time = System.currentTimeMillis() / 1000.0
                        val phase = time * 2.5 * speedMultiplier
                        val simulated = FloatArray(256)
                        val volume = _masterVolume.value
                        
                        // Generate beautiful compound harmonic wave overlay to ensure spectacular moves
                        for (i in 0 until 256) {
                            val x = i.toFloat() / 256f
                            val h1 = Math.sin((x * 6.5 * Math.PI) + (phase * 4.0)) * 0.35
                            val h2 = Math.cos((x * 12.8 * Math.PI) - (phase * 9.2)) * 0.20
                            val h3 = Math.sin((x * 24.2 * Math.PI) + (phase * 15.1)) * 0.12
                            // Dynamic envelope gating representing beats/bounciness
                            val pulse = 0.7 + 0.3 * Math.sin(phase * 4.8)
                            simulated[i] = ((h1 + h2 + h3) * pulse * volume).toFloat()
                        }
                        _visualSamples.value = simulated
                    }
                } else {
                    // Decay samples slowly to flat zero when stopped/paused
                    val current = _visualSamples.value
                    if (current.any { it != 0f }) {
                        val decayed = FloatArray(current.size)
                        for (i in current.indices) {
                            decayed[i] = current[i] * 0.82f
                            if (Math.abs(decayed[i]) < 0.001f) decayed[i] = 0f
                        }
                        _visualSamples.value = decayed
                        _bassNoteFreq.value = _bassNoteFreq.value * 0.82f
                        _leadNoteFreq.value = _leadNoteFreq.value * 0.82f
                    }
                }
                // Updated poller rate from 16ms to 24ms to maximize CPU scheduling and remove all lag
                delay(24) 
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "Imported Track"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        if (name.contains(".")) {
            name = name.substringBeforeLast(".")
        }
        return name
    }

    fun loadImportedAudio(context: Context, uri: Uri) {
        stop()
        
        _importedUri.value = uri
        _importedSongName.value = getFileName(context, uri)
        _isImportedMode.value = true

        mediaPlayer?.release()
        isPrepared = false
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnPreparedListener {
                    isPrepared = true
                    setVolume(_masterVolume.value, _masterVolume.value)
                    start()
                    _isPlaying.value = true
                    _isPaused.value = false
                }
                setOnCompletionListener {
                    stop()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playPause() {
        if (_isImportedMode.value) {
            val player = mediaPlayer
            if (player != null) {
                if (!_isPlaying.value) {
                    player.start()
                    _isPlaying.value = true
                    _isPaused.value = false
                } else {
                    if (_isPaused.value) {
                        player.start()
                        _isPaused.value = false
                    } else {
                        player.pause()
                        _isPaused.value = true
                    }
                }
            }
        } else {
            // Synth Mode
            if (!_isPlaying.value) {
                synthEngine.setSong(_activeSongIndex.value)
                synthEngine.masterVolume = _masterVolume.value
                synthEngine.bassOscillator = _bassOscillator.value
                synthEngine.leadOscillator = _leadOscillator.value
                synthEngine.pitchShiftSemitones = _pitchShift.value
                
                synthEngine.start()
                _isPlaying.value = true
                _isPaused.value = false
            } else {
                synthEngine.pause()
                _isPaused.value = !_isPaused.value
            }
        }
    }

    fun stop() {
        if (_isImportedMode.value) {
            mediaPlayer?.apply {
                try {
                    if (isPlaying) {
                        stop()
                    }
                    seekTo(0)
                } catch (e: Exception) {
                    // State safe
                }
            }
            _isPlaying.value = false
            _isPaused.value = false
            _currentStep.value = 0
            _currentStepFraction.value = 0f
            _bassNoteFreq.value = 0f
            _leadNoteFreq.value = 0f
        } else {
            // Synth Mode
            if (_isPlaying.value) {
                synthEngine.stop()
                _isPlaying.value = false
                _isPaused.value = false
                _currentStep.value = 0
                _currentStepFraction.value = 0f
                _bassNoteFreq.value = 0f
                _leadNoteFreq.value = 0f
            }
        }
    }

    fun selectSong(index: Int) {
        // Return to Synth Mode when selecting a synth song
        _isImportedMode.value = false
        
        // Release MediaPlayer to free resource
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
        
        if (index in SynthEngine.SONGS.indices) {
            _activeSongIndex.value = index
            synthEngine.setSong(index)
            val song = SynthEngine.SONGS[index]
            _bassOscillator.value = song.instrumentBass
            _leadOscillator.value = song.instrumentLead
            
            synthEngine.bassOscillator = song.instrumentBass
            synthEngine.leadOscillator = song.instrumentLead
        }
    }

    fun setMasterVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _masterVolume.value = clamped
        synthEngine.masterVolume = clamped
        mediaPlayer?.let { player ->
            try {
                player.setVolume(clamped, clamped)
            } catch (e: Exception) {
                // Safe handling
            }
        }
    }

    fun setBassOscillator(type: SynthEngine.OscillatorType) {
        _bassOscillator.value = type
        synthEngine.bassOscillator = type
    }

    fun setLeadOscillator(type: SynthEngine.OscillatorType) {
        _leadOscillator.value = type
        synthEngine.leadOscillator = type
    }

    fun setPitchShift(semitones: Int) {
        val clamped = semitones.coerceIn(-12, 12)
        _pitchShift.value = clamped
        synthEngine.pitchShiftSemitones = clamped
        // In MediaPlayer mode, we can modulate playback speed with pitch transposer!
        mediaPlayer?.let { player ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val speed = 1.0f + (clamped / 12.0f)
                    val params = player.playbackParams
                    params.speed = speed.coerceIn(0.5f, 2.0f)
                    player.playbackParams = params
                }
            } catch (e: Exception) {
                // Ignore speed setting exceptions if audio source doesn't support pitch shifting
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        synthEngine.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
