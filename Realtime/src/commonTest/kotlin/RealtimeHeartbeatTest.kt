import app.cash.turbine.test
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.RealtimeMessage
import io.github.jan.supabase.realtime.RealtimeProtocolVersion
import io.github.jan.supabase.realtime.broadcast.encodeV2Text
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.websocket.RealtimeWebsocket
import io.github.jan.supabase.realtime.websocket.RealtimeWebsocketFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(SupabaseInternal::class)
class RealtimeHeartbeatTest {

    private val heartbeatInterval = 15.seconds

    private class HeartbeatSocket(private val answerHeartbeats: Boolean) : RealtimeWebsocket {
        private val incoming = Channel<Frame>(Channel.UNLIMITED)
        private var ref = 0
        var heartbeats = 0
            private set
        var disconnected = false
            private set

        override val hasIncomingMessages: Boolean get() = !incoming.isClosedForReceive
        override fun makeRef(): String = (++ref).toString()
        override suspend fun receive(): Frame = incoming.receive()

        override suspend fun send(message: RealtimeMessage, vsn: RealtimeProtocolVersion) {
            if (message.event != "heartbeat") return
            heartbeats++
            if (answerHeartbeats) {
                val reply = message.copy(
                    event = "phx_reply",
                    payload = buildJsonObject {
                        put("status", "ok")
                        putJsonObject("response") { }
                    }
                )
                incoming.send(Frame.Text(reply.encodeV2Text()))
            }
        }

        override suspend fun send(data: ByteArray) = Unit
        override suspend fun blockUntilDisconnect() = Unit

        override fun disconnect() {
            disconnected = true
            incoming.close()
        }

        /** The connection is lost without a close from the client, e.g. while the device sleeps. */
        fun lose() {
            incoming.close(IllegalStateException("Connection lost"))
        }
    }

    @Test
    fun testUnansweredHeartbeatOfALostSocketDoesNotTearDownTheNextSocket() {
        runTest {
            val sockets = mutableListOf<HeartbeatSocket>()
            val client = createSupabaseClient("https://example.supabase.co", "anon-key") {
                httpEngine = MockEngine { respond("") }
                install(Realtime) {
                    coroutineScope = backgroundScope
                    heartbeatInterval = 15.seconds
                    reconnectDelay = 7.seconds
                    disconnectOnNoSubscriptions = false
                    websocketFactory = object : RealtimeWebsocketFactory {
                        // Only the first socket leaves its heartbeat unanswered.
                        override suspend fun create(url: String): RealtimeWebsocket =
                            HeartbeatSocket(answerHeartbeats = sockets.isNotEmpty()).also(sockets::add)
                    }
                }
            }
            client.realtime.connect()

            advanceTimeBy(15.seconds + 100.milliseconds)
            assertEquals(1, sockets.single().heartbeats)
            sockets.single().lose()
            advanceTimeBy(7.seconds + 100.milliseconds)
            assertEquals(2, sockets.size, "The client reconnects after the reconnect delay")

            advanceTimeBy(15.seconds + 100.milliseconds)
            val replacement = sockets.last()
            assertEquals(1, replacement.heartbeats, "The new socket sends its own first heartbeat")
            assertFalse(replacement.disconnected, "The new socket is not closed for the old socket's missing reply")
            assertEquals(2, sockets.size)
            assertEquals(Realtime.Status.CONNECTED, client.realtime.status.value)
        }
    }

    @Test
    fun testChannelMessageWithRefZeroIsNotTakenForHeartbeatReply() {
        val channelId = "channelId"
        runTest {
            createTestClient(
                wsHandler = { i, o ->
                    handleSubscribe(i, o, channelId)
                    // The server closes a join with its join_ref as the ref, and a socket's first join_ref is 0
                    o.send(RealtimeMessage("realtime:$channelId", RealtimeChannel.CHANNEL_EVENT_CLOSE, buildJsonObject { }, "0", "0").toFrame())
                    // Removing the subscribed channel sends a leave
                    assertEquals(RealtimeChannel.CHANNEL_EVENT_LEAVE, i.receive().toMessage().event)
                },
                supabaseHandler = {
                    val channel = it.channel(channelId)
                    channel.subscribe(blockUntilSubscribed = true)
                    channel.status.test(FLOW_TIMEOUT) {
                        do {
                            val status = awaitItem()
                        } while(status != RealtimeChannel.Status.UNSUBSCRIBED)
                    }
                    assertNull(it.realtime.subscriptions["realtime:$channelId"])
                },
            )
        }
    }

    @Test
    fun testUnansweredFirstHeartbeatTimesOut() {
        val sockets = ScriptedWebsocketFactory()
        runTest {
            createTestClient(
                wsHandler = { _, _ -> },
                supabaseHandler = {
                    it.realtime.connect()
                    advanceTimeBy(heartbeatInterval + 1.seconds)
                    assertEquals("0", sockets.created[0].nextHeartbeat().ref)
                    advanceTimeBy(heartbeatInterval)
                    assertEquals(Realtime.Status.DISCONNECTED, it.realtime.status.value)
                },
                realtimeConfig = {
                    coroutineScope = backgroundScope
                    heartbeatInterval = this@RealtimeHeartbeatTest.heartbeatInterval
                    websocketFactory = sockets
                },
            )
        }
    }

    @Test
    fun testHeartbeatPendingAtDisconnectDoesNotTimeOutNextSocket() {
        val sockets = ScriptedWebsocketFactory()
        runTest {
            createTestClient(
                wsHandler = { _, _ -> },
                supabaseHandler = {
                    it.realtime.connect()
                    val first = sockets.created[0]
                    advanceTimeBy(heartbeatInterval + 1.seconds)
                    first.reply(first.nextHeartbeat())
                    advanceTimeBy(heartbeatInterval)
                    // Left unanswered
                    assertEquals("1", first.nextHeartbeat().ref)
                    it.realtime.disconnect()
                    it.realtime.connect()
                    advanceTimeBy(heartbeatInterval + 1.seconds)
                    assertEquals(Realtime.Status.CONNECTED, it.realtime.status.value)
                    assertEquals("heartbeat", sockets.created[1].nextHeartbeat().event)
                },
                realtimeConfig = {
                    coroutineScope = backgroundScope
                    heartbeatInterval = this@RealtimeHeartbeatTest.heartbeatInterval
                    websocketFactory = sockets
                },
            )
        }
    }

    private suspend fun ScriptedWebsocket.nextHeartbeat(): RealtimeMessage {
        val message = sent.awaitItem()
        assertEquals("heartbeat", message.event)
        return message
    }

    private suspend fun ScriptedWebsocket.reply(heartbeat: RealtimeMessage) = push(
        RealtimeMessage("phoenix", RealtimeChannel.CHANNEL_EVENT_REPLY, buildJsonObject {
            put("status", "ok")
            put("response", buildJsonObject { })
        }, heartbeat.ref)
    )

}
