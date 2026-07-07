package com.rajasudhan.taskmind

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Static guard against the #227 regression, where the whole background-work system silently died.
 *
 * TaskMindApp is a [androidx.work.Configuration.Provider] that installs a `HiltWorkerFactory`, but the
 * manifest had stopped removing WorkManager's default `androidx.startup` initializer. That initializer then
 * won the startup race and booted WorkManager with the DEFAULT factory, so every `@HiltWorker` (which has
 * only an `@AssistedInject` constructor) failed to instantiate — `NoSuchMethodException` on the
 * `(Context, WorkerParameters)` constructor — and the periodic scan + all periodic jobs stopped running.
 * Nothing caught it: the app works interactively (SCAN NOW, the notification listener) without WorkManager.
 *
 * These are plain source-reading assertions (no Hilt graph, no Robolectric) so they run in CI via
 * `testDebugUnitTest` and cover the two ways this can regress:
 *  1. the manifest losing the `WorkManagerInitializer` removal, and
 *  2. a WorkManager worker being added without `@HiltWorker` (so it, too, would hit the default factory).
 */
class WorkManagerHiltWorkerGuardTest {

    private val moduleDir: File by lazy { locateModuleDir() }

    @Test
    fun manifest_removesTheDefaultWorkManagerInitializer_soTheHiltWorkerFactoryIsUsed() {
        val manifest = File(moduleDir, "src/main/AndroidManifest.xml").readText()

        // Match each self-closing <meta-data .../> element, then find the WorkManager one.
        val metaData = Regex("<meta-data\\b(?:(?!</?meta-data).)*?/>", RegexOption.DOT_MATCHES_ALL)
        val wmInitializer = metaData.findAll(manifest)
            .map { it.value }
            .firstOrNull { it.contains("androidx.work.WorkManagerInitializer") }

        assertNotNull(
            "AndroidManifest must declare the androidx.work.WorkManagerInitializer meta-data (so it can be " +
                "removed). Without the removal, WorkManager auto-initialises with the DEFAULT factory and every " +
                "@HiltWorker fails to instantiate — the background scan and all periodic jobs die (regression #227).",
            wmInitializer,
        )
        assertTrue(
            "The WorkManagerInitializer meta-data must carry tools:node=\"remove\", so WorkManager initialises " +
                "on demand from TaskMindApp's Configuration.Provider (with the HiltWorkerFactory). See #227.",
            wmInitializer!!.contains("tools:node=\"remove\""),
        )
    }

    @Test
    fun taskMindApp_isAConfigurationProvider_thatInstallsTheHiltWorkerFactory() {
        // This is the precondition that makes the manifest removal above mandatory: as long as the app supplies
        // its own WorkManager Configuration with a HiltWorkerFactory, the default initializer must be removed.
        val app = File(moduleDir, "src/main/java/com/rajasudhan/taskmind/TaskMindApp.kt").readText()

        assertTrue(
            "TaskMindApp must implement Configuration.Provider (it's why the manifest guard is required).",
            app.contains("Configuration.Provider"),
        )
        assertTrue(
            "TaskMindApp must install the HiltWorkerFactory via setWorkerFactory() in its WorkManager Configuration.",
            app.contains("HiltWorkerFactory") && app.contains("setWorkerFactory"),
        )
    }

    @Test
    fun everyWorkManagerWorker_isAnnotatedHiltWorker() {
        val srcRoot = File(moduleDir, "src/main/java")
        val extendsWorker = Regex(":\\s*(?:Coroutine|Listenable)?Worker\\s*\\(")
        val workerFiles = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it to it.readText() }
            .filter { (_, text) -> extendsWorker.containsMatchIn(text) }
            .toList()

        // Anchor the scan so a broken path (finding nothing) can't make the @HiltWorker check vacuously pass.
        assertTrue(
            "Scan found no WorkManager workers — the DataCollectionWorker (periodic scan) must be among them; " +
                "check the source path.",
            workerFiles.any { it.first.name == "DataCollectionWorker.kt" },
        )

        val missing = workerFiles.filterNot { (_, text) -> text.contains("@HiltWorker") }.map { it.first.name }
        assertTrue(
            "Every WorkManager worker must be @HiltWorker so it's built by the HiltWorkerFactory. A worker " +
                "without it hits WorkManager's default factory, which can't inject @AssistedInject dependencies " +
                "→ NoSuchMethodException at runtime (the #227 failure mode). Missing @HiltWorker: $missing",
            missing.isEmpty(),
        )
    }

    /** Walk up from the test working dir to the taskmind module (works whether cwd is the module or repo root). */
    private fun locateModuleDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val d = dir ?: return@repeat
            if (File(d, "src/main/AndroidManifest.xml").exists()) return d
            if (File(d, "apps/taskmind/src/main/AndroidManifest.xml").exists()) return File(d, "apps/taskmind")
            dir = d.parentFile
        }
        fail("Could not locate the taskmind module from working dir ${System.getProperty("user.dir")}")
        error("unreachable")
    }
}
