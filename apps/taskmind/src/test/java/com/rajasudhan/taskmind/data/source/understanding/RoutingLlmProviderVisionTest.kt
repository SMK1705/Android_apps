package com.rajasudhan.taskmind.data.source.understanding

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure vision-routing decision behind the multimodal seam (#211, Gemma 3n Phase 0). */
class RoutingLlmProviderVisionTest {

    // --- Phase 0 reality: no engine reports supportsVision, so every route is NONE (ships dark). ---

    @Test
    fun none_whenNoEngineCanSee_onDeviceSelected() {
        assertEquals(VisionRoute.NONE, visionRoute(useOnDevice = true, onDeviceVision = false, cloudVision = false, hasCloudKey = true))
        assertEquals(VisionRoute.NONE, visionRoute(useOnDevice = true, onDeviceVision = false, cloudVision = false, hasCloudKey = false))
    }

    @Test
    fun none_whenNoEngineCanSee_cloudSelected() {
        assertEquals(VisionRoute.NONE, visionRoute(useOnDevice = false, onDeviceVision = false, cloudVision = false, hasCloudKey = true))
    }

    // --- Forward-looking: once an engine can see, the policy mirrors the text routing. ---

    @Test
    fun onDevice_isPreferred_whenSelectedAndItCanSee() {
        assertEquals(VisionRoute.ON_DEVICE, visionRoute(useOnDevice = true, onDeviceVision = true, cloudVision = true, hasCloudKey = true))
        assertEquals(VisionRoute.ON_DEVICE, visionRoute(useOnDevice = true, onDeviceVision = true, cloudVision = false, hasCloudKey = false))
    }

    @Test
    fun fallsBackToCloud_whenOnDeviceCantSeeButCloudCanAndKeyIsSet() {
        assertEquals(VisionRoute.CLOUD, visionRoute(useOnDevice = true, onDeviceVision = false, cloudVision = true, hasCloudKey = true))
    }

    @Test
    fun noCloudFallback_withoutAKey() {
        // Mirrors the text path: on-device selected but unavailable only reaches the cloud when a key exists.
        assertEquals(VisionRoute.NONE, visionRoute(useOnDevice = true, onDeviceVision = false, cloudVision = true, hasCloudKey = false))
    }

    @Test
    fun cloudSelected_usesCloud_whenItCanSee_regardlessOfKeyGate() {
        // Choosing cloud as the backend already implies a key; the key gate only guards the on-device fallback.
        assertEquals(VisionRoute.CLOUD, visionRoute(useOnDevice = false, onDeviceVision = false, cloudVision = true, hasCloudKey = true))
        assertEquals(VisionRoute.CLOUD, visionRoute(useOnDevice = false, onDeviceVision = false, cloudVision = true, hasCloudKey = false))
    }
}
