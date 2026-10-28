package io.github.jan.supabase.realtime

import kotlinx.serialization.json.JsonObject

/**
 * An error the server reported for a channel, like realtime-js's `CHANNEL_ERROR`. The channel rejoins after each one
 * until [Realtime.Config.maxAttempts] is reached.
 *
 * @param type Whether the server rejected the channel's join or reported an error for the joined channel
 * @param reason The server's reason, e.g. `RealtimeDisabledForConfiguration: ...` for a rejected join. A channel error usually has none.
 * @param payload The payload of the server's message
 */
data class RealtimeChannelError(
    val type: Type,
    val reason: String?,
    val payload: JsonObject
) {

    /**
     * The kind of [RealtimeChannelError]
     */
    enum class Type {
        /**
         * The server rejected the channel's join, e.g. a postgres changes subscription it couldn't set up when the channel
         * waits for it (see [PostgresChangesOptions.wait])
         */
        JOIN_REJECTED,

        /**
         * The server reported an error (`phx_error`) for the channel, e.g. when its process on the server crashed
         */
        CHANNEL_ERROR
    }

}
