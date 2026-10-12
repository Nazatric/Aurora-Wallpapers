package com.auroro.wallpapers.core.model

import java.util.Locale
import kotlin.random.Random

/** Broad visual families used only by the internal session mixer; no family is exposed as a selector. */
enum class DiscoveryFamily {
    ABSTRACT,
    EXPERIMENTAL,
    SURREAL,
    RETROFUTURE,
    MATERIALS,
    NATURE,
    ARCHITECTURE,
    DIGITAL_WORLDS,
    ILLUSTRATION,
    SCIENTIFIC,
    PHOTOGRAPHY,
    AERO,
}

/** A provider-specific plain-text query and evidence vocabulary. Evidence terms are not API tags. */
data class DiscoverySourceQuery(
    val phrases: List<String>,
    val evidenceTerms: Set<String>,
) {
    fun phraseAt(index: Int): String = phrases[Math.floorMod(index, phrases.size)]
}

/** One internal query recipe; its evidence vocabulary ranks results but does not reject provider rows. */
data class DiscoveryRecipe(
    val id: String,
    val label: String,
    val family: DiscoveryFamily,
    val sourceQueries: Map<WallpaperSource, DiscoverySourceQuery>,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9-]*")))
        require(sourceQueries[WallpaperSource.WALLHAVEN]?.phrases?.isNotEmpty() == true)
    }

    fun query(source: WallpaperSource, phraseIndex: Int = 0): String? =
        sourceQueries[source]?.phraseAt(phraseIndex)

    fun evidenceScore(wallpaper: Wallpaper): Int {
        if (!wallpaper.source.isIntegrated) return 0
        val evidence = sourceQueries[wallpaper.source]?.evidenceTerms.orEmpty()
        if (evidence.isEmpty()) return 0
        val metadata = sequenceOf(wallpaper.title, wallpaper.category)
            .filterNotNull()
            .plus(wallpaper.tags.asSequence().map { it.name })
            .joinToString(" ") { normalize(it) }
            .split(' ')
            .filter(String::isNotBlank)
            .toSet()
        return evidence.count { term ->
            val normalized = normalize(term)
            normalized.isNotEmpty() && (normalized in metadata || normalized.split(' ').all { it in metadata })
        }
    }

    /** Advisory evidence used for ranking/diagnostics only; this is never a result-acceptance gate. */
    fun isRelevant(wallpaper: Wallpaper, minimumEvidence: Int = 1): Boolean =
        wallpaper.source in sourceQueries && evidenceScore(wallpaper) >= minimumEvidence

    companion object {
        private fun normalize(value: String): String = value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}

/**
 * A process-local discovery session. Recipe IDs are candidate searches, not headings: the app never
 * renders a category label until a source returns enough matching, validated wallpapers.
 */
data class DiscoverySession(
    val seed: Long,
    val createdAtMillis: Long,
    val hourBucket: Long,
    val refreshOrdinal: Int,
    val candidateRecipeIds: List<String>,
) {
    val sessionId: String
        get() = "mix:$hourBucket:$refreshOrdinal:${seed.toString(16)}"

    val recipes: List<DiscoveryRecipe>
        get() = candidateRecipeIds.mapNotNull(DiscoveryCatalog::find)
}

/** Pure planner; tests inject a seed, while each real process/session gets a fresh random seed. */
object DiscoverySessionPlanner {
    const val TARGET_VALIDATED_RECIPES = 6
    const val MAX_CANDIDATE_RECIPES = 12
    private val qualityAnchorRecipeIds = setOf(
        "abstract-geometry", "fractal-art", "frutiger-aero", "space-art", "surrealism", "vaporwave",
    )

