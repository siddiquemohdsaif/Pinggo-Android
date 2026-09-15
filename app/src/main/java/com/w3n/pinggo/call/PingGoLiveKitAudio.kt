package com.w3n.pinggo.call

import android.media.MediaRecorder
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKitOverrides

/** Configures LiveKit's WebRTC capture for communication-grade APM processing. */
object PingGoLiveKitAudio {
    @JvmStatic
    fun createOverrides(): LiveKitOverrides = LiveKitOverrides(
        audioOptions = AudioOptions(
            javaAudioDeviceModuleCustomizer = { builder ->
                builder
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setUseHardwareNoiseSuppressor(true)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseLowLatency(true)
            },
        ),
    )
}
