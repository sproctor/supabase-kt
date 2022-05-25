package io.github.jan.supacompose.auth

import io.github.jan.supacompose.SupabaseClient
import io.github.jan.supacompose.auth.user.UserSession
import io.github.jan.supacompose.supabaseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

actual class SessionManager {

    actual suspend fun saveSession(supabaseClient: SupabaseClient, session: UserSession) {
        val auth = supabaseClient.auth as AuthImpl
        val sessionFile = auth.config.sessionFile ?: return
        withContext(Dispatchers.IO) {
            if(!sessionFile.exists()) sessionFile.createNewFile()
            sessionFile.writeText(supabaseJson.encodeToString(session))
        }
    }

    actual suspend fun loadSession(supabaseClient: SupabaseClient): UserSession? {
        val auth = supabaseClient.auth as AuthImpl
        val sessionFile = auth.config.sessionFile ?: return null
        if(!sessionFile.exists()) return null
        return supabaseJson.decodeFromString<UserSession>(sessionFile.readText())
    }

    actual suspend fun deleteSession(supabaseClient: SupabaseClient) {
        val auth = supabaseClient.auth as AuthImpl
        val sessionFile = auth.config.sessionFile ?: return
        if(!sessionFile.exists()) return
        sessionFile.delete()
    }

}