package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {

    private val synthEngine = SynthEngine()

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

    init {
        // Start a fast updates poller for real-time wave drawing
        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value && !_isPaused.value) {
                    _currentStep.value = synthEngine.currentStep
                    _currentStepFraction.value = synthEngine.currentStepFraction
                    _bassNoteFreq.value = synthEngine.currentBassNoteFrequency
                    _leadNoteFreq.value = synthEngine.currentLeadNoteFrequency
                    _visualSamples.value = synthEngine.getVisualSamples()
                } else {
                    // Decay samples slowly to flat zero when stopped/paused
                    val current = _visualSamples.value
                    if (current.any { it != 0f }) {
                        val decayed = FloatArray(current.size)
                        for (i in current.indices) {
                            decayed[i] = current[i] * 0.85f
                            if (Math.abs(decayed[i]) < 0.001f) decayed[i] = 0f
                        }
                        _visualSamples.value = decayed
                        _bassNoteFreq.value = _bassNoteFreq.value * 0.85f
                        _leadNoteFreq.value = _leadNoteFreq.value * 0.85f
                    }
                }
                delay(16) // ~60fps wave physics loop
            }
        }
    }

    fun playPause() {
        if (!_isPlaying.value) {
            // First play
            synthEngine.setSong(_activeSongIndex.value)
            synthEngine.masterVolume = _masterVolume.value
            synthEngine.bassOscillator = _bassOscillator.value
            synthEngine.leadOscillator = _leadOscillator.value
            synthEngine.pitchShiftSemitones = _pitchShift.value
            
            synthEngine.start()
            _isPlaying.value = true
            _isPaused.value = false
        } else {
            // Toggle pause
            synthEngine.pause()
            _isPaused.value = !_isPaused.value
        }
    }

    fun stop() {
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

    fun selectSong(index: Int) {
        if (index in SynthEngine.SONGS.indices) {
            _activeSongIndex.value = index
            synthEngine.setSong(index)
            // Sync current oscillators to the new song defaults
            val song = SynthEngine.SONGS[index]
            _bassOscillator.value = song.instrumentBass
            _leadOscillator.value = song.instrumentLead
            
            // If playing, update active engine parameters in real-time
            synthEngine.bassOscillator = song.instrumentBass
            synthEngine.leadOscillator = song.instrumentLead
        }
    }

    fun setMasterVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _masterVolume.value = clamped
        synthEngine.masterVolume = clamped
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
    }

    override fun onCleared() {
        super.onCleared()
        synthEngine.stop()
    }
}
