package at.familie.haushaltplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureNavigationTest {
    @Test fun shoppingFeaturesOpenUnderShopping() {
        listOf("templates", "receipts", "ocr", "prices", "budget").forEach {
            assertEquals(1, FeatureNavigation.targetTab(it))
        }
    }

    @Test fun tasksFeaturesOpenUnderTasks() {
        listOf("calendar", "checklists", "fairness").forEach {
            assertEquals(2, FeatureNavigation.targetTab(it))
        }
    }

    @Test fun pantryFeaturesOpenUnderPantry() {
        listOf("expiry", "recipes", "meals").forEach {
            assertEquals(3, FeatureNavigation.targetTab(it))
        }
    }

    @Test fun accountSettingsStayInMore() {
        listOf("family", "activity", "search", "notifications",
            "dashboard", "appearance", "backup", "trash", "account", "diagnostics").forEach {
            assertEquals(4, FeatureNavigation.targetTab(it))
        }
    }

    @Test fun eachFeatureHasExactlyOneHome() {
        val routes = FeatureNavigation.allRoutes()
        assertEquals(routes.size, routes.distinct().size)
        assertFalse(routes.isEmpty())
        assertNull(FeatureNavigation.targetTab("unknown"))
    }

    @Test fun mostUsedFeaturesNoLongerUnderMore() {
        val dailyFeatures = FeatureNavigation.shopping.map { it.id } +
            FeatureNavigation.tasks.map { it.id } +
            FeatureNavigation.stock.map { it.id }
        assertTrue(dailyFeatures.isNotEmpty())
        assertFalse(dailyFeatures.any { it in FeatureNavigation.familyAndSettings })
    }
}
