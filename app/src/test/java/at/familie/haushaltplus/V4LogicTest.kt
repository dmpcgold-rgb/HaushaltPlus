package at.familie.haushaltplus

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class V4LogicTest {
    @Test fun compatibleUnitsMergeWithoutRoundingErrors() {
        assertEquals("1.5", V4Logic.combine("1", "kg", "500", "g"))
        assertEquals("1500", V4Logic.combine("500", "g", "1", "kg"))
        assertEquals("2.25", V4Logic.combine("2", "l", "250", "ml"))
        assertNull(V4Logic.combine("1", "kg", "1", "l"))
        assertNull(V4Logic.combine("abc", "kg", "1", "g"))
    }
    @Test fun expirySupportsMissingAndPastDates() {
        val today = LocalDate.of(2026, 11, 7)
        assertEquals(3L, V4Logic.expiryDays(StockItem("x", "Milch", 1, 1, "l", "2026-11-10"), today))
        assertEquals(-1L, V4Logic.expiryDays(StockItem("x", "Milch", 1, 1, "l", "2026-11-06"), today))
        assertNull(V4Logic.expiryDays(StockItem("x", "Milch", 1, 1, "l", ""), today))
    }
    @Test fun fairAssignmentsPrioritizeFewestCompletedJobs() {
        val people = listOf(Member("1", "A"), Member("2", "B"))
        val tasks = listOf(HouseholdTask(id = "a", done = true, doneByName = "A"))
        assertEquals("B", V4Logic.fairAssignee(people, tasks))
    }
    @Test fun bestPriceUsesOnlyMatchingProducts() {
        val prices = listOf(
            PriceEntry("a", "Milch", "A", 1.40),
            PriceEntry("b", "Milch", "B", 1.10),
            PriceEntry("c", "Brot", "C", 0.60))
        assertEquals("B", V4Logic.priceCheapest("milch", prices)?.shop)
    }
}
