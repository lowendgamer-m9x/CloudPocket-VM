package com.example

import com.example.model.CloudProvider
import com.example.model.RemoteInputEvent
import com.example.model.StreamQuality
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testMouseMoveSerialization() {
        val move = RemoteInputEvent.MouseMove(0.4523f, 0.7812f)
        val json = move.toJson()
        assertTrue(json.contains("\"type\":\"mouse_move\""))
        assertTrue(json.contains("\"x\":0.4523") || json.contains("0.4523"))
        assertTrue(json.contains("\"y\":0.7812") || json.contains("0.7812"))
    }

    @Test
    fun testMouseClickSerialization() {
        val click = RemoteInputEvent.MouseClick("left", true)
        val json = click.toJson()
        assertEquals("""{"type":"mouse_click","button":"left","down":true}""", json)
    }

    @Test
    fun testMouseScrollSerialization() {
        val scroll = RemoteInputEvent.MouseScroll(0f, -2.5f)
        val json = scroll.toJson()
        assertTrue(json.contains("\"type\":\"mouse_scroll\""))
        assertTrue(json.contains("\"dy\":-2.50") || json.contains("-2.5"))
    }

    @Test
    fun testQualityPresetDefaults() {
        val low = StreamQuality.LOW
        assertEquals(854, low.targetWidth)
        assertEquals(480, low.targetHeight)
        assertEquals(15, low.targetFps)
        assertEquals(45, low.jpegQuality)
    }

    @Test
    fun testFreeTierProviders() {
        assertTrue(CloudProvider.ORACLE_FREE_TIER.isFreeTierEligible)
        assertTrue(CloudProvider.GCP_E2_MICRO.isFreeTierEligible)
        assertTrue(CloudProvider.AWS_EC2_FREE.isFreeTierEligible)
    }
}
