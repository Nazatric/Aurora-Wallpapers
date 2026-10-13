package com.auroro.wallpapers.core.model

import com.auroro.wallpapers.wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryCatalogTest {
    @Test fun catalogueHasAtLeastEightyDistinctProviderMappedRecipes() {
        assertTrue(DiscoveryCatalog.recipes.size >= 80)
        assertEquals(DiscoveryCatalog.recipes.size, DiscoveryCatalog.allIds.size)
        // The Art Institute lane was retired in 1.3.0; no recipe may map it anymore.
        assertTrue(DiscoveryCatalog.recipes.none { WallpaperSource.ART_INSTITUTE in it.sourceQueries })
        DiscoveryCatalog.recipes.forEach { recipe ->
            assertTrue(recipe.id.matches(Regex("[a-z0-9][a-z0-9-]*")))
            assertTrue(WallpaperSource.WALLHAVEN in recipe.sourceQueries)
            assertTrue(recipe.sourceQueries.getValue(WallpaperSource.WALLHAVEN).phrases.all(String::isNotBlank))
        }
    }

    @Test fun userFacingTopicsMapToExistingRecipesWithoutTheRetiredFlowerName() {
        val botanicalRecipes = DiscoveryStylePreferenceMapping.recipeIds(setOf(DiscoveryStyle.BOTANICAL_ART.id))

        assertTrue("botanical-illustration" in botanicalRecipes)
        assertTrue("luminous-botanical-art" in botanicalRecipes)
        assertTrue(botanicalRecipes.all { it in DiscoveryCatalog.allIds })
        assertFalse("frutiger-flower" in DiscoveryCatalog.allIds)
        assertEquals("Botanical art", DiscoveryStyle.BOTANICAL_ART.label)
        assertFalse(DiscoveryStyle.entries.any { it.label == "Frutiger Flower" })
    }

    @Test fun sessionPlannerIsRepeatableForASeedAndVariesForANewSession() {
        val now = 1_800_000_000_000L
        val first = DiscoverySessionPlanner.newSession(seed = 271828L, nowMillis = now)
        assertEquals(first, DiscoverySessionPlanner.newSession(seed = 271828L, nowMillis = now))
        val next = DiscoverySessionPlanner.newSession(seed = 314159L, nowMillis = now)
        assertNotEquals(first.candidateRecipeIds, next.candidateRecipeIds)
        assertEquals(12, first.candidateRecipeIds.size)
        assertTrue(first.recipes.map { it.family }.distinct().size >= 8)
        assertEquals(DiscoveryRotation.bucketAt(now), first.hourBucket)
    }

    @Test fun recentlyUsedAndExcludedRecipesAreAvoidedWhenTheRemainingCatalogueIsLargeEnough() {
        val recent = setOf("fractal-art", "vaporwave", "botanical-illustration")
        val excluded = setOf("glitch-art", "brutalist-architecture")
        val session = DiscoverySessionPlanner.newSession(
            seed = 731L,
            nowMillis = 1_800_000_000_000L,
            recentRecipeIds = recent,
            excludedRecipeIds = excluded,
        )
        assertTrue(session.candidateRecipeIds.none { it in recent })
        assertTrue(session.candidateRecipeIds.none { it in excluded })
    }

    @Test fun eachLargeSessionIncludesANonrecentQualityAnchorAndStillVariesItsMix() {
        val anchors = setOf("abstract-geometry", "fractal-art", "frutiger-aero", "space-art", "surrealism", "vaporwave")
        val first = DiscoverySessionPlanner.newSession(seed = 11L, nowMillis = 1_800_000_000_000L)
        val next = DiscoverySessionPlanner.newSession(seed = 12L, nowMillis = 1_800_000_000_000L)
        val recent = setOf("abstract-geometry", "fractal-art", "frutiger-aero", "space-art", "surrealism")
        val afterRecent = DiscoverySessionPlanner.newSession(
            seed = 13L,
            nowMillis = 1_800_000_000_000L,
            recentRecipeIds = recent,
        )

        assertTrue(first.candidateRecipeIds.any { it in anchors })
        assertTrue(next.candidateRecipeIds.any { it in anchors })
        assertNotEquals(first.candidateRecipeIds, next.candidateRecipeIds)
        assertTrue(afterRecent.candidateRecipeIds.none { it in recent })
        assertTrue(afterRecent.candidateRecipeIds.any { it in anchors - recent })
    }

    @Test fun explicitExclusionOfTheWholeCatalogueDoesNotSilentlyReintroduceRecipes() {
        val session = DiscoverySessionPlanner.newSession(
            seed = 19L,
            nowMillis = 1_800_000_000_000L,
            excludedRecipeIds = DiscoveryCatalog.allIds,
        )
        assertTrue(session.candidateRecipeIds.isEmpty())
    }

    @Test fun metadataEvidenceOnlyRanksResultsAndNeverRejectsMissingOrGenericLabels() {
        val recipe = DiscoveryCatalog.find("abstract-geometry")!!
        val generic = wallpaper(id = "generic", tags = listOf(WallpaperTag(null, "abstract")))
        val creatorOnly = wallpaper(id = "creator", tags = emptyList()).copy(creatorName = "Geometric Studio")
        val relevant = wallpaper(id = "geometric", tags = listOf(WallpaperTag(null, "geometric")))

        assertFalse(recipe.isRelevant(generic))
        assertFalse(recipe.isRelevant(creatorOnly))
        assertTrue(recipe.isRelevant(relevant))
        val ranked = DiscoveryQuality.rank(listOf(generic, creatorOnly, relevant), recipe)
        assertEquals(setOf(generic.key, creatorOnly.key, relevant.key), ranked.map { it.key }.toSet())
        assertEquals(relevant.key, ranked.first().key)
    }
}
