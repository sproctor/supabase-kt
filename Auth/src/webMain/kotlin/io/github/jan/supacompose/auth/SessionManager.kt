package io.github.jan.supacompose.auth

import io.github.jan.supacompose.SupabaseClient
import io.github.jan.supacompose.auth.user.UserSession
import io.github.jan.supacompose.supabaseJson
import kotlinx.browser.window
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import org.w3c.dom.set

actual class SessionManager {

    actual suspend fun saveSession(supabaseClient: SupabaseClient, session: UserSession) {
        val json = supabaseJson.encodeToString(session)
        window.localStorage["session"] = json
    }

    actual suspend fun loadSession(supabaseClient: SupabaseClient): UserSession? {
        return window.localStorage["session"]?.let { supabaseJson.decodeFromString(it) }
    }

    actual suspend fun deleteSession(supabaseClient: SupabaseClient) {
        window.localStorage.removeItem("session")
    }


}