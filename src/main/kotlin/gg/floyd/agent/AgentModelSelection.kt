package gg.floyd.agent

import com.google.gson.JsonArray

/** The advertised default, not a name heuristic, determines account model selection. */
internal object AgentModelSelection {
    fun preferred(models: JsonArray?): String? {
        val visible = models?.map { it.asJsonObject }?.filter { it["hidden"]?.asBoolean != true }.orEmpty()
        return visible.firstOrNull { it["isDefault"]?.asBoolean == true }?.get("model")?.asString
    }
}
