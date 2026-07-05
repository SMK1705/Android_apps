package com.rajasudhan.taskmind.data.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.rajasudhan.taskmind.testutil.FakeTaskMindDao
import com.rajasudhan.taskmind.testutil.aNote
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The daily snapshot worker delegates to [SnapshotManager] and reports success/retry. */
@RunWith(RobolectricTestRunner::class)
class AutoSnapshotWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dao = FakeTaskMindDao()
    private val dir = File(context.noBackupFilesDir, SnapshotManager.SNAPSHOT_DIR)

    @Before
    @After
    fun clean() {
        dir.deleteRecursively()
    }

    private fun worker(): AutoSnapshotWorker {
        val sm = SnapshotManager(context, dao, moshi, mockk(relaxed = true), mockk(relaxed = true))
        return TestListenableWorkerBuilder<AutoSnapshotWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(c: Context, name: String, params: WorkerParameters) =
                    AutoSnapshotWorker(c, params, sm)
            })
            .build()
    }

    @Test
    fun doWork_writesASnapshot_andSucceeds() = runTest {
        dao.insertNote(aNote(title = "Snapshot me"))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(dir.listFiles()!!.any { it.name.endsWith(".json") })
    }

    @Test
    fun doWork_withNoNotes_stillSucceeds_withoutWriting() = runTest {
        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(dir.listFiles()?.none { it.name.endsWith(".json") } ?: true)
    }
}
