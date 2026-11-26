package com.mulocoolbot.gymtracker.platform

import com.mulocoolbot.gymtracker.timer.buildRestCompletionWavBytes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.darwin.NSObject

internal class IosTimerAudioCoordinator(
    private val scope: CoroutineScope
) {
    private var activePlayer: AVAudioPlayer? = null
    private var activeDelegate: AudioPlayerFinishDelegate? = null
    private var cleanupJob: Job? = null
    private var retryDeactivateJob: Job? = null
    private var isSessionActive: Boolean = false
    private val alarmWavBytes: ByteArray by lazy { buildRestCompletionWavBytes() }

    @OptIn(ExperimentalForeignApi::class)
    fun playCompletionAlert() {
        stopAndRestoreAudio()

        val session = AVAudioSession.sharedInstance()
        // Playback with DuckOthers lowers Spotify/Apple Music volume during the timer alert.
        session.setCategory(
            AVAudioSessionCategoryPlayback,
            withOptions = AVAudioSessionCategoryOptionDuckOthers,
            error = null
        )
        isSessionActive = session.setActive(true, error = null)

        val wavData = alarmWavBytes.toNSData()
        val player = runCatching { AVAudioPlayer(data = wavData, error = null) }.getOrNull()
        if (player == null) {
            stopAndRestoreAudio()
            return
        }

        val delegate = AudioPlayerFinishDelegate {
            scope.launch {
                stopAndRestoreAudio()
            }
        }
        activeDelegate = delegate
        activePlayer = player
        player.delegate = delegate
        player.prepareToPlay()
        player.play()

        cleanupJob = scope.launch {
            delay(1_350L)
            stopAndRestoreAudio()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun stopAndRestoreAudio() {
        cleanupJob?.cancel()
        cleanupJob = null
        retryDeactivateJob?.cancel()
        retryDeactivateJob = null

        activePlayer?.let { player ->
            if (player.isPlaying()) {
                player.stop()
            }
            player.delegate = null
        }
        activePlayer = null
        activeDelegate = null

        if (isSessionActive) {
            isSessionActive = false
            val deactivated = AVAudioSession.sharedInstance().setActive(
                false,
                withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                error = null
            )
            if (!deactivated) {
                retryDeactivateJob = scope.launch {
                    delay(120L)
                    AVAudioSession.sharedInstance().setActive(
                        false,
                        withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                        error = null
                    )
                }
            }
        }
    }

    private class AudioPlayerFinishDelegate(
        private val onFinished: () -> Unit
    ) : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            onFinished()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(bytes = pinned.addressOf(0), length = size.toULong())
}
