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
        DiscoveryCatalog.recipes.forEach { recipe ->
            assertTrue(recipe.id.matches(Regex("[a-z0-9][a-z0-9-]*")))
            assertTrue(recipe.sourceQueries[WallpaperSource.WALLHAVEN]!!.phrases.all(String::isNotBlank))
            assertTrue(recipe.sourceQueries[WallpaperSource.OPENVERSE]!!.phrases.all(String::isNotBlank))
            assertNotEquals(recipe.query(WallpaperSource.WALLHAVEN), recipe.query(WallpaperSource.OPENVERSE))
        }
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

    @Test fun explicitExclusionOfTheWholeCatalogueDoesNotSilentlyReintroduceRecipes() {
        val session = DiscoverySessionPlanner.newSession(
            seed = 19L,
            nowMillis = 1_800_000_000_000L,
            excludedRecipeIds = DiscoveryCatalog.allIds,
        )
        assertTrue(session.candidateRecipeIds.isEmpty())
    }

    @Test fun genericAbstractOrUploaderNameDoesNotValidateGeometricAbstraction() {
        val recipe = DiscoveryCatalog.find("abstract-geometry")!!
        val generic = wallpaper(id = "generic", tags = listOf(WallpaperTag(null, "abstract")))
        val creatorOnly = wallpaper(id = "creator", tags = emptyList()).copy(creatorName = "Geometric Studio")
        val relevant = wallpaper(id = "geometric", tags = listOf(WallpaperTag(null, "geometric")))

        assertFalse(recipe.isRelevant(generic))
        assertFalse(recipe.isRelevant(creatorOnly))
        assertTrue(recipe.isRelevant(relevant))
    }
}
