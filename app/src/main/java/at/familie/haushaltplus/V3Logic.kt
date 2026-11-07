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

    /**
     * Local, deterministic parsing after Android's on-device/system speech recognizer.
     * Confirmation in the UI is required before writing anything to the household.
     */
    fun voiceItems(spoken: String): List<TemplateProduct> {
        val words = mapOf("ein" to "1", "eine" to "1", "einen" to "1",
            "zwei" to "2", "drei" to "3", "vier" to "4", "fünf" to "5", "sechs" to "6",
            "sieben" to "7", "acht" to "8", "neun" to "9", "zehn" to "10")
        val units = mapOf("liter" to "l", "l" to "l", "kilo" to "kg", "kilogramm" to "kg",
            "kg" to "kg", "gramm" to "g", "g" to "g", "stück" to "Stk",
            "packung" to "Pkg", "packungen" to "Pkg", "flaschen" to "Fl",
            "flasche" to "Fl")
        return spoken.split(Regex("(?i)\\s*(?:,|\\bund\\b)\\s*"))
            .mapNotNull { part ->
                val tokens = part.trim().replace(Regex("(?i)\\b(hinzufügen|kaufen|bitte)\\b"), "")
                    .trim().split(Regex("\\s+"))
                if (tokens.isEmpty() || tokens[0].isBlank()) return@mapNotNull null
                val first = tokens[0].lowercase(java.util.Locale.GERMAN)
                val qty = first.toDoubleOrNull()?.toString()?.removeSuffix(".0")
                    ?: words[first]
                val idx = if (qty == null) 0 else 1
                val unit = tokens.getOrNull(idx)?.lowercase(java.util.Locale.GERMAN)
                val namedUnit = units[unit]
                val name = tokens.drop(idx + if (namedUnit == null) 0 else 1)
                    .joinToString(" ").trim().take(100)
                if (name.isBlank()) null else TemplateProduct(
                    name = name.replaceFirstChar { it.uppercase() },
                    quantity = qty ?: "1", unit = namedUnit ?: "Stk")
            }.take(25)
    }

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

    /** Combine ingredients across several recipes and subtract available matching stock. */
    fun missingIngredients(plans: List<Pair<RecipeEntry, Int>>, stock: List<StockItem>): List<TemplateProduct> {
        val all = plans.flatMap { (recipe, count) ->
            recipeItems(recipe.ingredients, recipe.portions, count)
        }
        return all.groupBy { it.name.trim().lowercase(java.util.Locale.GERMAN) + "|" + it.unit.lowercase() }
            .values.mapNotNull { group ->
                val item = group.first()
                val quantities = group.map { it.quantity.replace(',', '.').toDoubleOrNull() }
                if (quantities.any { it == null }) return@mapNotNull item
                val total = quantities.filterNotNull().sum()
                val owned = stock.filter { it.name.equals(item.name, true) &&
                    it.unit.equals(item.unit, true) }.sumOf { it.quantity }
                val missing = (total - owned).coerceAtLeast(0.0)
                if (missing <= 0.000001) null else {
                    val fmt = if (missing == missing.toLong().toDouble()) missing.toLong().toString()
                        else "%.2f".format(java.util.Locale.ROOT, missing).trimEnd('0').trimEnd('.')
                    item.copy(quantity = fmt)
                }
            }
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
