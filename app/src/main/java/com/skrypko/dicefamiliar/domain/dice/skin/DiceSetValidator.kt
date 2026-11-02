package com.skrypko.dicefamiliar.domain.dice.skin

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class DiceSetIssue(val path: String, val message: String)

sealed interface DiceSetParseResult {
    /** Parsed manifest; [issues] are non-fatal and the affected items fall back to defaults. */
    data class Parsed(val manifest: DiceSetManifest, val issues: List<DiceSetIssue>) : DiceSetParseResult
    data class Failed(val issue: DiceSetIssue) : DiceSetParseResult
}

/** Parses and validates dice-set manifests. Pure Kotlin; used by the loader and by tests. */
object DiceSetValidator {
    private val json = Json { ignoreUnknownKeys = true }
    private val COLOR = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val ID = Regex("^[a-z0-9_\\-]{1,40}$")

    fun parse(text: String): DiceSetParseResult {
        val manifest = try {
            json.decodeFromString<DiceSetManifest>(text)
        } catch (e: SerializationException) {
            return DiceSetParseResult.Failed(DiceSetIssue(DiceSetFormat.MANIFEST, "Invalid JSON: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            return DiceSetParseResult.Failed(DiceSetIssue(DiceSetFormat.MANIFEST, "Invalid JSON: ${e.message}"))
        }
        if (manifest.formatVersion != DiceSetFormat.VERSION) {
            return DiceSetParseResult.Failed(
                DiceSetIssue("formatVersion", "Unsupported version ${manifest.formatVersion}"),
            )
        }
        return DiceSetParseResult.Parsed(manifest, validate(manifest))
    }

    fun validate(manifest: DiceSetManifest): List<DiceSetIssue> = buildList {
        if (!ID.matches(manifest.id)) add(DiceSetIssue("id", "Use 1-40 chars: a-z, 0-9, _ or -"))
        if (manifest.name.isBlank()) add(DiceSetIssue("name", "Name is required"))
        addAll(validateStyle("style", manifest.style, key = null))
        manifest.dice.forEach { (key, spec) ->
            if (key !in DiceSetFormat.DIE_KEYS) {
                add(DiceSetIssue("dice.$key", "Unknown die; use one of ${DiceSetFormat.DIE_KEYS}"))
            } else {
                addAll(validateStyle("dice.$key", spec, key))
            }
        }
    }

    private fun validateStyle(path: String, spec: DieStyleSpec, key: String?): List<DiceSetIssue> = buildList {
        listOf("fill" to spec.fill, "edge" to spec.edge, "text" to spec.text).forEach { (name, value) ->
            if (value != null && value != DiceSetFormat.THEME && !COLOR.matches(value)) {
                add(DiceSetIssue("$path.$name", "Colour must be #RRGGBB, #AARRGGBB or \"theme\""))
            }
        }
        spec.edgeWidth?.let {
            if (it < 0f || it > DiceSetFormat.MAX_EDGE_WIDTH) add(DiceSetIssue("$path.edgeWidth", "Must be 0..8"))
        }
        checkPath("$path.font", spec.font, DiceSetFormat.FONT_EXTENSIONS)?.let(::add)
        checkPath("$path.image2d", spec.image2d, DiceSetFormat.IMAGE_EXTENSIONS)?.let(::add)
        checkPath("$path.faceTexture3d", spec.faceTexture3d, DiceSetFormat.IMAGE_EXTENSIONS)?.let(::add)
        if (spec.pips == true && key != "d6") add(DiceSetIssue("$path.pips", "Pips are only supported on d6"))
    }

    /** Paths are relative to the set folder; no absolute paths, `..`, or backslashes. */
    fun checkPath(path: String, value: String?, extensions: Set<String>): DiceSetIssue? {
        if (value == null) return null
        return when {
            value.isBlank() -> DiceSetIssue(path, "Path is empty")
            value.startsWith("/") || value.contains('\\') || value.contains(':') ->
                DiceSetIssue(path, "Path must be relative to the set folder")
            value.split('/').any { it == ".." || it == "." || it.isEmpty() } ->
                DiceSetIssue(path, "Path must not contain '..', '.' or empty segments")
            value.substringAfterLast('.', "").lowercase() !in extensions ->
                DiceSetIssue(path, "Allowed types: ${extensions.joinToString()}")
            else -> null
        }
    }
}
