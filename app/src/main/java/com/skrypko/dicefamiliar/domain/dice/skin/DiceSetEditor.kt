package com.skrypko.dicefamiliar.domain.dice.skin

import kotlinx.serialization.json.Json

/** Which picture of a die an uploaded image replaces. */
enum class DieImageSlot {
    /** The flat silhouette used in the palette and with Quick roll. */
    IMAGE_2D,

    /** The texture painted on every face of the 3D die. */
    FACE_3D,
}

/** Pure edits of a [DiceSetManifest], used by the in-app dice-set editor. */
object DiceSetEditor {
    const val CUSTOM_KEY = "dN"

    private val json = Json {
        prettyPrint = true
        explicitNulls = false
    }

    /** A new, empty set that follows the app theme. */
    fun newManifest(id: String, name: String): DiceSetManifest = DiceSetManifest(
        formatVersion = DiceSetFormat.VERSION,
        id = id,
        name = name.trim(),
        style = DieStyleSpec(
            fill = DiceSetFormat.THEME,
            edge = DiceSetFormat.THEME,
            text = DiceSetFormat.THEME,
            edgeWidth = 2f,
        ),
    )

    /** Custom dN dice are spheres in 3D, so they have no face texture. */
    fun supports(key: String, slot: DieImageSlot): Boolean =
        key in DiceSetFormat.DIE_KEYS && !(slot == DieImageSlot.FACE_3D && key == CUSTOM_KEY)

    /** Where the editor stores an uploaded image, relative to the set folder. */
    fun fileFor(key: String, slot: DieImageSlot): String = when (slot) {
        DieImageSlot.IMAGE_2D -> "2d/$key.png"
        DieImageSlot.FACE_3D -> "3d/${key}_face.png"
    }

    fun imagePath(manifest: DiceSetManifest, key: String, slot: DieImageSlot): String? {
        val spec = manifest.dice[key] ?: return null
        return when (slot) {
            DieImageSlot.IMAGE_2D -> spec.image2d
            DieImageSlot.FACE_3D -> spec.faceTexture3d
        }
    }

    /** Sets (or with a null [path], removes) one die's image. Empty die entries are dropped. */
    fun withImage(manifest: DiceSetManifest, key: String, slot: DieImageSlot, path: String?): DiceSetManifest {
        require(supports(key, slot)) { "$slot is not supported on $key" }
        val spec = manifest.dice[key] ?: DieStyleSpec()
        val updated = when (slot) {
            DieImageSlot.IMAGE_2D -> spec.copy(image2d = path)
            DieImageSlot.FACE_3D -> spec.copy(faceTexture3d = path)
        }
        val dice = if (updated == DieStyleSpec()) manifest.dice - key else manifest.dice + (key to updated)
        return manifest.copy(dice = dice)
    }

    /** Every file path the manifest refers to. */
    fun referencedPaths(manifest: DiceSetManifest): Set<String> =
        (listOf(manifest.style) + manifest.dice.values)
            .flatMap { listOfNotNull(it.font, it.image2d, it.faceTexture3d) }
            .toSet()

    fun encode(manifest: DiceSetManifest): String = json.encodeToString(DiceSetManifest.serializer(), manifest)
}
