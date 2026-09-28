package com.zhangti.utalk.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangti.utalk.KeyNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedSettingsStoreTest {
    @Test
    fun encryptedKeyRoundTripsWithoutPlaintextStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefName = "utalk_settings_instrumented_test"
        context.deleteSharedPreferences(prefName)
        try {
            val store = EncryptedSettingsStore(context, prefName)
            store.saveKeys(mapOf(KeyNames.DEEPSEEK to "test-secret-value"))
            assertEquals("test-secret-value", store.savedKey(KeyNames.DEEPSEEK))
            val raw = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                .getString("key.${KeyNames.DEEPSEEK}", null).orEmpty()
            assertFalse(raw.contains("test-secret-value"))
            store.saveKeys(mapOf(KeyNames.DEEPSEEK to ""))
            assertNull(store.savedKey(KeyNames.DEEPSEEK))
            assertEquals(DiDiEnvironment.SANDBOX, store.didiEnvironment())
        } finally { context.deleteSharedPreferences(prefName) }
    }
}
