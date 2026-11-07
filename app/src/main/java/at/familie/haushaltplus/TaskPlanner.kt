package at.familie.haushaltplus

import java.time.LocalDate

/** Rules shared by local/cloud tasks, free of Android and Firebase dependencies. */
object TaskPlanner {
    fun nextDue(due: String, repeat: String, today: LocalDate = LocalDate.now()): String? {
        if (repeat !in setOf("Täglich", "Wöchentlich", "Monatlich")) return null
        val parsed = runCatching { LocalDate.parse(due) }.getOrNull()
        val base = if (parsed != null && parsed.isAfter(today)) parsed else today
        val next = when (repeat) {
            "Täglich" -> base.plusDays(1)
            "Wöchentlich" -> base.plusWeeks(1)
            else -> base.plusMonths(1)
        }
        return next.toString()
    }

    fun nextAssignee(current: String, rotate: Boolean, names: List<String>): String {
        if (!rotate) return current
        val eligible = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (eligible.isEmpty()) return current
        val index = eligible.indexOf(current)
        return eligible[(index + 1) % eligible.size]
    }
}
