package com.autodict.data.integration

import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Scopet Autodict ber om – berre skrivetilgang til gjeremål, ingenting anna på kontoen. */
private const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
private const val TASKS_ENDPOINT = "https://tasks.googleapis.com/tasks/v1/lists/@default/tasks"

/** Resultat av eit forsøk på å skaffe eit access-token for Google Tasks-scopet. */
sealed interface GoogleTasksAuthResult {
    data class Authorized(val accessToken: String) : GoogleTasksAuthResult

    /** Brukaren må stadfeste samtykke i eit system-ark – start [intentSenderRequest] frå UI-et. */
    data class ConsentRequired(val intentSenderRequest: IntentSenderRequest) : GoogleTasksAuthResult
    data class Failure(val message: String) : GoogleTasksAuthResult
}

/**
 * Grensesnitt for å opprette gjeremål i Google Tasks. Opt-in (M6, sjå
 * `AppSettings.googleTasksEnabled`) – kallast berre etter at brukaren har stadfesta eit konkret
 * handlingspunkt (kjerneprinsipp 5 i CLAUDE.md), og feilar penst utan nett/samtykke
 * (kjerneprinsipp 4).
 */
interface GoogleTasksClient {
    /** Prøver å skaffe tilgang stille (Play Services huskar tidlegare samtykke). */
    suspend fun authorize(): GoogleTasksAuthResult

    /** Fullfør autorisering etter at brukaren har svart på samtykke-arket frå [ConsentRequired]. */
    suspend fun authorizeFromConsent(data: Intent): GoogleTasksAuthResult

    /** Opprett eit gjeremål i standard-lista til den autoriserte kontoen. */
    suspend fun createTask(accessToken: String, title: String, notes: String?, dueIso: String?): Result<Unit>
}

/** Implementasjon via Play Services Authorization API (kontosamtykke) + REST (OkHttp). */
class GooglePlayServicesTasksClient(private val context: Context) : GoogleTasksClient {

    private val http = OkHttpClient()

    override suspend fun authorize(): GoogleTasksAuthResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(TASKS_SCOPE)))
            .build()
        return try {
            toAuthResult(Identity.getAuthorizationClient(context).authorize(request).await())
        } catch (e: ApiException) {
            GoogleTasksAuthResult.Failure(e.message ?: "Google-autorisering feila.")
        } catch (e: Exception) {
            GoogleTasksAuthResult.Failure(e.message ?: "Ukjend feil ved Google-autorisering. Er du på nett?")
        }
    }

    override suspend fun authorizeFromConsent(data: Intent): GoogleTasksAuthResult {
        return try {
            toAuthResult(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
        } catch (e: ApiException) {
            GoogleTasksAuthResult.Failure(e.message ?: "Samtykket vart ikkje fullført.")
        }
    }

    private fun toAuthResult(result: AuthorizationResult): GoogleTasksAuthResult {
        val pending = result.pendingIntent
        val token = result.accessToken
        return when {
            result.hasResolution() && pending != null ->
                GoogleTasksAuthResult.ConsentRequired(IntentSenderRequest.Builder(pending.intentSender).build())
            token != null -> GoogleTasksAuthResult.Authorized(token)
            else -> GoogleTasksAuthResult.Failure("Fekk ikkje tilgangstoken frå Google.")
        }
    }

    override suspend fun createTask(
        accessToken: String,
        title: String,
        notes: String?,
        dueIso: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = GoogleTasksRequestBuilder.taskJson(title, notes, dueIso)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(TASKS_ENDPOINT)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body)
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Google Tasks svarte med status ${response.code}.")
                }
            }
        }
    }
}

/**
 * Byggjer JSON-body for Google Tasks REST API (`tasks.insert`). Reint og testbart – ingen
 * Android- eller nettverksavhengnader.
 */
object GoogleTasksRequestBuilder {
    fun taskJson(title: String, notes: String? = null, dueIso: String? = null): String {
        val fields = mutableListOf("\"title\":\"${escape(title)}\"")
        notes?.takeIf { it.isNotBlank() }?.let { fields.add("\"notes\":\"${escape(it)}\"") }
        dueIso?.takeIf { it.isNotBlank() }?.let { fields.add("\"due\":\"${escape(it)}\"") }
        return "{${fields.joinToString(",")}}"
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
}
