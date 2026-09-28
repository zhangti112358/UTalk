package com.zhangti.utalk.settings

import com.zhangti.utalk.KeyNames
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCatalogTest {
    @Test
    fun `field order begins with model then speech then map`() {
        assertEquals(listOf(KeyNames.DEEPSEEK, KeyNames.DOUBAO, KeyNames.AMAP),
            SettingsCatalog.apiKeys.take(3).map { it.key })
    }

    @Test
    fun `multi line properties import recognizes only known nonempty keys`() {
        val imported = SettingsCatalog.parseImport("""
            ```properties
            # copied from secrets.properties
            deepseek = model-value
            doubao=voice=value
            amap = map-value
            unknown=ignore
            didi=
            ```
        """.trimIndent())
        assertEquals(mapOf("deepseek" to "model-value", "doubao" to "voice=value", "amap" to "map-value"), imported)
    }

}
