package com.rajasudhan.taskmind.data.source

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The launch-re-arm must NOT destroy a pending (possibly Doze-deferred) occurrence — issue #182.
 * On launch (replace = false) the schedulers must KEEP an existing job; only an explicit settings
 * change (replace = true) may CANCEL_AND_REENQUEUE to apply a new time.
 */
@RunWith(RobolectricTestRunner::class)
class BriefRecapSchedulerPolicyTest {

    private val wm = mockk<WorkManager>(relaxed = true)

    // ---- Daily Brief ----

    @Test
    fun dailyBrief_launchReArm_keepsExistingSchedule() {
        DailyBriefScheduler(wm).reschedule(enabled = true, hour = 8, minute = 0, replace = false)
        verify {
            wm.enqueueUniquePeriodicWork(
                DailyBriefScheduler.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun dailyBrief_settingsChange_reenqueuesToApplyNewTime() {
        DailyBriefScheduler(wm).reschedule(enabled = true, hour = 8, minute = 0, replace = true)
        verify {
            wm.enqueueUniquePeriodicWork(
                DailyBriefScheduler.WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun dailyBrief_disabled_cancelsAndDoesNotEnqueue() {
        DailyBriefScheduler(wm).reschedule(enabled = false, hour = 8, minute = 0, replace = false)
        verify { wm.cancelUniqueWork(DailyBriefScheduler.WORK_NAME) }
        verify(exactly = 0) {
            wm.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>())
        }
    }

    // ---- Weekly Wins ----

    @Test
    fun weeklyWins_launchReArm_keepsExistingSchedule() {
        WeeklyWinsScheduler(wm).reschedule(enabled = true, hour = 18, replace = false)
        verify {
            wm.enqueueUniquePeriodicWork(
                WeeklyWinsScheduler.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun weeklyWins_settingsChange_reenqueuesToApplyNewTime() {
        WeeklyWinsScheduler(wm).reschedule(enabled = true, hour = 18, replace = true)
        verify {
            wm.enqueueUniquePeriodicWork(
                WeeklyWinsScheduler.WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun weeklyWins_disabled_cancelsAndDoesNotEnqueue() {
        WeeklyWinsScheduler(wm).reschedule(enabled = false, hour = 18, replace = false)
        verify { wm.cancelUniqueWork(WeeklyWinsScheduler.WORK_NAME) }
        verify(exactly = 0) {
            wm.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>())
        }
    }

    // ---- Auto-snapshot safety net (#161) ----

    @Test
    fun autoSnapshot_schedulesAUniqueDailyJob_withKeepSoALaunchDoesNotRollItForward() {
        // No user toggle — it's an always-on net — so it just (re)arms with KEEP on every launch, which
        // must not destroy a pending (Doze-deferred) snapshot occurrence.
        AutoSnapshotScheduler(wm).schedule()
        verify {
            wm.enqueueUniquePeriodicWork(
                AutoSnapshotScheduler.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, any<PeriodicWorkRequest>()
            )
        }
    }
}
