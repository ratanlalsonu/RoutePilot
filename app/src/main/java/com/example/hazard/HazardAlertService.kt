package com.example.hazard

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Handles driver-safe audio, voice guidance (TTS), and haptic alerts during navigation
 * and hazard detection, controlled by user settings.
 */
class HazardAlertService(
    private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    isTtsReady = true
                }
            }
        }
    }

    fun triggerHazardAlertFeedback(
        hazardTitle: String,
        statusText: String,
        soundEnabled: Boolean,
        voiceEnabled: Boolean
    ) {
        if (soundEnabled) {
            vibratePattern()
            playWarningTone()
        }
        if (voiceEnabled) {
            speak("Hazard detected ahead. $hazardTitle. $statusText. Recalculating a safer route.")
        }
    }

    fun announceRouteUpdated(voiceEnabled: Boolean) {
        if (voiceEnabled) {
            speak("Route updated. A safer route has been selected.")
        }
    }

    fun announceTurnInstruction(instruction: String, voiceEnabled: Boolean) {
        if (voiceEnabled) {
            speak(instruction)
        }
    }

    fun announceArrival(destinationName: String, voiceEnabled: Boolean) {
        if (voiceEnabled) {
            speak("Journey completed. You have safely reached $destinationName.")
        }
    }

    private fun speak(text: String) {
        if (!isTtsReady) return
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "routepilot_tts_${System.currentTimeMillis()}")
        }
    }

    private fun vibratePattern() {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 300), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(400)
                }
            }
        }
    }

    private fun playWarningTone() {
        runCatching {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
    }
}
