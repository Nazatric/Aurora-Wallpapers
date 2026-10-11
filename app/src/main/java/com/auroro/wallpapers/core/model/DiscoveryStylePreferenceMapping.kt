package com.auroro.wallpapers.core.model

/** Legacy settings labels map to several real recipes; preferences bias the mix without pinning it. */
object DiscoveryStylePreferenceMapping {
    private val mapping: Map<String, Set<String>> = mapOf(
        DiscoveryStyle.FRUTIGER_AERO.id to setOf("frutiger-aero", "translucent-structures", "biophilic-architecture"),
        DiscoveryStyle.FRUTIGER_FLOWER.id to setOf("frutiger-flower", "surreal-flowers", "botanical-illustration"),
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
