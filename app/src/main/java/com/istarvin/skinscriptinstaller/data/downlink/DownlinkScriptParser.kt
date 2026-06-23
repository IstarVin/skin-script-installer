package com.istarvin.skinscriptinstaller.data.downlink

import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownlinkScriptParser @Inject constructor() {

    fun parse(html: String, heroes: List<Hero>): List<DownlinkRepositoryEntry> {
        if (heroes.isEmpty()) return emptyList()

        val objectLiteral = extractScriptsObject(html) ?: return emptyList()
        val heroMatchers = heroes
            .sortedByDescending { it.name.length }
            .map { hero -> hero to normalizeWords(hero.name) }
            .filter { (_, words) -> words.isNotEmpty() }

        return parseScriptObjects(objectLiteral).flatMap { scriptObject ->
            val key = scriptObject.key
            val body = scriptObject.body
            val title = extractStringProperty(body, "title") ?: return@flatMap emptyList()
            val hero = findTitleHero(title, heroMatchers) ?: return@flatMap emptyList()
            val replacementSkinName = title.substring(hero.name.length).trim()
            if (replacementSkinName.isBlank()) return@flatMap emptyList()

            val imageUrl = extractStringProperty(body, "image")
            extractSkins(body).mapIndexed { index, skin ->
                DownlinkRepositoryEntry(
                    id = "$key:$index:${skin.name}:${skin.url}".stableId(),
                    title = title,
                    imageUrl = imageUrl,
                    heroName = hero.name,
                    heroIcon = hero.heroIcon,
                    replacementSkinName = replacementSkinName,
                    originalSkinName = skin.name,
                    sfileUrl = skin.url
                )
            }
        }
    }

    private fun extractScriptsObject(html: String): String? {
        val start = html.indexOf("const scripts")
        if (start < 0) return null
        val equals = html.indexOf('=', start)
        if (equals < 0) return null
        val firstBrace = html.indexOf('{', equals)
        if (firstBrace < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        var quote = '\u0000'
        for (index in firstBrace until html.length) {
            val char = html[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == quote) {
                    inString = false
                }
                continue
            }

            when (char) {
                '\'', '"' -> {
                    inString = true
                    quote = char
                }
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(firstBrace, index + 1)
                }
            }
        }
        return null
    }

    private fun parseScriptObjects(objectLiteral: String): List<ScriptObject> {
        val result = mutableListOf<ScriptObject>()
        var index = 1
        while (index < objectLiteral.lastIndex) {
            while (index < objectLiteral.lastIndex && (objectLiteral[index].isWhitespace() || objectLiteral[index] == ',')) index++
            val keyStart = index
            while (index < objectLiteral.lastIndex && (objectLiteral[index].isLetterOrDigit() || objectLiteral[index] == '_' || objectLiteral[index] == '-')) index++
            val key = objectLiteral.substring(keyStart, index).trim()
            if (key.isBlank()) break
            while (index < objectLiteral.lastIndex && objectLiteral[index].isWhitespace()) index++
            if (index >= objectLiteral.lastIndex || objectLiteral[index] != ':') break
            index++
            while (index < objectLiteral.lastIndex && objectLiteral[index].isWhitespace()) index++
            if (index >= objectLiteral.lastIndex || objectLiteral[index] != '{') break
            val bodyStart = index
            val bodyEnd = findMatching(objectLiteral, bodyStart, '{', '}') ?: break
            result += ScriptObject(key = key, body = objectLiteral.substring(bodyStart, bodyEnd + 1))
            index = bodyEnd + 1
        }
        return result
    }

    private fun extractSkins(body: String): List<SkinObject> {
        val skinsStart = body.indexOf("skins")
        if (skinsStart < 0) return emptyList()
        val arrayStart = body.indexOf('[', skinsStart)
        if (arrayStart < 0) return emptyList()
        val arrayEnd = findMatching(body, arrayStart, '[', ']') ?: return emptyList()
        val array = body.substring(arrayStart + 1, arrayEnd)
        val skins = mutableListOf<SkinObject>()
        var index = 0
        while (index < array.length) {
            val objectStart = array.indexOf('{', index)
            if (objectStart < 0) break
            val objectEnd = findMatching(array, objectStart, '{', '}') ?: break
            val skinBody = array.substring(objectStart, objectEnd + 1)
            val name = extractStringProperty(skinBody, "name")
            val url = extractStringProperty(skinBody, "url")
            if (!name.isNullOrBlank() && !url.isNullOrBlank()) {
                skins += SkinObject(name = name.trim(), url = url.trim())
            }
            index = objectEnd + 1
        }
        return skins
    }

    private fun extractStringProperty(body: String, property: String): String? {
        val regex = Regex("""\b${Regex.escape(property)}\s*:\s*(['\"])((?:\\.|(?!\1).)*)\1""")
        return regex.find(body)?.groupValues?.get(2)?.unescapeJsString()
    }

    private fun findMatching(text: String, start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        var quote = '\u0000'
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == quote) {
                    inString = false
                }
                continue
            }
            when (char) {
                '\'', '"' -> {
                    inString = true
                    quote = char
                }
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun findTitleHero(
        title: String,
        heroMatchers: List<Pair<Hero, List<String>>>
    ): Hero? {
        val titleWords = normalizeWords(title)
        return heroMatchers.firstOrNull { (_, heroWords) ->
            titleWords.size > heroWords.size && titleWords.take(heroWords.size) == heroWords
        }?.first
    }

    private fun normalizeWords(value: String): List<String> = value
        .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
        .lowercase()
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    private fun String.unescapeJsString(): String = this
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")

    private fun String.stableId(): String = Integer.toHexString(hashCode())

    private data class ScriptObject(val key: String, val body: String)
    private data class SkinObject(val name: String, val url: String)
}
