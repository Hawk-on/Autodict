package com.autodict.data.assistant

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Krypterer API-nøklar med ein nøkkel som ligg i Android Keystore.
 *
 * Nøkkelen forlèt aldri Keystore – vi sender data gjennom han, vi hentar han ikkje ut. Sjølve
 * chifferteksten blir lagra i DataStore som base64 saman med resten av innstillingane
 * (CLAUDE.md-prinsipp 6: nøklar skal lagrast trygt på eininga, aldri i repoet).
 *
 * Vi brukar Keystore direkte i staden for `androidx.security:security-crypto`: det biblioteket
 * har stått i alpha i årevis og er sidan avvikla. Dette er ikkje heimelaga krypto – det er
 * plattforma sin dokumenterte AES-GCM-veg.
 */
object SecretStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "autodict.assistant.credential"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12

    /** Krypterer, eller null om Keystore ikkje er tilgjengeleg. */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())

        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // IV-en er ikkje hemmeleg, men må følgje med for å kunne dekryptere.
        val combined = cipher.iv + encrypted
        Base64.encodeToString(combined, Base64.NO_WRAP)
    }.getOrNull()

    /**
     * Dekrypterer. Null når verdien ikkje kan lesast – typisk fordi Keystore-nøkkelen er
     * borte etter at brukaren har nullstilt skjermlåsen eller gjenoppretta ein sikkerheits-
     * kopi. Då er nøkkelen tapt og må skrivast inn på nytt, som er rett oppførsel.
     */
    fun decrypt(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= IV_BYTES) return null

        val iv = combined.copyOfRange(0, IV_BYTES)
        val body = combined.copyOfRange(IV_BYTES, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Ingen krav om autentisering: assistenten må kunne kallast frå ein
                // bakgrunnsjobb utan at brukaren står med telefonen i handa.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }
}
