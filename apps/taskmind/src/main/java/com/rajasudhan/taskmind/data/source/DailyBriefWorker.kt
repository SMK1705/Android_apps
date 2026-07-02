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
import com.rajasudhan.taskmind.ui.common.isOverdue
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/**
 * Posts the morning Daily Brief: a once-a-day glance at what's overdue, due today, and freshly caught
 * for review — so TaskMind shows up on a schedule instead of waiting to be opened. Reads only local
 * data; composes deterministically ([DailyBriefComposer]); stays silent on an empty day.
 */
@HiltWorker
class DailyBriefWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: TaskMindDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val notes = dao.getActiveNotes().first()
            val today = LocalDate.now().toString()

            val overdue = notes.count { isOverdue(it.dueDate, it.dueTime) }
            val dueToday = notes.count { !isOverdue(it.dueDate, it.dueTime) && it.dueDate == today }
            val now = System.currentTimeMillis()
            val pending = dao.getPendingSuggestions().first()
                .count { it.snoozedUntil == null || it.snoozedUntil!! <= now }

            // Focus = the day's actionable items, most-important first (high priority, then soonest).
            val focus = notes
                .filter { isOverdue(it.dueDate, it.dueTime) || it.dueDate == today }
                .sortedWith(compareBy({ priorityRank(it.priority) }, { dueSortKey(it.dueDate, it.dueTime) }))
                .map { it.title }

            val brief = DailyBriefComposer.compose(overdue, dueToday, pending, focus)
                ?: return Result.success() // empty day — don't ping

            postBrief(brief)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun postBrief(brief: DailyBrief) {
        val tapIntent = PendingIntent.getActivity(
            applicationContext, DAILY_BRIEF_REQUEST_CODE,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, TaskMindForegroundService.DAILY_BRIEF_CHANNEL_ID)
            .setContentTitle(brief.title)
            .setContentText(brief.body.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(brief.body))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(DAILY_BRIEF_NOTIFICATION_ID, notification)
    }

    private fun priorityRank(priority: String): Int = when (priority) {
        "high" -> 0
        "low" -> 2
        else -> 1
    }

    private fun dueSortKey(dueDate: String?, dueTime: String?): Long {
        val date = dueDate ?: return Long.MAX_VALUE
        return try {
            LocalDate.parse(date).toEpochDay() * 1440 + (dueTime?.let { LocalTime.parse(it).toSecondOfDay() / 60 } ?: 0)
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    companion object {
        const val DAILY_BRIEF_NOTIFICATION_ID = 7
        private const val DAILY_BRIEF_REQUEST_CODE = 9_100_000
    }
}
