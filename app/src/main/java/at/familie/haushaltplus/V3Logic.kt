package at.familie.haushaltplus

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object V3Logic {
    fun itemsToJson(items: List<TemplateProduct>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("name", it.name).put("quantity", it.quantity)
            .put("unit", it.unit).put("category", it.category)) }
    }.toString()

    fun itemsFromJson(json: String): List<TemplateProduct> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let {
            TemplateProduct(it.optString("name"), it.optString("quantity", "1"),
                it.optString("unit", "Stk"), it.optString("category", "Lebensmittel"))
        } }.filter { it.name.isNotBlank() }
    }.getOrDefault(emptyList())

    /** Ingredient lines are '2|kg|Kartoffeln' or 'Kartoffeln' (one per line). */
    fun recipeItems(ingredients: String, portions: Int, desired: Int): List<TemplateProduct> =
        ingredients.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split('|').map { it.trim() }
            val amount = parts.getOrNull(0)?.replace(',', '.')?.toDoubleOrNull()
            if (parts.size >= 3 && amount != null && parts[2].isNotBlank()) {
                val total = amount * desired.coerceAtLeast(1) / portions.coerceAtLeast(1)
                val q = if (total == total.toLong().toDouble()) total.toLong().toString()
                    else "%.2f".format(java.util.Locale.ROOT, total).trimEnd('0').trimEnd('.')
                TemplateProduct(parts[2], q, parts[1].ifBlank { "Stk" })
            } else line.trim().takeIf { it.isNotBlank() }?.let { TemplateProduct(it) }
        }

    fun daysRemaining(stock: StockItem, history: List<StockUsage>, today: LocalDate = LocalDate.now()): Int? {
        val week = history.filter {
            it.stockId == stock.id && it.quantity > 0 &&
                runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it.date), today) }.getOrDefault(100L) in 0..13
        }.sumOf { it.quantity }
        if (week <= 0) return null
        return ((stock.quantity.toDouble() * 14.0 / week)).toInt().coerceAtLeast(0)
    }
}
