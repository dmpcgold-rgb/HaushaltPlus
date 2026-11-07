package at.familie.haushaltplus

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class V3LogicTest {
    @Test fun voiceDetectsGermanQuantityAndUnit() {
        val items = V3Logic.voiceItems("Zwei Liter Milch und sechs Eier hinzufügen")
        assertEquals(2, items.size)
        assertEquals(TemplateProduct("Milch", "2", "l", "Lebensmittel"), items[0])
        assertEquals(TemplateProduct("Eier", "6", "Stk", "Lebensmittel"), items[1])
    }
    @Test fun recipeScalesPortions() {
        val items = V3Logic.recipeItems("2|kg|Kartoffeln\n1|l|Milch", 2, 4)
        assertEquals("4", items[0].quantity)
        assertEquals("2", items[1].quantity)
    }
    @Test fun usageEstimateUsesPastTwoWeeks() {
        val stock = StockItem("s1", "Milch", 10, 1, "l")
        val today = LocalDate.of(2026, 11, 7)
        val usage = listOf(StockUsage("u1", "s1", today.minusDays(5).toString(), 14))
        assertEquals(10, V3Logic.daysRemaining(stock, usage, today))
        assertNull(V3Logic.daysRemaining(stock, emptyList(), today))
    }
    @Test fun emptyVoiceDoesNotWrite() {
        assertTrue(V3Logic.voiceItems("").isEmpty())
    }
}
