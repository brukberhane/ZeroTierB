package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon

sealed class WorldBlobParseResult {
    data class Moon(val worldId: String, val seed: String?) : WorldBlobParseResult()

    data class Planet(val worldId: String) : WorldBlobParseResult()

    data class Error(val reason: String) : WorldBlobParseResult()
}

object WorldBlobParser {
    private const val TYPE_PLANET = 1
    private const val TYPE_MOON = 127

    fun parse(bytes: ByteArray): WorldBlobParseResult {
        if (bytes.isEmpty()) return WorldBlobParseResult.Error("empty")
        val text = stripBom(bytes.toString(Charsets.UTF_8)).trimStart()
        if (text.startsWith("{")) return parseJson(text)
        return parseBinary(bytes)
    }

    private fun parseBinary(bytes: ByteArray): WorldBlobParseResult {
        if (bytes.size < 17) return WorldBlobParseResult.Error("too short")
        val type = bytes[0].toInt() and 0xff
        val id = readBeU64(bytes, 1)
        val worldId = java.lang.Long.toUnsignedString(id, 16).padStart(16, '0')
        return when (type) {
            TYPE_PLANET -> WorldBlobParseResult.Planet(worldId)
            TYPE_MOON -> WorldBlobParseResult.Moon(worldId, seed = null)
            else -> WorldBlobParseResult.Error("unknown type")
        }
    }

    private fun parseJson(text: String): WorldBlobParseResult {
        val worldType = extractJsonStringValue(text, "worldType")
            ?: return WorldBlobParseResult.Error("missing worldType")
        if (!worldType.equals("moon", ignoreCase = true)) {
            return WorldBlobParseResult.Error("unsupported worldType")
        }
        val objtype = extractJsonStringValue(text, "objtype")
        if (objtype != null && !objtype.equals("world", ignoreCase = true)) {
            return WorldBlobParseResult.Error("invalid objtype")
        }
        val idRaw = extractJsonStringValue(text, "id")
            ?: return WorldBlobParseResult.Error("missing id")
        val worldId = Moon.normalizeWorldIdOrNull(idRaw)
            ?: return WorldBlobParseResult.Error("invalid id")
        val identity = extractFirstIdentity(text)
        val seed = identity?.substringBefore(':')?.let { Moon.normalizeSeed(it) }
        return WorldBlobParseResult.Moon(worldId, seed)
    }

    private fun extractJsonStringValue(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        val match = pattern.find(json) ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    private fun extractFirstIdentity(json: String): String? {
        val pattern = Regex(""""identity"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        return pattern.find(json)?.groupValues?.get(1)?.let { unescapeJsonString(it) }
    }

    private fun unescapeJsonString(value: String): String =
        value.replace("\\\"", "\"").replace("\\\\", "\\")

    private fun stripBom(text: String): String =
        if (text.startsWith('\uFEFF')) text.drop(1) else text

    private fun readBeU64(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xff)
        }
        return value
    }
}
