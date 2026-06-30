package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.test.core.app.ApplicationProvider
import com.rajasudhan.taskmind.data.local.TaskMindDao
import com.rajasudhan.taskmind.data.local.TaskMindDatabase
import com.rajasudhan.taskmind.testutil.aNote
import com.rajasudhan.taskmind.testutil.aSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The periodic background scan + retention cleanup worker. */
@RunWith(RobolectricTestRunner::class)
class DataCollectionWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TaskMindDatabase
    private lateinit var dao: TaskMindDao
    private val scanner = mockk<RecentDataScanner>(relaxed = true)
    private val settings = mockk<SettingsManager>(relaxed = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, TaskMindDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.taskMindDao()
    }

    @After
    fun tearDown() = db.close()

    private fun worker() = TestListenableWorkerBuilder<DataCollectionWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(c: Context, name: String, params: WorkerParameters) =
                DataCollectionWorker(c, params, scanner, dao, settings)
        })
        .build()

    @Test
    fun doWork_scansIncrementally_purgesActioned_andAppliesRetention() = runTest {
        every { settings.retentionDays } returns 30
        val day = 24L * 60 * 60 * 1000
        dao.insertSuggestion(aSuggestion(extractedTitle = "actioned", status = "approved"))
        dao.insertNote(aNote(title = "old", createdDate = System.currentTimeMillis() - 100 * day))
        dao.insertNote(aNote(title = "recent", createdDate = System.currentTimeMillis()))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { scanner.scanIncremental() }
        assertEquals(listOf("recent"), dao.getNotesList().map { it.title }) // old note purged by retention
    }

    @Test
    fun doWork_retriesWhenScanFails() = runTest {
        coEvery { scanner.scanIncremental() } throws RuntimeException("boom")

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
