package com.rajasudhan.taskmind.data.source.transcription

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records a short voice note from the microphone to an AAC/MP4 file in the app cache. The file is
 * later decoded + transcribed on-device by [VoskTranscriber]. One recording at a time; the caller
 * is responsible for deleting the returned file once it has been consumed.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** Begins recording. Returns true if the microphone started successfully. */
    fun start(): Boolean = try {
        val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(128_000)
        rec.setAudioSamplingRate(44_100)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        outputFile = file
        true
    } catch (e: Exception) {
        e.printStackTrace()
        release()
        false
    }

    /** Stops recording and returns the captured file, or null if nothing usable was recorded. */
    fun stop(): File? = try {
        recorder?.stop()
        outputFile.also { release() }
    } catch (e: Exception) {
        // stop() throws if stopped almost immediately (no frames captured); discard the file.
        e.printStackTrace()
        outputFile?.delete()
        release()
        null
    }

    /** Aborts the current recording and deletes any partial file. */
    fun cancel() {
        runCatching { recorder?.stop() }
        outputFile?.delete()
        release()
    }

    private fun release() {
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
    }
}
