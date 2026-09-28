package com.zhangti.utalk.settings

import android.content.Context
import com.zhangti.utalk.LocalConfig

/** 全局配置唯一入口：手机保存值优先，缺省时使用构建期 secrets.properties。 */
object SettingsRepository {
    @Volatile private var store: EncryptedSettingsStore? = null

    fun initialize(context: Context) {
        if (store == null) synchronized(this) {
            if (store == null) store = EncryptedSettingsStore(context.applicationContext)
        }
    }

    fun savedKey(name: String): String? = store?.savedKey(name)
    fun resolvedKey(name: String): String = savedKey(name)?.takeIf(String::isNotBlank) ?: LocalConfig[name]
    fun saveKeys(values: Map<String, String>) = requireNotNull(store).saveKeys(values)
    fun commonInfo(): String = store?.commonInfo().orEmpty()
    fun saveCommonInfo(value: String) = requireNotNull(store).saveCommonInfo(value)
    fun didiEnvironment(): DiDiEnvironment = store?.didiEnvironment() ?: DiDiEnvironment.SANDBOX
    fun setDiDiEnvironment(value: DiDiEnvironment) = requireNotNull(store).setDiDiEnvironment(value)
}
