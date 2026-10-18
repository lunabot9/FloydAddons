package gg.floyd.agent

/** Only explicit requests to inspect the game attach a frame; ordinary chat stays text-only. */
internal object GameplayVisionIntent {
    private val refusal = Regex("\\b(don't|do not|never|stop)\\b.{0,35}\\b(look|see|view|watch|analy[sz]e|captur\\w*|screenshot)")
    private val request = Regex("\\b(?:look at|check|inspect|view|watch|analy[sz]e) (?:my |the |this |our )?(?:gameplay|game|screen|inventory|build|hud|surroundings|screenshot)\\b|\\bwhat (?:am i looking at|can you see|do you see|is on my screen)\\b|\\b(?:take|send|attach|capture) (?:a |my |the )?(?:screenshot|gameplay snapshot)\\b|\\bhow does my build look\\b")
    fun requestsImage(text: String): Boolean {
        val normalized = text.lowercase().replace('’', '\'').replace(Regex("\\s+"), " ")
        return !refusal.containsMatchIn(normalized) && request.containsMatchIn(normalized)
    }
}
