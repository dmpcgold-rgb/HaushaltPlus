package at.familie.haushaltplus

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Pure family-household calculations; no remote service or API keys. */
object V4Logic {
    private val units = mapOf(
        "kg" to ("g" to 1000.0), "g" to ("g" to 1.0),
        "l" to ("ml" to 1000.0), "ml" to ("ml" to 1.0),
        "stk" to ("stk" to 1.0), "stück" to ("stk" to 1.0),
        "fl" to ("fl" to 1.0), "flasche" to ("fl" to 1.0),
        "pkg" to ("pkg" to 1.0), "packung" to ("pkg" to 1.0)
    )
    fun amount(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 }

    /** Returns a total in the original unit (or null for incompatible quantities). */
    fun combine(old: String, oldUnit: String, added: String, newUnit: String): String? {
        val a = amount(old) ?: return null
        val b = amount(added) ?: return null
        val oldDef = units[oldUnit.lowercase(Locale.GERMAN)] ?: return null
        val newDef = units[newUnit.lowercase(Locale.GERMAN)] ?: return null
        if (oldDef.first != newDef.first) return null
        val result = a + b * newDef.second / oldDef.second
        return format(result)
    }

    fun format(value: Double): String {
        if (!value.isFinite()) return "0"
        if (value == value.toLong().toDouble()) return value.toLong().toString()
        return "%.3f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }
    fun expiryDays(item: StockItem, today: LocalDate = LocalDate.now()): Long? {
        val expiry = runCatching { LocalDate.parse(item.expiresOn) }.getOrNull() ?: return null
        return ChronoUnit.DAYS.between(today, expiry)
    }
    fun fairAssignee(members: List<Member>, tasks: List<HouseholdTask>): String {
        val names = members.map { it.name }.filter { it.isNotBlank() }.distinct()
        if (names.isEmpty()) return "Alle"
        val completed = tasks.filter { it.done }.groupingBy { it.doneByName }.eachCount()
        val current = tasks.filter { !it.done }.groupingBy { it.assignee }.eachCount()
        return names.minWithOrNull(compareBy<String> { completed[it] ?: 0 }
            .thenBy { current[it] ?: 0 }.thenBy { names.indexOf(it) }) ?: names[0]
    }
    fun priceCheapest(product: String, entries: List<PriceEntry>): PriceEntry? =
        entries.filter { it.product.equals(product, true) && it.unitPrice > 0 }
            .minByOrNull { it.unitPrice }
}

/** Explicit schema-whitelisted snapshots for the 30-day recycle bin. */
object V4TrashCodec {
    fun shopping(x: ShoppingItem) = JSONObject().put("id", x.id).put("name", x.name)
        .put("quantity", x.quantity).put("unit", x.unit).put("category", x.category)
        .put("price", x.price).put("listId", x.listId).put("bought", x.bought)
        .put("boughtAt", x.boughtAt).put("archived", x.archived).toString()

    fun task(x: HouseholdTask) = JSONObject().put("id", x.id).put("title", x.title)
        .put("assignee", x.assignee).put("due", x.due).put("repeat", x.repeat)
        .put("rotate", x.rotate).put("done", x.done).toString()

    fun recipe(x: RecipeEntry) = JSONObject().put("id", x.id).put("name", x.name)
        .put("portions", x.portions).put("ingredients", x.ingredients).toString()

    fun stock(x: StockItem) = JSONObject().put("id", x.id).put("name", x.name)
        .put("quantity", x.quantity).put("minimum", x.minimum)
        .put("unit", x.unit).put("expiresOn", x.expiresOn).toString()

    fun restoreShopping(s: String): ShoppingItem {
        val x = JSONObject(s)
        return ShoppingItem(id = x.optString("id"), name = x.optString("name"),
            quantity = x.optString("quantity", "1"), unit = x.optString("unit", "Stk"),
            category = x.optString("category", "Sonstiges"), price = x.optDouble("price", 0.0),
            listId = x.optString("listId", "standard"), bought = x.optBoolean("bought"),
            boughtAt = x.optString("boughtAt"), archived = x.optBoolean("archived"))
    }
    fun restoreTask(s: String): HouseholdTask {
        val x = JSONObject(s)
        return HouseholdTask(id = x.optString("id"), title = x.optString("title"),
            assignee = x.optString("assignee", "Alle"), due = x.optString("due"),
            repeat = x.optString("repeat", "Nie"), rotate = x.optBoolean("rotate"),
            done = x.optBoolean("done"))
    }
    fun restoreRecipe(s: String): RecipeEntry {
        val x = JSONObject(s)
        return RecipeEntry(id = x.optString("id"), name = x.optString("name"),
            portions = x.optInt("portions", 2), ingredients = x.optString("ingredients"))
    }
    fun restoreStock(s: String): StockItem {
        val x = JSONObject(s)
        return StockItem(id = x.optString("id"), name = x.optString("name"),
            quantity = x.optInt("quantity"), minimum = x.optInt("minimum", 1),
            unit = x.optString("unit", "Stk"), expiresOn = x.optString("expiresOn"))
    }
    fun isExpired(x: TrashEntry, now: Long = System.currentTimeMillis()) =
        x.deletedAt > 0 && now - x.deletedAt >= 30L * 24L * 60L * 60L * 1000L
}
