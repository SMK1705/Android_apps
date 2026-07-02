package com.rajasudhan.taskmind.data.source

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rajasudhan.taskmind.MainActivity
import com.rajasudhan.taskmind.R
import com.rajasudhan.taskmind.data.local.TaskMindDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Posts the once-a-week Weekly Wins recap: a streak-free "done list" of what got completed in the
 * last 7 days, doubling as a trust report for the capture engine (how many wins were auto-caught
 * from a source vs. typed in). Reads only local data; composes deterministically
 * ([WeeklyWinsComposer]); stays silent on a week with nothing done.
 */
@HiltWorker
class WeeklyWinsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: TaskMindDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val since = System.currentTimeMillis() - WINDOW_MILLIS
            val wins = dao.getCompletedNotes().first()
                .filter { (it.completedDate ?: 0L) >= since }
                .map { WeeklyWin(title = it.title, source = it.source) }

            val recap = WeeklyWinsComposer.compose(wins)
                ?: return Result.success() // quiet week — don't ping

            postRecap(recap)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun postRecap(recap: WeeklyWins) {
        val tapIntent = PendingIntent.getActivity(
            applicationContext, WEEKLY_WINS_REQUEST_CODE,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, TaskMindForegroundService.WEEKLY_WINS_CHANNEL_ID)
            .setContentTitle(recap.title)
            .setContentText(recap.body.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(recap.body))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(WEEKLY_WINS_NOTIFICATION_ID, notification)
    }

    companion object {
        const val WEEKLY_WINS_NOTIFICATION_ID = 8
        private const val WEEKLY_WINS_REQUEST_CODE = 9_200_000
        private const val WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
