package gg.floyd.agent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentSpeechPipelineTest {
    @Test fun nextSentencePreparesWhilePreviousSentenceIsPlaying() {
        val preparation = Executors.newSingleThreadExecutor()
        val playback = Executors.newSingleThreadExecutor()
        val playing = CountDownLatch(1)
        val releasePlayback = CountDownLatch(1)
        val secondPrepared = CountDownLatch(1)
        val finished = CountDownLatch(2)
        try {
            val pipeline = AgentSpeechPipeline(preparation, playback)
            pipeline.enqueue({ true }, { 1 }, { playing.countDown(); releasePlayback.await(3, TimeUnit.SECONDS) }, {}, { throw it }, { finished.countDown() })
            assertTrue(playing.await(3, TimeUnit.SECONDS))
            pipeline.enqueue({ true }, { secondPrepared.countDown(); 2 }, {}, {}, { throw it }, { finished.countDown() })
            assertTrue(secondPrepared.await(3, TimeUnit.SECONDS), "Next synthesis must not wait for playback")
            assertEquals(2L, finished.count)
            releasePlayback.countDown()
            assertTrue(finished.await(3, TimeUnit.SECONDS))
        } finally {
            releasePlayback.countDown(); preparation.shutdownNow(); playback.shutdownNow()
        }
    }

    private class ManualExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runNext() = tasks.removeFirst().run()
    }

    @Test fun cancelledPreparedAudioIsReleasedWithoutPlaying() {
        val preparation = ManualExecutor(); val playback = ManualExecutor()
        val pipeline = AgentSpeechPipeline(preparation, playback)
        var active = true; var played = 0; var released = 0; var finished = 0
        pipeline.enqueue({ active }, { "wav" }, { played++ }, { released++ }, { throw it }, { finished++ })
        preparation.runNext(); active = false; playback.runNext()
        assertEquals(0, played); assertEquals(1, released); assertEquals(0, finished)
    }

    @Test fun staleQueuedSynthesisDoesNotStart() {
        val preparation = ManualExecutor(); val playback = ManualExecutor()
        var prepared = 0
        AgentSpeechPipeline(preparation, playback).enqueue({ false }, { prepared++; "wav" }, {}, {}, { throw it }, {})
        preparation.runNext()
        assertEquals(0, prepared); assertTrue(playback.tasks.isEmpty())
    }

    @Test fun synthesisFailureStillFinishesItsPlaceInThePlaybackQueue() {
        val preparation = ManualExecutor(); val playback = ManualExecutor()
        var failed = 0; var finished = 0
        AgentSpeechPipeline(preparation, playback).enqueue<String>({ true }, { error("failed") }, {}, {}, { failed++ }, { finished++ })
        preparation.runNext(); playback.runNext()
        assertEquals(1, failed); assertEquals(1, finished)
    }
}
