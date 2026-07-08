package com.rajasudhan.taskmind.data.source.understanding

import com.rajasudhan.taskmind.data.source.SettingsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The routing + fallback logic for the Magic Breakdown list call (generateList). */
class RoutingLlmProviderTest {

    private val onDevice = mockk<OnDeviceLlmProvider>(relaxed = true)
    private val cloud = mockk<CloudLlmProvider>(relaxed = true)
    private val settings = mockk<SettingsManager>(relaxed = true)
    private val router = RoutingLlmProvider(onDevice, cloud, settings)

    @Test
    fun generateList_onDeviceMode_usesOnDevice() = runTest {
        every { settings.useOnDeviceLlm } returns true
        coEvery { onDevice.generateList(any(), any()) } returns """["a","b"]"""

        assertEquals("""["a","b"]""", router.generateList("sys", "user"))
        coVerify(exactly = 0) { cloud.generateList(any(), any()) }
    }

    @Test
    fun generateList_cloudMode_usesCloud() = runTest {
        every { settings.useOnDeviceLlm } returns false
        coEvery { cloud.generateList(any(), any()) } returns """["x","y"]"""

        assertEquals("""["x","y"]""", router.generateList("sys", "user"))
        coVerify(exactly = 0) { onDevice.generateList(any(), any()) }
    }

    @Test
    fun generateList_onDeviceUnavailable_fallsBackToCloud_whenKeyPresent() = runTest {
        every { settings.useOnDeviceLlm } returns true
        every { settings.llmApiKey } returns "key-123"
        coEvery { onDevice.generateList(any(), any()) } throws IllegalStateException("no model")
        coEvery { cloud.generateList(any(), any()) } returns """["fallback"]"""

        assertEquals("""["fallback"]""", router.generateList("sys", "user"))
    }

    @Test
    fun generateList_onDeviceUnavailable_noKey_returnsEmptyArray() = runTest {
        every { settings.useOnDeviceLlm } returns true
        every { settings.llmApiKey } returns ""
        coEvery { onDevice.generateList(any(), any()) } throws IllegalStateException("no model")

        assertEquals("[]", router.generateList("sys", "user"))
        coVerify(exactly = 0) { cloud.generateList(any(), any()) }
    }

    // isOnDeviceEffective() — the honest-label predicate (#197): true only when data stays on-device.

    @Test
    fun isOnDeviceEffective_falseWhenCloudSelected() {
        every { settings.useOnDeviceLlm } returns false
        assertEquals(false, router.isOnDeviceEffective())
    }

    @Test
    fun isOnDeviceEffective_trueWhenOnDeviceSelectedAndModelPresent() {
        every { settings.useOnDeviceLlm } returns true
        every { onDevice.isModelPresent() } returns true
        every { settings.llmApiKey } returns "key-123"
        assertEquals(true, router.isOnDeviceEffective())
    }

    @Test
    fun isOnDeviceEffective_falseWhenModelMissingButCloudKeySet() {
        // on-device selected, model not downloaded, key present -> runtime falls back to cloud.
        every { settings.useOnDeviceLlm } returns true
        every { onDevice.isModelPresent() } returns false
        every { settings.llmApiKey } returns "key-123"
        assertEquals(false, router.isOnDeviceEffective())
    }

    @Test
    fun isOnDeviceEffective_trueWhenModelMissingAndNoKey_dataStaysLocal() {
        // Nothing can run, but nothing leaves the phone either, so "on-device" is still honest.
        every { settings.useOnDeviceLlm } returns true
        every { onDevice.isModelPresent() } returns false
        every { settings.llmApiKey } returns ""
        assertEquals(true, router.isOnDeviceEffective())
    }

    // mediaEgressesToCloud() — the honest vision-route signal (#251): true when captured media leaves the phone.

    @Test
    fun mediaEgressesToCloud_trueWhenOnDeviceSelected_butCloudHandlesVisionWithKey() {
        // The exact honest-label bug config: on-device text is effective, but on-device has no vision,
        // so a configured key routes screenshots/audio to Gemini even while isOnDeviceEffective() is true.
        every { settings.useOnDeviceLlm } returns true
        every { onDevice.isModelPresent() } returns true
        every { onDevice.supportsVision() } returns false
        every { cloud.supportsVision() } returns true
        every { settings.llmApiKey } returns "key-123"
        assertEquals(true, router.isOnDeviceEffective())   // text stays local
        assertEquals(true, router.mediaEgressesToCloud())  // but media does not
    }

    @Test
    fun mediaEgressesToCloud_falseWhenNoCloudKey() {
        every { settings.useOnDeviceLlm } returns true
        every { onDevice.supportsVision() } returns false
        every { cloud.supportsVision() } returns true
        every { settings.llmApiKey } returns ""
        assertEquals(false, router.mediaEgressesToCloud())
    }

    @Test
    fun mediaEgressesToCloud_falseWhenOnDeviceCanSee() {
        every { settings.useOnDeviceLlm } returns true
        every { onDevice.supportsVision() } returns true
        every { cloud.supportsVision() } returns true
        every { settings.llmApiKey } returns "key-123"
        assertEquals(false, router.mediaEgressesToCloud())
    }
}
