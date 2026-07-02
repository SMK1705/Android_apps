package com.rajasudhan.taskmind.data.source

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** ModelDownloader must stream the file AND record the fetch in the egress ledger (metadata only). */
class ModelDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tempDir = Files.createTempDirectory("model-dl").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun download_writesTheFile_andLogsEgress() = runBlocking {
        server.enqueue(MockResponse().setBody("MODEL-BYTES"))
        val egress = mockk<EgressLogger>(relaxed = true)
        val downloader = ModelDownloader(egress)
        val dest = File(tempDir, "eng.traineddata")

        val err = downloader.download(server.url("/models/eng.traineddata").toString(), dest) {}

        assertNull(err)
        assertTrue(dest.exists())
        assertEquals("MODEL-BYTES", dest.readText())

        val host = slot<String>()
        val purpose = slot<String>()
        verify { egress.record(capture(host), capture(purpose)) }
        assertEquals(server.hostName, host.captured)
        assertTrue(purpose.captured.contains("eng.traineddata"))
    }

    @Test
    fun failedDownload_stillLogsTheAttempt() = runBlocking {
        // Honesty: we reached out to the host even if the fetch failed, so it must still be logged.
        server.enqueue(MockResponse().setResponseCode(500))
        val egress = mockk<EgressLogger>(relaxed = true)
        val downloader = ModelDownloader(egress)

        val err = downloader.download(server.url("/models/vosk.zip").toString(), File(tempDir, "vosk.zip")) {}

        assertTrue(err != null)
        verify { egress.record(server.hostName, match { it.contains("vosk.zip") }) }
    }
}
