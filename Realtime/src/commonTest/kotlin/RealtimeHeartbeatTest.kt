import app.cash.turbine.test
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.RealtimeMessage
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RealtimeHeartbeatTest {

    private val heartbeatInterval = 15.seconds
    private val reconnectDelay = 7.seconds

    @Test
    fun testUnansweredHeartbeatOfALostSocketDoesNotTearDownTheNextSocket() {
        val sockets = ScriptedWebsocketFactory()
        runTest {
            createTestClient(
                wsHandler = { _, _ -> },
                supabaseHandler = {
                    it.realtime.connect()
                    advanceTimeBy(heartbeatInterval + 100.milliseconds)
                    // Left unanswered
                    sockets.created[0].nextHeartbeat()
                    sockets.created[0].lose()
                    advanceTimeBy(reconnectDelay + 100.milliseconds)
                    assertEquals(2, sockets.created.size, "The client reconnects after the reconnect delay")

                    advanceTimeBy(heartbeatInterval + 100.milliseconds)
                    val replacement = sockets.created[1]
                    replacement.reply(replacement.nextHeartbeat())
                    assertTrue(replacement.hasIncomingMessages, "The new socket is not closed for the old socket's missing reply")
                    // The reply was taken for the heartbeat's, so the next tick sends another one on the same socket
                    advanceTimeBy(heartbeatInterval)
                    replacement.nextHeartbeat()
                    assertEquals(2, sockets.created.size)
                    assertEquals(Realtime.Status.CONNECTED, it.realtime.status.value)
                },
                realtimeConfig = {
                    coroutineScope = backgroundScope
                    heartbeatInterval = this@RealtimeHeartbeatTest.heartbeatInterval
                    reconnectDelay = this@RealtimeHeartbeatTest.reconnectDelay
                    websocketFactory = sockets
                },
            )
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