    fun newSession(
        seed: Long,
        nowMillis: Long,
        rotateHourly: Boolean = true,
        refreshOrdinal: Int = 0,
        recentRecipeIds: Set<String> = emptySet(),
        preferredRecipeIds: Set<String> = emptySet(),
        excludedRecipeIds: Set<String> = emptySet(),
        candidateCount: Int = MAX_CANDIDATE_RECIPES,
    ): DiscoverySession {
        val all = DiscoveryCatalog.recipes.filterNot { it.id in excludedRecipeIds }
        val recentSafe = recentRecipeIds.intersect(all.mapTo(hashSetOf()) { it.id })
        // Avoid recent recipes whenever possible, but never let a short history erase discovery.
        val fresh = all.filterNot { it.id in recentSafe }
        val pool = if (fresh.size >= TARGET_VALIDATED_RECIPES) fresh else all
        val preferred = preferredRecipeIds.intersect(pool.mapTo(hashSetOf()) { it.id })
        val random = Random(seed)
        val requested = candidateCount.coerceIn(1, MAX_CANDIDATE_RECIPES).coerceAtMost(pool.size)
        val chosen = mutableListOf<DiscoveryRecipe>()
        val usedFamilies = mutableSetOf<DiscoveryFamily>()
        if (requested > 1) {
            val anchors = pool.filter { it.id in qualityAnchorRecipeIds && it.id !in recentSafe }
            if (anchors.isNotEmpty()) {
                val anchor = anchors[random.nextInt(anchors.size)]
                chosen += anchor
                usedFamilies += anchor.family
            }
        }
        // Keep one genuinely mapped museum search in each normal For You mix; the provider can
        // then contribute its own public-domain records without pretending its metadata matches
        // every Wallhaven recipe.
        if (requested > 0 && chosen.none { WallpaperSource.ART_INSTITUTE in it.sourceQueries }) {
            val museumCandidates = pool.filter {
                WallpaperSource.ART_INSTITUTE in it.sourceQueries && it !in chosen
            }.ifEmpty {
                // Source coverage takes precedence over skipping every mapped museum recipe as recent.
                all.filter { WallpaperSource.ART_INSTITUTE in it.sourceQueries && it !in chosen }
            }
            if (museumCandidates.isNotEmpty()) {
                val candidate = museumCandidates[random.nextInt(museumCandidates.size)]
                if (chosen.size >= requested) chosen.removeAt(chosen.lastIndex)
                chosen += candidate
                usedFamilies += candidate.family
            }
        }
        val preferredBudget = minOf(preferred.size, maxOf(1, requested / 3))
        val alreadyPreferred = chosen.count { it.id in preferred }

        repeat(requested - chosen.size) { index ->
            val wantsPreferred = preferred.isNotEmpty() && index + alreadyPreferred < preferredBudget
            val remaining = pool.filter { candidate ->
                candidate !in chosen && (usedFamilies.size >= DiscoveryFamily.entries.size || candidate.family !in usedFamilies) &&
                    (!wantsPreferred || candidate.id in preferred)
            }
            val fallback = if (remaining.isEmpty()) {
                pool.filter { candidate -> candidate !in chosen && (!wantsPreferred || candidate.id in preferred) }
            } else remaining
            val candidates = fallback.ifEmpty { pool.filterNot { it in chosen } }
            if (candidates.isEmpty()) return@repeat
            val preferredWeight = if (preferred.isEmpty() || !wantsPreferred) 1 else 3
            val totalWeight = candidates.sumOf { if (it.id in preferred && preferredWeight > 1) preferredWeight else 1 }
            var choice = random.nextInt(totalWeight)
            val selected = candidates.firstOrNull { candidate ->
                val weight = if (candidate.id in preferred && preferredWeight > 1) preferredWeight else 1
                choice -= weight
                choice < 0
            } ?: candidates.first()
            chosen += selected
            usedFamilies += selected.family
        }

        val hourBucket = if (rotateHourly) DiscoveryRotation.bucketAt(nowMillis) else 0L
        return DiscoverySession(
            seed = seed,
            createdAtMillis = nowMillis,
            hourBucket = hourBucket,
            refreshOrdinal = refreshOrdinal.coerceAtLeast(0),
            candidateRecipeIds = chosen.map { it.id },
        )
    }

