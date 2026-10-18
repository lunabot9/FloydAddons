package gg.floyd.agent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameplayVisionIntentTest {
    @Test fun `explicit visual requests attach a game frame`() {
        listOf("Look at my gameplay and help me", "Analyze my inventory", "What am I looking at?",
            "What is on my screen?", "How does my build look?", "Take a screenshot").forEach {
            assertTrue(GameplayVisionIntent.requestsImage(it), it)
        }
    }
    @Test fun `ordinary conversation and refused capture stay text only`() {
        listOf("Hello Randy", "What is Minecraft?", "Tell me how to build a house",
            "Don't look at my screen", "Do not capture a screenshot", "Never analyze my inventory").forEach {
            assertFalse(GameplayVisionIntent.requestsImage(it), it)
        }
    }
}
