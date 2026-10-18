package gg.floyd.agent

import com.google.gson.*
import gg.floyd.FloydAddonsMod
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Provider-owned login. The mod never reads or stores access/refresh tokens. */
class AgentConnector(private val directory: File, private val event: (String, JsonObject) -> Unit) : AutoCloseable {
    private val gson = Gson()
    private val ids = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
    private var process: Process? = null
    private var writer: java.io.BufferedWriter? = null
    private var thread: String? = null
    private var replyModel: String? = null
    val selectedModel: String get() = replyModel ?: "provider-default"
    @Volatile private var currentTurn: String? = null
    @Volatile private var turnIsActive: () -> Boolean = { true }
    private val interruptedTurns = ConcurrentHashMap.newKeySet<String>()

    fun connect() {
        if (process?.isAlive == true) return
        directory.mkdirs()
        // Auto preserves existing keyring logins and lets Codex fall back to its
        // private account/auth.json when Windows rejects an oversized token.
        val command = command("codex") + listOf("app-server", "-c", "cli_auth_credentials_store=\"auto\"",
            "-c", "features.shell_tool=false", "-c", "web_search=\"live\"", "-c", "project_doc_max_bytes=0")
        val builder = ProcessBuilder(command).directory(directory)
        builder.environment()["CODEX_HOME"] = File(directory, "account").apply { mkdirs() }.absolutePath
        removeApiCredentials(builder)
        val child = builder.start()
        process = child
        writer = child.outputStream.bufferedWriter(Charsets.UTF_8)
        Thread({ child.errorStream.bufferedReader().useLines { lines -> lines.forEach { /* Do not log provider output or credentials. */ } } }, "Floyd-Agent-Errors").apply { isDaemon = true; start() }
        Thread({
            try {
                child.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { line ->
                    val message = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull() ?: return@forEach
                    val method = message.get("method")?.asString
                    if (method != null && message.has("id")) {
                        // Provider-owned web search is allowed; local tool/approval requests remain disabled.
                        write(mapOf("id" to message["id"], "error" to mapOf("code" to -32601, "message" to "Companion tools are disabled")))
                    } else if (message.has("id")) {
                        val future = pending.remove(message["id"].asInt)
                        if (message.has("error")) future?.completeExceptionally(IllegalStateException("Provider request failed. Check your account and connection."))
                        else future?.complete(message.getAsJsonObject("result") ?: JsonObject())
                    } else if (method != null) {
                        val params = message.getAsJsonObject("params") ?: JsonObject()
                        val turnId = params["turnId"]?.asString ?: params.getAsJsonObject("turn")?.get("id")?.asString
                        if (method == "turn/started" && turnId != null) {
                            currentTurn = turnId
                            if (!turnIsActive()) interruptTurn(turnId)
                        }
                        if (turnId == null || turnId !in interruptedTurns) event(method, params)
                    }
                } }
            } catch (_: Exception) { /* Closed below, including normal EOF. */ }
            finally {
                if (process === child) event("connection/closed", JsonObject())
                pending.values.forEach { it.completeExceptionally(IllegalStateException("Connection closed")) }; pending.clear()
            }
        }, "Floyd-Agent-Reader").apply { isDaemon = true; start() }
        request("initialize", mapOf("clientInfo" to mapOf("name" to "floyd_companion", "title" to "Floyd Companion", "version" to FloydAddonsMod.MOD_VERSION)))
        write(mapOf("method" to "initialized", "params" to emptyMap<String, Any>()))
        val models = runCatching { request("model/list", emptyMap<String, Any>())["data"]?.asJsonArray }.getOrNull()
        replyModel = AgentModelSelection.preferred(models)
    }

    fun account(): Boolean {
        val account = request("account/read", mapOf("refreshToken" to false))["account"]
        return account?.isJsonObject == true && account.asJsonObject["type"]?.asString == "chatgpt"
    }

    fun login(): String = request("account/login/start", mapOf("type" to "chatgpt"))["authUrl"].asString
    fun logout() { request("account/logout", emptyMap<String, Any>()); thread = null }

    fun send(text: String, instructions: String, image: File? = null, active: () -> Boolean = { true }) {
        if (thread == null) {
            val result = request("thread/start", mapOf("cwd" to directory.absolutePath, "sandbox" to "read-only",
                "approvalPolicy" to "never", "baseInstructions" to instructions, "ephemeral" to true, "model" to replyModel,
                "config" to mapOf("features.shell_tool" to false, "web_search" to "live", "project_doc_max_bytes" to 0)))
            thread = result.getAsJsonObject("thread")["id"].asString
        }
        if (!active()) return
        currentTurn = null; turnIsActive = active
        val input = mutableListOf<Map<String, String>>(mapOf("type" to "text", "text" to text))
        image?.let { input += mapOf("type" to "localImage", "path" to it.absolutePath) }
        val result = request("turn/start", mapOf("threadId" to thread, "effort" to "low", "input" to input))
        currentTurn = result.getAsJsonObject("turn")?.get("id")?.asString
        if (!active()) interrupt()
    }

    fun interrupt() { currentTurn?.let(::interruptTurn) }
    private fun interruptTurn(turnId: String) {
        val threadId = thread ?: return
        if (!interruptedTurns.add(turnId)) return
        // Cancellation is a turn operation; it never closes the signed-in provider session.
        write(mapOf("id" to ids.incrementAndGet(), "method" to "turn/interrupt", "params" to mapOf("threadId" to threadId, "turnId" to turnId)))
    }

    fun newConversation() { thread = null }
    private fun request(method: String, params: Any): JsonObject {
        val id = ids.incrementAndGet()
        val future = CompletableFuture<JsonObject>()
        pending[id] = future
        try { write(mapOf("id" to id, "method" to method, "params" to params)); return future.get(45, TimeUnit.SECONDS) }
        finally { pending.remove(id) }
    }
    @Synchronized private fun write(value: Any) { val out = checkNotNull(writer); out.write(gson.toJson(value)); out.newLine(); out.flush() }
    override fun close() { writer?.runCatching { close() }; process?.destroy(); process = null; thread = null }

    companion object {
        fun removeApiCredentials(builder: ProcessBuilder) {
            listOf("OPENAI_API_KEY", "ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN", "CLAUDE_CODE_OAUTH_TOKEN", "CODEX_ACCESS_TOKEN").forEach { builder.environment().remove(it) }
        }
        /** Run executables/Node entrypoints directly, never build shell commands from chat text. */
        fun command(provider: String): List<String> {
            val win = System.getProperty("os.name").lowercase().contains("win")
            val home = File(System.getProperty("user.home"))
            if (provider == "codex") {
                System.getenv("CODEX_CLI_PATH")?.let(::File)?.takeIf { it.isFile && it.canExecute() }
                    ?.let { return listOf(it.absolutePath) }
                if (win) {
                    // Launchers inherit a different PATH from Codex. Prefer the installed
                    // desktop CLI to a stale npm fallback with obsolete account models.
                    val appData = System.getenv("LOCALAPPDATA") ?: File(home, "AppData/Local").absolutePath
                    File(appData, "OpenAI/Codex/bin").listFiles()?.asSequence()
                        ?.filter { it.isDirectory }?.map { File(it, "codex.exe") }
                        ?.filter { it.isFile && it.canExecute() }?.maxByOrNull { it.lastModified() }
                        ?.let { return listOf(it.absolutePath) }
                }
            }
            val candidates = (System.getenv("PATH") ?: "").split(File.pathSeparator).map { File(it, provider + if (win) ".exe" else "") } +
                listOf(File(home, ".local/bin/$provider${if (win) ".exe" else ""}"))
            candidates.firstOrNull { it.isFile && it.canExecute() }?.let { return listOf(it.absolutePath) }
            val npm = File(System.getenv("APPDATA") ?: home.absolutePath, "npm/node_modules")
            if (provider == "codex") {
                val native = File(npm, "@openai/codex/node_modules/@openai/codex-win32-x64/vendor/x86_64-pc-windows-msvc/codex/codex.exe")
                if (win && native.isFile) return listOf(native.absolutePath)
            }
            val claudeNative = File(npm, "@anthropic-ai/claude-code/bin/claude.exe")
            if (provider == "claude" && win && claudeNative.isFile) return listOf(claudeNative.absolutePath)
            val js = if (provider == "codex") File(npm, "@openai/codex/bin/codex.js") else File(npm, "@anthropic-ai/claude-code/cli.js")
            if (js.isFile) return listOf(if (win) "node.exe" else "node", js.absolutePath)
            throw IllegalStateException("Install ${if (provider == "codex") "Codex CLI" else "Claude Code"} to use account sign-in.")
        }
    }
}