    fun newRandomSession(
        nowMillis: Long,
        rotateHourly: Boolean,
        refreshOrdinal: Int,
        recentRecipeIds: Set<String> = emptySet(),
        preferredRecipeIds: Set<String> = emptySet(),
        excludedRecipeIds: Set<String> = emptySet(),
    ): DiscoverySession = newSession(
        seed = Random.Default.nextLong(),
        nowMillis = nowMillis,
        rotateHourly = rotateHourly,
        refreshOrdinal = refreshOrdinal,
        recentRecipeIds = recentRecipeIds,
        preferredRecipeIds = preferredRecipeIds,
        excludedRecipeIds = excludedRecipeIds,
    )
}

/** Curated plain-language query vocabulary. Search phrases are not represented as provider tags. */
object DiscoveryCatalog {
    private fun recipe(
        id: String,
        label: String,
        family: DiscoveryFamily,
        wallhavenPhrases: List<String>,
        evidence: String,
        artInstitutePhrase: String? = null,
        artInstituteEvidence: String = "",
    ): DiscoveryRecipe {
        val evidenceTerms = evidence.split('|').map(String::trim).filter(String::isNotEmpty).toSet()
        val sourceQueries = buildMap {
            put(WallpaperSource.WALLHAVEN, DiscoverySourceQuery(wallhavenPhrases, evidenceTerms))
            artInstitutePhrase?.let { phrase ->
                val terms = artInstituteEvidence.split('|').map(String::trim).filter(String::isNotEmpty).toSet()
                put(WallpaperSource.ART_INSTITUTE, DiscoverySourceQuery(listOf(phrase), terms))
            }
        }
        return DiscoveryRecipe(
            id = id,
            label = label,
            family = family,
            sourceQueries = sourceQueries,
        )
    }

