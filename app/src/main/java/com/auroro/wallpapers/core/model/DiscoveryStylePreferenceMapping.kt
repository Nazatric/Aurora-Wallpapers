package com.auroro.wallpapers.core.model

/**
 * Maps retained, user-facing discovery topics to the real recipes that can bias For You.
 * These are local query-mix preferences, not provider tags or global result rejection rules.
 */
object DiscoveryStylePreferenceMapping {
    private val mapping: Map<String, Set<String>> = mapOf(
        DiscoveryStyle.FRUTIGER_AERO.id to setOf("frutiger-aero", "translucent-structures", "biophilic-architecture"),
        DiscoveryStyle.BOTANICAL_ART.id to setOf("luminous-botanical-art", "surreal-flowers", "botanical-illustration", "art-nouveau-botanical"),
        DiscoveryStyle.FRUTIGER_ZEN.id to setOf("frutiger-zen", "underwater-worlds", "futuristic-gardens"),
        DiscoveryStyle.DARK_AERO.id to setOf("dark-aero", "liquid-metal", "refraction-art"),
        DiscoveryStyle.SURREAL.id to setOf("surrealism", "dreamlike-landscapes", "impossible-architecture", "cosmic-surrealism"),
        DiscoveryStyle.WEIRDCORE.id to setOf("weirdcore", "dreamcore", "liminal-spaces", "uncanny-architecture"),
        DiscoveryStyle.VAPORWAVE_Y2K.id to setOf("vaporwave", "y2k-graphics", "synthwave", "outrun", "retro-3d"),
        DiscoveryStyle.ABSTRACT_DIGITAL.id to setOf("abstract-geometry", "generative-art", "procedural-patterns", "glitch-art", "experimental-digital"),
        DiscoveryStyle.SPACE_ART.id to setOf("celestial-art", "sci-fi-environments", "cosmic-surrealism", "fractal-art"),
    )

    fun recipeIds(styleIds: Set<String>): Set<String> =
        styleIds.flatMapTo(linkedSetOf()) { mapping[it].orEmpty() }.intersect(DiscoveryCatalog.allIds)
}
