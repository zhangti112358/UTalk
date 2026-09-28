package com.zhangti.utalk.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** API key 只存放于应用私有偏好设置，值经 Android Keystore AES-GCM 加密。 */
class EncryptedSettingsStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    fun savedKey(name: String): String? {
        require(SettingsCatalog.apiKeys.any { it.key == name })
        val encoded = prefs.getString("key.$name", null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 12) { "保存的密钥数据损坏" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }

    fun saveKeys(values: Map<String, String>) {
        require(values.keys.all { name -> SettingsCatalog.apiKeys.any { it.key == name } })
        val editor = prefs.edit()
        values.forEach { (name, value) ->
            if (value.isBlank()) editor.remove("key.$name")
            else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val encrypted = cipher.iv + cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
                editor.putString("key.$name", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
        }
        check(editor.commit()) { "保存 API key 失败" }
    }

    fun didiEnvironment(): DiDiEnvironment =
        runCatching { DiDiEnvironment.valueOf(prefs.getString(DIDI_ENV, DiDiEnvironment.SANDBOX.name)!!) }
            .getOrDefault(DiDiEnvironment.SANDBOX)

    fun setDiDiEnvironment(value: DiDiEnvironment) {
        check(prefs.edit().putString(DIDI_ENV, value.name).commit()) { "保存滴滴环境失败" }
    }

    companion object {
        const val PREFS_NAME = "utalk_private_settings"
        private const val KEY_ALIAS = "utalk_api_keys_v1"
        private const val DIDI_ENV = "didi_environment"
    }
}
