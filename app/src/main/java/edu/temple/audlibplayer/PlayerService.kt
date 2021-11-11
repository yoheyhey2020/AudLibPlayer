package edu.temple.audlibplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.util.Log

import java.io.File
import java.io.FileInputStream
import java.io.IOException

import androidx.core.app.NotificationCompat

private const val NOTIFICATION_CHANNEL_ID = "media_player_control"

class PlayerService : Service() {

    private val TAG = "PDPService";
    private val mediaPlayer = MediaPlayer()
    private val binder = MediaControlBinder()
    private var progressHandler: Handler? = null
    private lateinit var notification: Notification
    private var playingState: PlayingState? = PlayingState.STOPPED
    private var progressThread: Thread? = null
    private var startPosition = 0
    private var currentBookId = -1
    private var currentBookUri: Uri? = null

    inner class MediaControlBinder : Binder() {
        fun play(id: Int) {
            this@PlayerService.play(id)
        }

        fun play(file: File, startPosition: Int) {
            this@PlayerService.play(file, startPosition)
        }

        fun pause() {
            this@PlayerService.pause()
        }

        fun stop() {
            this@PlayerService.stop()
        }

        fun setProgressHandler(handler: Handler?) {
            this@PlayerService.setHandler(handler)
        }

        fun seekTo(position: Int) {
            this@PlayerService.seekTo(position)
        }

        val isPlaying: Boolean
            get() = this@PlayerService.isPlaying()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        mediaPlayer.setOnPreparedListener(object: MediaPlayer.OnPreparedListener {
            override fun onPrepared(mp: MediaPlayer?) {
                Log.i(TAG, "Audiobook prepared")
                playingState = PlayingState.PLAYING
                if (startPosition > 0) {
                    Thread(SeekDelay(500)).start()
                }
                mediaPlayer.start()
                progressThread = Thread(NotifyProgress())
                progressThread!!.start()
                Log.i(TAG, "Audiobook started")
            }
        })
        mediaPlayer.setOnCompletionListener(object: MediaPlayer.OnCompletionListener{
            override fun onCompletion(mp: MediaPlayer?) {
                mp?.reset()
                progressThread = null
                stopSelf()
            }

        })

        notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AudioBook is playing")
            .setContentText("Open app to control playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "Player bound");
        return binder
    }

    fun setHandler(handler: Handler?) {
        Log.i(TAG, "Handler set")
        progressHandler = handler
    }

    fun play(id: Int) {
        try {
            currentBookId = id
            currentBookUri = null
            mediaPlayer.reset()
            val BOOK_DOWNLOAD_URL = "https://kamorris.com/lab/audlib/download.php?id="
            mediaPlayer.setDataSource(BOOK_DOWNLOAD_URL + id)
            playingState = PlayingState.STOPPED
            mediaPlayer.prepareAsync()
            Log.i(TAG, "Audiobook preparing")
            val FOREGROUND_CODE = 1
            startForeground(FOREGROUND_CODE, notification)
            Log.i(TAG, "Foreground notification started")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun play(file: File) {
        try {
            currentBookUri = Uri.fromFile(file)
            currentBookId = -1
            mediaPlayer.reset()
            mediaPlayer.setDataSource(FileInputStream(file).fd)
            playingState = PlayingState.STOPPED
            mediaPlayer.prepareAsync()
            Log.i(TAG, "Audiobook preparing")
            val FOREGROUND_CODE = 1
            startForeground(FOREGROUND_CODE, notification)
            Log.i(TAG, "Foreground notification started")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun play(file: File, position: Int) {
        startPosition = position
        play(file)
    }

    private fun pause() {
        if (playingState === PlayingState.PLAYING) {
            playingState = PlayingState.PAUSED
            mediaPlayer.pause()
            if (progressThread != null) {
                progressThread!!.interrupt()
                progressThread = null
            }
            Log.i(TAG, "Player paused")
        } else if (playingState === PlayingState.PAUSED) {
            playingState = PlayingState.PLAYING
            mediaPlayer.start()
            progressThread = Thread(NotifyProgress())
            progressThread!!.start()
            Log.i(TAG, "Player started")
        }
    }

    private fun stop() {
        mediaPlayer.stop()
        playingState = PlayingState.STOPPED
        stopForeground(true)
        if (progressThread != null) {
            progressThread!!.interrupt()
            progressThread = null
        }
        Log.i(TAG, "Player stopped")
    }

    private fun seekTo(positionInSec: Int) {
        val position = positionInSec * 1000
        if (position <= mediaPlayer.duration) {
            mediaPlayer.seekTo(position)
            Log.i(TAG, "Audiobook position changed")
        }
    }

    private fun isPlaying() = mediaPlayer.isPlaying

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "AUDIOBOOK_CONTROL", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Control audiobook playback"
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    inner class NotifyProgress : Runnable {
        override fun run() {
            var message: Message
            while (playingState === PlayingState.PLAYING) {
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    Log.i(TAG, "Progress update stopped")
                }
                if (progressHandler != null) {
                    if (playingState === PlayingState.PLAYING) {
                        message = Message.obtain()
                        if (currentBookId >= 0) message.obj = BookProgress(
                            currentBookId,
                            mediaPlayer.getCurrentPosition() / 1000
                        ) else message.obj =
                            BookProgress(currentBookUri, mediaPlayer.getCurrentPosition() / 1000)
                        progressHandler?.sendMessage(message)
                    } else if (playingState === PlayingState.PAUSED) progressHandler?.sendEmptyMessage(
                        0
                    )
                }
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        progressHandler = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        mediaPlayer.release();
        super.onDestroy()
    }

    class BookProgress {
        var bookId = -1
            private set
        var bookUri: Uri? = null
            private set
        var progress: Int
            private set

        constructor(bookId: Int, progress: Int) {
            this.bookId = bookId
            this.progress = progress
        }

        constructor(bookUri: Uri?, progress: Int) {
            this.bookUri = bookUri
            this.progress = progress
        }
    }

    inner class SeekDelay(var delay: Int) : Runnable {
        override fun run() {
            try {
                Thread.sleep(delay.toLong())
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            mediaPlayer.seekTo(1000 * startPosition)
            startPosition = 0
        }
    }

    internal enum class PlayingState {
        STOPPED, PLAYING, PAUSED
    }
}