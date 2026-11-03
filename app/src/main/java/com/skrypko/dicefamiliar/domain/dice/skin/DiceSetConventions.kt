package com.skrypko.dicefamiliar.domain.dice.skin

/**
 * Finds set files by name (case-insensitive): `d6.png` or `2d/d6.png` → 2D image,
 * `d6_face.png` or `3d/d6_face.png` → 3D face texture, and the first `.ttf`/`.otf` at the root
 * or in `fonts/` → font. Used to build a manifest for a pack without one, and to fill in what a
 * manifest leaves out (fields the manifest sets always win).
 */
object DiceSetConventions {

    /** A manifest for a pack without `manifest.json`, or null when no file matches. */
    fun manifestFromFiles(name: String, files: Collection<String>): DiceSetManifest? {
        val base = DiceSetEditor.newManifest(DiceSetIds.fromName(name), name)
        val filled = fillFromFiles(base, files)
        return filled.takeIf { it.dice.isNotEmpty() || it.style.font != null }
    }

    /** Adds conventionally named files the manifest doesn't mention: per-die images and the font. */
    fun fillFromFiles(manifest: DiceSetManifest, files: Collection<String>): DiceSetManifest {
        val byLower = files.associateBy { it.lowercase() }
        fun find(candidates: List<String>): String? = candidates.firstNotNullOfOrNull { byLower[it] }
        val extensions = DiceSetFormat.IMAGE_EXTENSIONS.sorted()

        val dice = manifest.dice.toMutableMap()
        DiceSetFormat.DIE_KEYS.forEach { key ->
            val k = key.lowercase()
            val spec = dice[key] ?: DieStyleSpec()
            val image = spec.image2d ?: find(extensions.flatMap { listOf("2d/$k.$it", "$k.$it") })
            val face = spec.faceTexture3d ?: if (DiceSetEditor.supports(key, DieImageSlot.FACE_3D)) {
                find(extensions.flatMap { listOf("3d/${k}_face.$it", "${k}_face.$it") })
            } else {
                null
            }
            val updated = spec.copy(image2d = image, faceTexture3d = face)
            if (updated != DieStyleSpec()) dice[key] = updated
        }

        val font = manifest.style.font ?: files.sorted().firstOrNull { path ->
            val lower = path.lowercase()
            lower.substringAfterLast('.', "") in DiceSetFormat.FONT_EXTENSIONS &&
                ('/' !in lower || lower.startsWith("fonts/") && lower.count { it == '/' } == 1)
        }
        return manifest.copy(style = manifest.style.copy(font = font), dice = dice)
    }
}
