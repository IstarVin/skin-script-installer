package com.istarvin.skinscriptinstaller.data.downlink

import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownlinkScriptParserTest {

    private val parser = DownlinkScriptParser()

    @Test
    fun `parses sample downlink scripts into skin variants`() {
        val html = sampleDownlinkHtml()
        val heroes = listOf(
            Hero(id = 1L, name = "Angela", heroIcon = "angela.png"),
            Hero(id = 2L, name = "Odette"),
            Hero(id = 3L, name = "Lancelot")
        )

        val result = parser.parse(html, heroes)

        val angelaSanrio = result.filter { it.title == "Angela Sanrio" }
        assertEquals(6, angelaSanrio.size)
        assertEquals("Angela", angelaSanrio.first().heroName)
        assertEquals("angela.png", angelaSanrio.first().heroIcon)
        assertEquals("Sanrio", angelaSanrio.first().replacementSkinName)
        assertEquals(
            listOf("Default", "Basic", "Helloween", "Starlight", "Venom", "Summer"),
            angelaSanrio.map { it.originalSkinName }
        )
        assertTrue(angelaSanrio.all { it.sfileUrl.startsWith("https://sfile.co/") })
    }

    @Test
    fun `skips titles that do not match a hero`() {
        val html = sampleDownlinkHtml()

        val result = parser.parse(html, listOf(Hero(id = 1L, name = "Angela")))

        assertFalse(result.any { it.title.startsWith("Recall", ignoreCase = true) })
    }

    @Test
    fun `prefers longest hero name when matching title start`() {
        val html = """
            <script>
                const scripts = {
                    yiSunShinCollector: { title: 'Yi Sun Shin Collector', image: 'image.png', skins: [{ name: 'Default', url: 'https://sfile.co/example' }] }
                };
            </script>
        """.trimIndent()
        val heroes = listOf(
            Hero(id = 1L, name = "Yi"),
            Hero(id = 2L, name = "Yi Sun Shin")
        )

        val result = parser.parse(html, heroes)

        assertEquals(1, result.size)
        assertEquals("Yi Sun Shin", result.single().heroName)
        assertEquals("Collector", result.single().replacementSkinName)
    }

    private fun sampleDownlinkHtml(): String =
        listOf("sample/downlink.html", "../sample/downlink.html")
            .map(::File)
            .first { it.exists() }
            .readText()
}
