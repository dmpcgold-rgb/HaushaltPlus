package at.familie.haushaltplus

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.time.LocalDate
import java.time.YearMonth

/** Durable local mode; keeps the same SharedPreferences file and JSON keys as v0.1. */
class LocalStore(private val context: Context) : HouseholdStore {
    private val prefs = context.getSharedPreferences("haushalt_local_v1", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    override val data: StateFlow<HouseholdData> = mutable
    private fun id() = UUID.randomUUID().toString()

    private fun load(): HouseholdData {
        return try {
            val raw = prefs.getString("data", null) ?: return HouseholdData(
                id = "local", name = "Unser Zuhause", ownName = "Ich",
                members = listOf(Member("local", "Ich")), ownUid = "local"
            )
            val root = JSONObject(raw)
            fun <T> list(key: String, convert: (JSONObject) -> T): List<T> {
                val array = root.optJSONArray(key) ?: JSONArray()
                return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(convert) }
            }
            HouseholdData(
                id = "local", name = root.optString("name", "Unser Zuhause"),
                ownName = root.optString("ownName", "Ich"), members = listOf(Member("local", "Ich")),
                lists = listOf(ShoppingList()) + list("lists") { ShoppingList(it.optString("id"), it.optString("name")) },
                shopping = list("shopping") {
                    ShoppingItem(it.optString("id"), it.optString("name"), it.optString("quantity", "1"),
                        it.optString("unit", "Stk"), it.optString("category", "Sonstiges"),
                        it.optBoolean("bought"), it.optString("doneBy"), it.optString("doneByName"),
                        it.optDouble("price", 0.0), it.optString("listId", "standard"),
                        it.optString("boughtAt"), it.optString("barcode"), it.optBoolean("archived"))
                },
                tasks = list("tasks") {
                    HouseholdTask(it.optString("id"), it.optString("title"), it.optString("assignee", "Alle"),
                        it.optString("due"), it.optString("repeat", "Nie"), it.optBoolean("done"),
                        it.optString("doneBy"), it.optString("doneByName"), it.optBoolean("rotate"))
                },
                stock = list("stock") {
                    StockItem(it.optString("id"), it.optString("name"), it.optInt("quantity"),
                        it.optInt("minimum", 1), it.optString("unit", "Stk"))
                },
                meals = list("meals") {
                    MealEntry(it.optString("id"), it.optString("date"),
                        it.optString("slot", "Abendessen"), it.optString("title"), it.optString("ingredients"))
                },
                monthlyBudget = root.optDouble("monthlyBudget", 0.0).coerceAtLeast(0.0),
                templates = list("templates") {
                    ShoppingTemplate(it.optString("id"), it.optString("name"),
                        V3Logic.itemsFromJson(it.optString("itemsJson", "[]")))
                },
                recipes = list("recipes") {
                    RecipeEntry(it.optString("id"), it.optString("name"),
                        it.optInt("portions", 2), it.optString("ingredients"))
                },
                receipts = list("receipts") {
                    ReceiptEntry(it.optString("id"), it.optString("merchant"),
                        it.optString("date"), it.optDouble("total"), it.optString("note"))
                },
                stockUsage = list("stockUsage") {
                    StockUsage(it.optString("id"), it.optString("stockId"),
                        it.optString("date"), it.optInt("quantity"))
                },
                activity = list("activity") {
                    ActivityEntry(it.optString("id"), it.optString("actor"), it.optString("title"),
                        it.optString("kind"), it.optLong("createdAt"))
                },
                syncStatus = "Lokal gespeichert"
            )
        } catch (_: Exception) {
            HouseholdData(id = "local", name = "Unser Zuhause",
                members = listOf(Member("local", "Ich")), error = "Lokale Daten konnten nicht gelesen werden")
        }
    }
    private fun save(next: HouseholdData) {
        val root = JSONObject().put("name", next.name).put("ownName", next.ownName)
            .put("monthlyBudget", next.monthlyBudget).put("schemaVersion", 3)
        root.put("lists", JSONArray().apply {
            next.lists.filterNot { it.id == "standard" }.forEach { v ->
                put(JSONObject().put("id",v.id).put("name",v.name))
            }
        })
        root.put("shopping", JSONArray().apply {
            next.shopping.forEach { v ->
                put(JSONObject().put("id",v.id).put("name",v.name).put("quantity",v.quantity)
                    .put("unit",v.unit).put("category",v.category).put("bought",v.bought)
                    .put("doneBy",v.doneBy).put("doneByName",v.doneByName).put("price",v.price)
                    .put("listId",v.listId).put("boughtAt",v.boughtAt).put("barcode",v.barcode)
                    .put("archived",v.archived))
            }
        })
        root.put("tasks", JSONArray().apply {
            next.tasks.forEach { v ->
                put(JSONObject().put("id",v.id).put("title",v.title).put("assignee",v.assignee)
                    .put("due",v.due).put("repeat",v.repeat).put("done",v.done)
                    .put("doneBy",v.doneBy).put("doneByName",v.doneByName).put("rotate",v.rotate))
            }
        })
        root.put("stock", JSONArray().apply {
            next.stock.forEach { v ->
                put(JSONObject().put("id",v.id).put("name",v.name).put("quantity",v.quantity)
                    .put("minimum",v.minimum).put("unit",v.unit))
            }
        })
        root.put("meals", JSONArray().apply {
            next.meals.forEach { v ->
                put(JSONObject().put("id",v.id).put("date",v.date).put("slot",v.slot)
                    .put("title",v.title).put("ingredients",v.ingredients))
            }
        })
        root.put("templates", JSONArray().apply {
            next.templates.forEach { v -> put(JSONObject().put("id",v.id).put("name",v.name)
                .put("itemsJson",V3Logic.itemsToJson(v.items))) }
        })
        root.put("recipes", JSONArray().apply {
            next.recipes.forEach { v -> put(JSONObject().put("id",v.id).put("name",v.name)
                .put("portions",v.portions).put("ingredients",v.ingredients)) }
        })
        root.put("receipts", JSONArray().apply {
            next.receipts.forEach { v -> put(JSONObject().put("id",v.id).put("merchant",v.merchant)
                .put("date",v.date).put("total",v.total).put("note",v.note)) }
        })
        root.put("stockUsage", JSONArray().apply {
            next.stockUsage.takeLast(1000).forEach { v -> put(JSONObject().put("id",v.id)
                .put("stockId",v.stockId).put("date",v.date).put("quantity",v.quantity)) }
        })
        root.put("activity", JSONArray().apply {
            next.activity.takeLast(120).forEach { v -> put(JSONObject().put("id",v.id)
                .put("actor",v.actor).put("title",v.title).put("kind",v.kind)
                .put("createdAt",v.createdAt)) }
        })
        prefs.edit().putString("data",root.toString()).apply()
        mutable.value = next.copy(syncStatus = "Lokal gespeichert")
    }
    private fun mutate(fn: (HouseholdData) -> HouseholdData) = save(fn(mutable.value))
    private fun withEvent(h: HouseholdData, title: String, kind: String): HouseholdData =
        h.copy(activity = (h.activity + ActivityEntry(id(), h.ownName, title,
            kind, System.currentTimeMillis())).takeLast(120))

