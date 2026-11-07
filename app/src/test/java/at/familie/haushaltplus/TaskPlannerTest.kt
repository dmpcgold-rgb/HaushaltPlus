package at.familie.haushaltplus

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskPlannerTest {
    private val today = LocalDate.of(2026, 11, 7)

    @Test fun dailyFromToday() {
        assertEquals("2026-11-08", TaskPlanner.nextDue("2026-11-07", "Täglich", today))
    }

    @Test fun missedWeeklyTaskDoesNotRepeatInPast() {
        assertEquals("2026-11-14", TaskPlanner.nextDue("2026-10-01", "Wöchentlich", today))
    }

    @Test fun futureMonthlyAndLeapYear() {
        assertEquals("2024-02-29", TaskPlanner.nextDue("2024-01-31", "Monatlich", LocalDate.of(2024, 1, 1)))
    }

    @Test fun emptyDateFallsBackToToday() {
        assertEquals("2026-12-07", TaskPlanner.nextDue("", "Monatlich", today))
    }

    @Test fun noneDoesNotCreateFollowUp() {
        assertNull(TaskPlanner.nextDue("2026-11-07", "Nie", today))
    }

    @Test fun nextAssigneeRotatesAndWraps() {
        assertEquals("B", TaskPlanner.nextAssignee("A", true, listOf("A", "B", "C")))
        assertEquals("A", TaskPlanner.nextAssignee("C", true, listOf("A", "B", "C")))
        assertEquals("A", TaskPlanner.nextAssignee("Unbekannt", true, listOf("A", "B")))
        assertEquals("B", TaskPlanner.nextAssignee("B", false, listOf("A", "B")))
    }
}
