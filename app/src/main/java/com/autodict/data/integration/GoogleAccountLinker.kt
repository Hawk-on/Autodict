package com.autodict.data.integration

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.autodict.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Koplar til ein Google-konto via Credential Manager, berre for å identifisere/vise kva konto
 * som er kopla til i innstillingane (M6). Sjølve tilgangen til Tasks-scopet handterast separat
 * av [GoogleTasksClient] – denne klassen identifiserer berre kontoen, ho ber ikkje om nokon
 * API-tilgang og gjer ingen nettverkskall utover Google sin kontoveljar.
 *
 * `activityContext` må vere ein Activity-context (ikkje Application) sidan Credential Manager
 * viser UI (kontoveljar-bottomsheet).
 */
object GoogleAccountLinker {

    /**
     * Opnar kontoveljaren og returnerer konto-IDen (e-post) om brukaren vel ein konto.
     * Feilar penst (via [Result.failure]) om brukaren avbryt, om det ikkje er nett, eller om
     * `google_oauth_client_id` ikkje er sett opp enno.
     */
    suspend fun signIn(activityContext: Context): Result<String> {
        val clientId = activityContext.getString(R.string.google_oauth_client_id)
        if (clientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Google-kopling er ikkje sett opp (google_oauth_client_id manglar i strings.xml).",
                ),
            )
        }

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(idToken.id)
            } else {
                Result.failure(IllegalStateException("Uventa svar frå Credential Manager."))
            }
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(e)
        }
    }
}
