package floydaddons.not.dogshit.client.features.impl.misc

import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FloydLocalControlTest {
    @Test
    fun `client future join unwraps illegal argument failures`() {
        val future = CompletableFuture<Unit>()
        future.completeExceptionally(IllegalArgumentException("not_connected"))

        assertFailsWith<IllegalArgumentException> {
            FloydLocalControlFutures.joinClientFuture(future)
        }
    }

    @Test
    fun `client future join unwraps checked exceptions like Floyd bridge`() {
        val future = CompletableFuture<Unit>()
        future.completeExceptionally(IOException("client_io"))

        assertFailsWith<IOException> {
            FloydLocalControlFutures.joinClientFuture(future)
        }
    }

    @Test
    fun `persisted control bridge ports normalize like Floyd settings loader`() {
        assertEquals(38765, FloydLocalControlSettings.normalizePort(0))
        assertEquals(38765, FloydLocalControlSettings.normalizePort(-1))
        assertEquals(1024, FloydLocalControlSettings.normalizePort(1024))
        assertEquals(65536, FloydLocalControlSettings.normalizePort(65536))
        assertEquals(38765, FloydLocalControlSettings.newSettingsPort())
    }

    @Test
    fun `required string errors distinguish missing and blank like Floyd bridge`() {
        assertEquals(
            "missing_message",
            assertFailsWith<IllegalArgumentException> {
                FloydLocalControlJson.requiredString(JsonObject(), "message")
            }.message
        )

        val blank = JsonObject().apply { addProperty("message", "   ") }
        assertEquals(
            "blank_message",
            assertFailsWith<IllegalArgumentException> {
                FloydLocalControlJson.requiredString(blank, "message")
            }.message
        )

        val valid = JsonObject().apply { addProperty("message", "hello") }
        assertEquals("hello", FloydLocalControlJson.requiredString(valid, "message"))
    }

}
