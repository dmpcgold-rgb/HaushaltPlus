package at.familie.haushaltplus

import org.json.JSONArray
import org.json.JSONObject

/**
 * User-controlled JSON export/import using Android's document picker.
 * No cloud upload, no external servers and no credentials in a backup.
 */
object BackupCodec {
    fun encode(data: HouseholdData): String {
        val root = JSONObject().put("format", "HaushaltPlus-backup")
            .put("version", 3).put("name", data.name).put("monthlyBudget", data.monthlyBudget)
        root.put("lists", JSONArray().apply { data.lists.forEach { x ->
            put(JSONObject().put("id", x.id).put("name", x.name))
        } })
        root.put("shopping", JSONArray().apply { data.shopping.forEach { x ->
            put(JSONObject().put("name", x.name).put("quantity", x.quantity).put("unit", x.unit)
                .put("category", x.category).put("price", x.price).put("listId", x.listId)
                .put("bought", x.bought).put("boughtAt", x.boughtAt).put("barcode", x.barcode))
        } })
        root.put("tasks", JSONArray().apply { data.tasks.forEach { x ->
            put(JSONObject().put("title", x.title).put("assignee", x.assignee)
                .put("due", x.due).put("repeat", x.repeat).put("rotate", x.rotate)
                .put("done", x.done))
        } })
        root.put("stock", JSONArray().apply { data.stock.forEach { x ->
            put(JSONObject().put("name", x.name).put("quantity", x.quantity)
                .put("minimum", x.minimum).put("unit", x.unit).put("expiresOn", x.expiresOn))
        } })
        root.put("meals", JSONArray().apply { data.meals.forEach { x ->
            put(JSONObject().put("date", x.date).put("slot", x.slot)
                .put("title", x.title).put("ingredients", x.ingredients))
        } })
        root.put("templates", JSONArray().apply { data.templates.forEach { x ->
            put(JSONObject().put("name", x.name)
                .put("itemsJson", V3Logic.itemsToJson(x.items)))
        } })
        root.put("recipes", JSONArray().apply { data.recipes.forEach { x ->
            put(JSONObject().put("name", x.name).put("portions", x.portions)
                .put("ingredients", x.ingredients))
        } })
        root.put("receipts", JSONArray().apply { data.receipts.forEach { x ->
            put(JSONObject().put("merchant", x.merchant).put("date", x.date)
                .put("total", x.total).put("note", x.note))
        } })
        return root.toString(2)
    }

    /** Import adds missing entries only, avoids destroying existing family data. */
    fun importInto(text: String, store: HouseholdStore, existing: HouseholdData): String {
        val root = try { JSONObject(text) } catch (_: Exception) { return "Ungültige JSON-Datei" }
        if (root.optString("format") != "HaushaltPlus-backup" ||
            !root.has("shopping") || !root.has("tasks") || !root.has("stock"))
            return "Kein gültiges Haushalt+-Backup"
        fun rows(name: String): List<JSONObject> {
            val a = root.optJSONArray(name) ?: JSONArray()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }
        var inserted = 0
        val knownLists = existing.lists.toMutableList()
        for (o in rows("lists")) {
            val name = o.optString("name").trim()
            if (name.isNotBlank() && knownLists.none { it.name.equals(name, true) }) {
                store.addShoppingList(name)
                knownLists.add(ShoppingList(name = name))
                inserted++
            }
        }
        // Shopping-list IDs are backend-specific: restore products to the default list.
        for (o in rows("shopping")) {
            val name = o.optString("name").trim()
            if (name.isBlank() || existing.shopping.any { !it.bought && it.name.equals(name, true) }) continue
            store.addShopping(name, o.optString("quantity", "1"), o.optString("unit", "Stk"),
                o.optString("category", "Sonstiges"), o.optDouble("price", 0.0), "standard")
            inserted++
        }
        for (o in rows("tasks")) {
            val title = o.optString("title").trim()
            if (title.isBlank() || existing.tasks.any { !it.done && it.title.equals(title, true) && it.due == o.optString("due") }) continue
            store.addTask(title, o.optString("assignee", "Alle"), o.optString("due"),
                o.optString("repeat", "Nie"), o.optBoolean("rotate"))
            inserted++
        }
        for (o in rows("stock")) {
            val name = o.optString("name").trim()
            if (name.isBlank() || existing.stock.any { it.name.equals(name, true) }) continue
            store.addStock(name, o.optInt("quantity").coerceAtLeast(0),
                o.optInt("minimum", 1).coerceAtLeast(0), o.optString("unit", "Stk"),
                o.optString("expiresOn"))
            inserted++
        }
        for (o in rows("meals")) {
            val date = o.optString("date")
            val slot = o.optString("slot", "Abendessen")
            val title = o.optString("title").trim()
            if (title.isBlank() || existing.meals.any { it.date == date && it.slot == slot && it.title.equals(title, true) }) continue
            store.addMeal(date, slot, title, o.optString("ingredients"))
            inserted++
        }
        for (o in rows("templates")) {
            val name = o.optString("name").trim()
            val products = V3Logic.itemsFromJson(o.optString("itemsJson", "[]"))
            if (name.isBlank() || products.isEmpty() ||
                existing.templates.any { it.name.equals(name, true) }) continue
            store.saveTemplate(name, products)
            inserted++
        }
        for (o in rows("recipes")) {
            val name = o.optString("name").trim()
            if (name.isBlank() || existing.recipes.any { it.name.equals(name, true) }) continue
            store.addRecipe(name, o.optInt("portions", 2), o.optString("ingredients"))
            inserted++
        }
        for (o in rows("receipts")) {
            val date = o.optString("date")
            val merchant = o.optString("merchant").trim()
            val value = o.optDouble("total", -1.0)
            if (merchant.isBlank() || value < 0 ||
                runCatching { java.time.LocalDate.parse(date) }.isFailure ||
                existing.receipts.any { it.merchant.equals(merchant, true) &&
                    it.date == date && it.total == value }) continue
            store.addReceipt(merchant, date, value, o.optString("note"))
            inserted++
        }
        return inserted.toString() + " Einträge ergänzt (ohne vorhandene Daten zu überschreiben)"
    }
}
