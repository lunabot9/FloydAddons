package gg.floyd.agent

import java.util.concurrent.Executor

/** Serial synthesis overlaps serial playback; stale prepared audio is always released. */
internal class AgentSpeechPipeline(private val preparation: Executor, private val playback: Executor) {
    fun <T : Any> enqueue(
        current: () -> Boolean,
        prepare: () -> T,
        play: (T) -> Unit,
        release: (T) -> Unit,
        failed: (Throwable) -> Unit,
        finished: () -> Unit,
    ) {
        preparation.execute {
            if (!current()) return@execute
            val prepared = runCatching(prepare)
            playback.execute {
                try {
                    if (current()) {
                        prepared.fold(
                            onSuccess = { audio -> runCatching { play(audio) }.onFailure(failed) },
                            onFailure = failed,
                        )
                    }
                } finally {
                    prepared.getOrNull()?.let(release)
                    if (current()) finished()
                }
            }
        }
    }
}
