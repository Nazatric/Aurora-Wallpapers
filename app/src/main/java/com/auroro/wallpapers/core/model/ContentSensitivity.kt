package com.auroro.wallpapers.core.model

/**
 * Conservative metadata-only belt-and-suspenders filter. It does not inspect pixels and cannot
 * certify safety. Ambiguous terms such as "figure", "nude", "swimwear" and "person" are not blocked.
 */
object ContentSensitivity {
    private val explicitMarkers = setOf(
        "nsfw", "porn", "pornography", "pornographic", "hardcore", "sexually explicit", "explicit sexual content",
    )

    fun isPotentiallyExplicit(wallpaper: Wallpaper): Boolean {
        val metadata = sequenceOf(wallpaper.title, wallpaper.category)
            .filterNotNull()
            .plus(wallpaper.tags.asSequence().map { it.name })
        return metadata.any { value ->
            val normalized = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
            normalized in explicitMarkers ||
                normalized.split(' ').any { it in setOf("nsfw", "porn", "pornography", "pornographic", "hardcore") }
        }
    }
}