    override fun addShopping(name: String, quantity: String, unit: String, category: String, price: Double, listId: String) =
        mutate { h ->
            val initial = withEvent(h, "Artikel hinzugefügt: " + name.trim(), "shopping")
            val duplicate = initial.shopping.firstOrNull {
                !it.bought && it.listId == listId && it.name.equals(name.trim(), ignoreCase = true)
                    && it.unit.equals(unit, ignoreCase = true)
            }
            val oldQty = duplicate?.quantity?.replace(',', '.')?.toDoubleOrNull()
            val newQty = quantity.replace(',', '.').toDoubleOrNull()
            if (duplicate != null && oldQty != null && newQty != null) {
                val sum = oldQty + newQty
                val qty = if (sum == sum.toLong().toDouble()) sum.toLong().toString() else sum.toString()
                initial.copy(shopping = initial.shopping.map {
                    if (it.id == duplicate.id) it.copy(quantity = qty, price = it.price + price) else it
                })
            } else {
                initial.copy(shopping = initial.shopping + ShoppingItem(id(), name.trim(), quantity, unit, category, price = price, listId = listId))
            }
        }
    override fun addShoppingList(name: String) =
        mutate { it.copy(lists = it.lists + ShoppingList(id(), name.trim())) }
    override fun renameShoppingList(list: ShoppingList, name: String) =
        mutate { h -> h.copy(lists = h.lists.map { if (it.id == list.id && it.id != "standard") it.copy(name = name.trim()) else it }) }
    override fun updateShopping(item: ShoppingItem) =
        mutate { h -> h.copy(shopping = h.shopping.map { if (it.id == item.id) item else it }) }
    override fun setShoppingDone(item: ShoppingItem, done: Boolean) = mutate { h ->
        val source = h.shopping.firstOrNull { it.id == item.id }
        if (source == null || source.bought == done) h else withEvent(h,
            if (done) "Eingekauft: " + source.name else "Wieder geöffnet: " + source.name,
            "shopping").copy(shopping = h.shopping.map {
            if(it.id == item.id) it.copy(bought = done, doneBy = if(done) "local" else "",
                doneByName = if(done) "Ich" else "",
                boughtAt = if(done) LocalDate.now().toString() else "",
                archived = if (done) it.archived else false) else it
        })
    }
    override fun archiveCompletedShopping(listId: String) = mutate { h ->
        h.copy(shopping = h.shopping.map { item ->
            if (item.listId == listId && item.bought) item.copy(archived = true) else item
        })
    }
    override fun deleteShopping(item: ShoppingItem) =
        mutate { it.copy(shopping = it.shopping.filterNot { s -> s.id == item.id }) }
    override fun addTask(title: String, assignee: String, due: String, repeat: String, rotate: Boolean) =
        mutate { it.copy(tasks = it.tasks + HouseholdTask(id(), title.trim(), assignee, due, repeat, rotate = rotate)) }
    override fun updateTask(task: HouseholdTask) =
        mutate { h -> h.copy(tasks = h.tasks.map { if (it.id == task.id) task else it }) }
    override fun setTaskDone(item: HouseholdTask, done: Boolean) = mutate { h ->
        // Use the current persisted task, not a possibly stale checkbox callback.
        val current = h.tasks.firstOrNull { it.id == item.id }
        if (current == null || current.done == done) {
            h
        } else {
            val updated = h.tasks.map {
                if (it.id == current.id) it.copy(done = done, doneBy = if (done) "local" else "",
                    doneByName = if (done) "Ich" else "") else it
            }
            val due = if (done) TaskPlanner.nextDue(current.due, current.repeat) else null
            val next = if (due != null) {
                listOf(current.copy(id = id(), due = due,
                    assignee = TaskPlanner.nextAssignee(current.assignee, current.rotate, h.members.map { it.name }),
                    done = false, doneBy = "", doneByName = ""))
            } else emptyList()
            withEvent(h, if(done) "Aufgabe erledigt: " + current.title else
                "Aufgabe wieder geöffnet: " + current.title, "tasks").copy(tasks = updated + next)
        }
    }
    override fun deleteTask(item: HouseholdTask) =
        mutate { it.copy(tasks = it.tasks.filterNot { t -> t.id == item.id }) }
    override fun addStock(name: String, quantity: Int, minimum: Int, unit: String) =
        mutate { h ->
            val need = quantity <= minimum &&
                h.shopping.none { !it.bought && it.name.equals(name.trim(), ignoreCase = true) }
            h.copy(
                stock = h.stock + StockItem(id(), name.trim(), quantity, minimum, unit),
                shopping = if (need) h.shopping + ShoppingItem(id(), name.trim(), "1", unit, "Vorrat")
                    else h.shopping
            )
        }
    override fun updateStock(item: StockItem) =
        mutate { h ->
            val need = item.quantity <= item.minimum &&
                h.shopping.none { !it.bought && it.name.equals(item.name, ignoreCase = true) }
            h.copy(
                stock = h.stock.map { if(it.id == item.id) item else it },
                shopping = if (need) h.shopping + ShoppingItem(id(), item.name, "1", item.unit, "Vorrat")
                    else h.shopping
            )
        }
    override fun changeStock(item: StockItem, amount: Int) = mutate { h ->
        val current = h.stock.firstOrNull { it.id == item.id } ?: return@mutate h
        val newQuantity = (current.quantity + amount).coerceAtLeast(0)
        val used = (current.quantity - newQuantity).coerceAtLeast(0)
        val newlyLow = current.quantity > current.minimum && newQuantity <= current.minimum
        val alreadyListed = h.shopping.any { !it.bought && it.name.equals(item.name, ignoreCase = true) }
        val changed = h.copy(stock = h.stock.map { if(it.id == item.id) it.copy(quantity = newQuantity) else it },
            stockUsage = if (used > 0) (h.stockUsage + StockUsage(id(), item.id,
                LocalDate.now().toString(), used)).takeLast(1000) else h.stockUsage,
            shopping = if(newlyLow && !alreadyListed) h.shopping + ShoppingItem(
                id(), item.name, "1", item.unit, "Vorrat") else h.shopping)
        withEvent(changed, item.name + ": Bestand " + newQuantity, "stock")
    }
    override fun deleteStock(item: StockItem) =
        mutate { it.copy(stock = it.stock.filterNot { s -> s.id == item.id }) }
    override fun setBudget(value: Double) = mutate { it.copy(monthlyBudget = value.coerceAtLeast(0.0)) }
    override fun addMeal(date: String, slot: String, title: String, ingredients: String) =
        mutate { it.copy(meals = it.meals + MealEntry(id(), date, slot, title.trim(), ingredients.trim())) }
    override fun deleteMeal(item: MealEntry) =
        mutate { it.copy(meals = it.meals.filterNot { m -> m.id == item.id }) }
    override fun saveTemplate(name: String, products: List<TemplateProduct>) = mutate { h ->
        h.copy(templates = h.templates + ShoppingTemplate(id(), name.trim(), products.take(100)))
    }
    override fun deleteTemplate(template: ShoppingTemplate) = mutate { h ->
        h.copy(templates = h.templates.filterNot { it.id == template.id })
    }
    override fun addReceipt(merchant: String, date: String, total: Double, note: String) = mutate { h ->
        h.copy(receipts = h.receipts + ReceiptEntry(id(), merchant.trim(), date, total.coerceAtLeast(0.0), note))
    }
    override fun deleteReceipt(item: ReceiptEntry) = mutate { h ->
        h.copy(receipts = h.receipts.filterNot { it.id == item.id })
    }
    override fun addRecipe(name: String, portions: Int, ingredients: String) = mutate { h ->
        h.copy(recipes = h.recipes + RecipeEntry(id(), name.trim(), portions.coerceAtLeast(1), ingredients))
    }
    override fun deleteRecipe(item: RecipeEntry) = mutate { h ->
        h.copy(recipes = h.recipes.filterNot { it.id == item.id })
    }
    override fun removeMember(uid: String) {}
    override fun transferOwnership(uid: String, reply: (String) -> Unit) = reply("Nur mit Cloud-Konto möglich")
    override fun revokeInvite(code: String, reply: (String) -> Unit) = reply("Nur mit Cloud-Konto möglich")
    override fun createInvite(reply: (String) -> Unit) = reply("Freigabe ist nur mit Cloud-Anmeldung möglich")

    /** Export and restore preserve IDs, dates, meal plans and budgets. Invalid JSON cannot replace data. */
    fun exportJson(): String = prefs.getString("data",null) ?: "{}"
    fun importJson(text: String): Boolean = try {
        val obj = JSONObject(text)
        if (!obj.has("shopping") || !obj.has("tasks") || !obj.has("stock")) false
        else {
            prefs.edit().putString("data",obj.toString()).commit()
            mutable.value = load()
            mutable.value.error.isBlank()
        }
    } catch (_: Exception) { false }
}