    val recipes: List<DiscoveryRecipe> = listOf(
        recipe("abstract-geometry", "Abstract geometry", DiscoveryFamily.ABSTRACT, listOf("abstract geometric art", "geometric abstraction"), "geometric|geometry", artInstitutePhrase = "geometric", artInstituteEvidence = "geometric|geometry|abstract"),
        recipe("generative-art", "Generative art", DiscoveryFamily.EXPERIMENTAL, listOf("generative art", "algorithmic abstract art"), "generative|algorithmic"),
        recipe("procedural-patterns", "Procedural patterns", DiscoveryFamily.EXPERIMENTAL, listOf("procedural pattern art", "generative patterns"), "procedural|procedural pattern"),
        recipe("fractal-art", "Fractal art", DiscoveryFamily.ABSTRACT, listOf("fractal art", "fractal landscape"), "fractal|mandelbrot"),
        recipe("mathematical-visualization", "Mathematical visualisation", DiscoveryFamily.EXPERIMENTAL, listOf("mathematical visualization art", "mathematical abstract"), "mathematical|visualization|visualisation"),
        recipe("optical-art", "Optical art", DiscoveryFamily.ABSTRACT, listOf("optical art", "op art geometric"), "optical|op art"),
        recipe("color-field", "Colour-field abstraction", DiscoveryFamily.ABSTRACT, listOf("color field abstract painting", "colour field art"), "color field|colour field"),
        recipe("abstract-expressionism", "Abstract expressionism", DiscoveryFamily.ABSTRACT, listOf("abstract expressionism painting", "abstract expressive painting"), "expressionism|abstract expressionism"),
        recipe("hard-edge-abstraction", "Hard-edge abstraction", DiscoveryFamily.ABSTRACT, listOf("hard edge abstract art", "hard-edge painting"), "hard edge|hard-edge"),
        recipe("organic-abstraction", "Organic abstraction", DiscoveryFamily.ABSTRACT, listOf("organic abstract art", "organic forms abstraction"), "organic abstraction|biomorphic"),
        recipe("fluid-abstraction", "Fluid abstraction", DiscoveryFamily.ABSTRACT, listOf("fluid abstract art", "flowing abstract shapes"), "fluid abstraction|fluid abstract"),
        recipe("minimalist-geometry", "Minimalist geometry", DiscoveryFamily.ABSTRACT, listOf("minimal geometric art", "minimalist geometry"), "minimal geometric|minimalist geometry"),
        recipe("maximalist-color", "Maximalist colour", DiscoveryFamily.ABSTRACT, listOf("maximalist colorful abstract art", "maximalist color"), "maximalist composition|maximalist art"),
        recipe("abstract-collage", "Abstract collage", DiscoveryFamily.EXPERIMENTAL, listOf("abstract collage art", "geometric collage"), "abstract collage|collage art"),
        recipe("surrealism", "Surrealism", DiscoveryFamily.SURREAL, listOf("surrealism art", "surreal landscape"), "surreal|surrealism"),
        recipe("cosmic-surrealism", "Cosmic surrealism", DiscoveryFamily.SURREAL, listOf("cosmic surrealism", "surreal cosmic art"), "cosmic surreal|cosmic surrealism"),
        recipe("dreamcore", "Dreamcore", DiscoveryFamily.SURREAL, listOf("dreamcore surreal art", "dreamcore landscape"), "dreamcore|dreamlike"),
        recipe("weirdcore", "Weirdcore", DiscoveryFamily.SURREAL, listOf("weirdcore digital art", "weirdcore landscape"), "weirdcore"),
        recipe("liminal-spaces", "Liminal spaces", DiscoveryFamily.SURREAL, listOf("liminal space photography", "liminal architecture"), "liminal"),
        recipe("uncanny-architecture", "Uncanny architecture", DiscoveryFamily.ARCHITECTURE, listOf("uncanny architecture", "surreal uncanny building"), "uncanny architecture|uncanny building"),
        recipe("dreamlike-landscapes", "Dreamlike landscapes", DiscoveryFamily.SURREAL, listOf("dreamlike landscape art", "ethereal surreal landscape"), "dreamlike landscape|ethereal landscape"),
        recipe("impossible-architecture", "Impossible architecture", DiscoveryFamily.ARCHITECTURE, listOf("impossible architecture art", "impossible buildings"), "impossible|architecture"),
        recipe("retro-futurism", "Retro-futurism", DiscoveryFamily.RETROFUTURE, listOf("retro futurism art", "retrofuturistic landscape"), "retrofuturism|retro futurism"),
        recipe("y2k-graphics", "Y2K graphics", DiscoveryFamily.RETROFUTURE, listOf("y2k graphics", "y2k digital art"), "y2k"),
        recipe("vaporwave", "Vaporwave", DiscoveryFamily.RETROFUTURE, listOf("vaporwave art", "vaporwave landscape"), "vaporwave"),
        recipe("synthwave", "Synthwave", DiscoveryFamily.RETROFUTURE, listOf("synthwave art", "synthwave city"), "synthwave"),
        recipe("outrun", "Outrun", DiscoveryFamily.RETROFUTURE, listOf("outrun aesthetic", "outrun landscape"), "outrun"),
        recipe("early-cgi", "Early CGI", DiscoveryFamily.RETROFUTURE, listOf("early cgi art", "retro computer graphics"), "cgi|computer graphics"),
        recipe("retro-3d", "Retro 3D", DiscoveryFamily.RETROFUTURE, listOf("retro 3d art", "vintage 3d graphics"), "retro 3d"),
        recipe("techno-organic", "Techno-organic design", DiscoveryFamily.RETROFUTURE, listOf("techno organic design", "biomechanical organic art"), "techno organic|biomechanical"),
        recipe("experimental-digital", "Experimental digital art", DiscoveryFamily.EXPERIMENTAL, listOf("experimental digital art", "experimental computer art"), "experimental|digital art"),
        recipe("cyberdelic", "Cyberdelic art", DiscoveryFamily.RETROFUTURE, listOf("cyberdelic art", "psychedelic cyber art"), "cyberdelic"),
        recipe("iridescent-materials", "Iridescent materials", DiscoveryFamily.MATERIALS, listOf("iridescent abstract material", "iridescent surface art"), "iridescent"),
        recipe("liquid-metal", "Liquid metal", DiscoveryFamily.MATERIALS, listOf("liquid metal abstract", "fluid chrome sculpture"), "liquid metal|chrome sculpture"),
        recipe("chrome-sculpture", "Chrome sculpture", DiscoveryFamily.MATERIALS, listOf("chrome sculpture", "reflective metal sculpture"), "chrome|sculpture"),
        recipe("glass-sculpture", "Glass sculpture", DiscoveryFamily.MATERIALS, listOf("glass sculpture art", "colored glass sculpture"), "glass sculpture|glass art"),
        recipe("translucent-structures", "Translucent structures", DiscoveryFamily.MATERIALS, listOf("translucent architecture", "transparent structure art"), "translucent|transparent"),
        recipe("holographic-color", "Holographic colour", DiscoveryFamily.MATERIALS, listOf("holographic color art", "holographic iridescent"), "holographic"),
        recipe("refraction-art", "Refraction art", DiscoveryFamily.MATERIALS, listOf("light refraction art", "refraction glass photography"), "refraction"),
        recipe("fluid-simulation", "Fluid simulation", DiscoveryFamily.EXPERIMENTAL, listOf("fluid simulation art", "fluid dynamics visualization"), "fluid simulation|fluid dynamics"),
        recipe("biophilic-architecture", "Biophilic architecture", DiscoveryFamily.ARCHITECTURE, listOf("biophilic architecture", "green building nature"), "biophilic|green architecture"),
        recipe("solarpunk", "Solarpunk environments", DiscoveryFamily.NATURE, listOf("solarpunk environment", "solarpunk city garden"), "solarpunk"),
        recipe("underwater-worlds", "Underwater worlds", DiscoveryFamily.NATURE, listOf("underwater world art", "underwater landscape"), "underwater|underwater world"),
        recipe("bioluminescent-scenes", "Bioluminescent scenes", DiscoveryFamily.NATURE, listOf("bioluminescent landscape", "bioluminescent forest"), "bioluminescent|bioluminescence"),
        recipe("futuristic-gardens", "Futuristic gardens", DiscoveryFamily.NATURE, listOf("futuristic garden art", "future garden architecture"), "futuristic garden|future garden"),
        recipe("miniature-ecosystems", "Miniature ecosystems", DiscoveryFamily.NATURE, listOf("miniature ecosystem art", "tiny terrarium landscape"), "miniature ecosystem|terrarium"),
        recipe("impossible-ecology", "Impossible ecology", DiscoveryFamily.NATURE, listOf("surreal ecology art", "impossible ecosystem"), "surreal ecology|impossible ecosystem"),
        recipe("brutalist-architecture", "Brutalist architecture", DiscoveryFamily.ARCHITECTURE, listOf("brutalist architecture", "brutalism building"), "brutalist|brutalism"),
        recipe("abstract-architecture", "Abstract architecture", DiscoveryFamily.ARCHITECTURE, listOf("abstract architecture", "architectural abstraction"), "abstract architecture|architectural abstraction"),
        recipe("isometric-worlds", "Isometric worlds", DiscoveryFamily.DIGITAL_WORLDS, listOf("isometric world art", "isometric environment"), "isometric"),
        recipe("voxel-art", "Voxel art", DiscoveryFamily.DIGITAL_WORLDS, listOf("voxel art landscape", "voxel world"), "voxel"),
        recipe("low-poly-landscapes", "Low-poly landscapes", DiscoveryFamily.DIGITAL_WORLDS, listOf("low poly landscape", "low-poly environment"), "low poly|low-poly"),
        recipe("miniature-dioramas", "Miniature dioramas", DiscoveryFamily.DIGITAL_WORLDS, listOf("miniature diorama art", "tiny diorama"), "diorama"),
        recipe("geometric-constructions", "Geometric constructions", DiscoveryFamily.ARCHITECTURE, listOf("geometric construction art", "geometric architecture"), "geometric construction|geometric architecture"),
        recipe("unusual-urban-spaces", "Unusual urban spaces", DiscoveryFamily.ARCHITECTURE, listOf("unusual urban architecture", "surreal cityscape"), "unusual urban|surreal cityscape"),
        recipe("psychedelic-patterns", "Psychedelic patterns", DiscoveryFamily.EXPERIMENTAL, listOf("psychedelic pattern art", "psychedelic geometric"), "psychedelic"),
        recipe("glitch-art", "Glitch art", DiscoveryFamily.EXPERIMENTAL, listOf("glitch art", "digital glitch"), "glitch"),
        recipe("datamoshing", "Datamoshing", DiscoveryFamily.EXPERIMENTAL, listOf("datamoshing art", "datamosh digital"), "datamosh|datamoshing"),
        recipe("digital-noise", "Digital noise art", DiscoveryFamily.EXPERIMENTAL, listOf("digital noise art", "digital static abstract"), "digital noise|digital static"),
        recipe("scanography", "Scanography", DiscoveryFamily.EXPERIMENTAL, listOf("scanography art", "scanner photography art"), "scanography|scanner photography"),
        recipe("mixed-media-collage", "Mixed-media collage", DiscoveryFamily.ILLUSTRATION, listOf("mixed media collage art", "experimental collage"), "mixed media|collage"),
        recipe("maximalist-compositions", "Maximalist compositions", DiscoveryFamily.ILLUSTRATION, listOf("maximalist art composition", "maximalist illustration"), "maximalist composition|maximalist illustration"),
        recipe("botanical-illustration", "Botanical illustration", DiscoveryFamily.ILLUSTRATION, listOf("botanical illustration", "vintage botanical drawing"), "botanical|botany", artInstitutePhrase = "botanical", artInstituteEvidence = "botanical|botany|plant|flower"),
        recipe("surreal-flowers", "Surreal flowers", DiscoveryFamily.ILLUSTRATION, listOf("surreal flower art", "surreal floral illustration"), "surreal flower|surreal floral"),
        recipe("unusual-plant-photography", "Unusual plant photography", DiscoveryFamily.PHOTOGRAPHY, listOf("unusual plant macro photography", "rare plant photography"), "plant|botanical|macro"),
        recipe("macro-textures", "Macro textures", DiscoveryFamily.PHOTOGRAPHY, listOf("macro texture photography", "abstract macro texture"), "macro texture|texture photography"),
        recipe("microscopic-imagery", "Microscopic imagery", DiscoveryFamily.SCIENTIFIC, listOf("microscopic imagery", "microscope photography"), "microscopic|microscope"),
        recipe("ink-in-water", "Ink in water", DiscoveryFamily.PHOTOGRAPHY, listOf("ink in water photography", "ink diffusion abstract"), "ink in water|ink diffusion"),
        recipe("stained-glass-art", "Stained glass", DiscoveryFamily.ILLUSTRATION, listOf("stained glass art", "stained glass window"), "stained glass", artInstitutePhrase = "stained glass", artInstituteEvidence = "stained glass|glass"),
        recipe("paper-craft", "Paper craft", DiscoveryFamily.ILLUSTRATION, listOf("paper craft art", "paper sculpture"), "paper craft|paper sculpture"),
        recipe("papercut-art", "Papercut art", DiscoveryFamily.ILLUSTRATION, listOf("papercut art", "paper cut illustration"), "papercut|paper cut"),
        recipe("scientific-illustration", "Scientific illustration", DiscoveryFamily.SCIENTIFIC, listOf("scientific illustration", "natural history illustration"), "scientific illustration|natural history", artInstitutePhrase = "natural history", artInstituteEvidence = "natural history|scientific illustration|botanical"),
        recipe("celestial-art", "Celestial art", DiscoveryFamily.DIGITAL_WORLDS, listOf("space art planetary landscape", "space stars nebula"), "space|planet|astronomy|cosmic|nebula|stars", artInstitutePhrase = "astronomy", artInstituteEvidence = "astronomy|celestial|space|planet"),
        recipe("sci-fi-environments", "Sci-fi environments", DiscoveryFamily.DIGITAL_WORLDS, listOf("science fiction environment art", "sci fi landscape"), "science fiction|sci fi|scifi|futuristic"),
        recipe("experimental-typography", "Experimental typography", DiscoveryFamily.EXPERIMENTAL, listOf("experimental typography", "typographic art"), "typography|typographic"),
        recipe("unusual-texture-studies", "Unusual texture studies", DiscoveryFamily.PHOTOGRAPHY, listOf("abstract texture study", "unusual surface texture"), "texture study|surface texture"),
        recipe("frutiger-aero", "Frutiger Aero", DiscoveryFamily.AERO, listOf("frutiger aero", "aqua glass architecture"), "frutiger aero|aqua glass"),
        recipe("luminous-botanical-art", "Luminous botanical art", DiscoveryFamily.NATURE, listOf("luminous botanical art", "translucent flower art"), "botanical illustration|translucent flower"),
        recipe("frutiger-zen", "Frutiger Zen", DiscoveryFamily.AERO, listOf("frutiger zen", "serene water architecture"), "frutiger zen|serene water"),
        recipe("dark-aero", "Dark Aero", DiscoveryFamily.AERO, listOf("dark aero", "dark reflective glass cyan"), "dark aero|dark reflective glass"),
        recipe("art-nouveau-botanical", "Art Nouveau botanicals", DiscoveryFamily.ILLUSTRATION, listOf("art nouveau botanical illustration", "art nouveau flowers"), "art nouveau|botanical", artInstitutePhrase = "art nouveau", artInstituteEvidence = "art nouveau|botanical|flower"),
        recipe("woodcut-landscapes", "Woodcut landscapes", DiscoveryFamily.ILLUSTRATION, listOf("woodcut landscape art", "woodblock landscape print"), "woodcut|woodblock"),
        recipe("pixel-art-environments", "Pixel-art environments", DiscoveryFamily.DIGITAL_WORLDS, listOf("pixel art landscape", "pixel art environment"), "pixel art|pixelated"),
        recipe("color-inversion-art", "Colour inversion art", DiscoveryFamily.EXPERIMENTAL, listOf("color inversion art", "negative color photography art"), "color inversion|negative color"),
        recipe("silhouette-compositions", "Silhouette compositions", DiscoveryFamily.ILLUSTRATION, listOf("silhouette art composition", "graphic silhouette landscape"), "silhouette"),
        recipe("cyanotype-botanical", "Cyanotype botanicals", DiscoveryFamily.PHOTOGRAPHY, listOf("cyanotype botanical art", "cyanotype plant print"), "cyanotype"),
        recipe("miniature-architecture", "Miniature architecture", DiscoveryFamily.ARCHITECTURE, listOf("miniature architecture model", "architectural miniature"), "miniature architecture|architectural model"),
        recipe("environmental-concept-art", "Environmental concept art", DiscoveryFamily.DIGITAL_WORLDS, listOf("environmental concept art", "fantasy environment concept"), "environment concept|concept art"),
    )

    private val byId = recipes.associateBy { it.id }
    val allIds: Set<String> = byId.keys
    val artInstituteRecipeIds: Set<String> = recipes
        .filter { WallpaperSource.ART_INSTITUTE in it.sourceQueries }
        .mapTo(linkedSetOf()) { it.id }

    init {
        require(recipes.size >= 80) { "The internal discovery vocabulary must include at least 80 recipes." }
        require(byId.size == recipes.size) { "Discovery recipe IDs must be unique." }
    }

    fun find(id: String): DiscoveryRecipe? = byId[id]
}
