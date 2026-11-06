package ru.tambovkaef.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Creates a per-installation AES key in Android Keystore and encrypts optional API credentials. */
object CredentialVault {
    private const val STORE = "yandex_api_credentials"
    private const val ALIAS = "tambov_kaef_yandex_api_aes"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** Generate the device-local key on first launch; no secret is embedded in the APK. */
    fun initialize() {
        getOrCreateKey()
    }

    fun save(context: Context, clid: String, apiKey: String) {
        require(clid.isNotBlank() && apiKey.isNotBlank()) { "Заполни оба поля" }
        val payload = JSONObject().put("clid", clid).put("apiKey", apiKey).toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val ivText = prefs.getString("iv", null) ?: return null
        val dataText = prefs.getString("data", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
        val plain = cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP))
        val json = JSONObject(String(plain, Charsets.UTF_8))
        return json.getString("clid") to json.getString("apiKey")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}
