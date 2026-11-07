package at.familie.haushaltplus

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.time.LocalDate

/** Private, durable local mode. No cross-device sharing or remote push in this mode. */
class LocalStore(context: Context) : HouseholdStore {
    private val prefs = context.getSharedPreferences("haushalt_local_v1", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    override val data: StateFlow<HouseholdData> = mutable

    private fun id() = UUID.randomUUID().toString()
    private fun load(): HouseholdData {
        return try {
        val raw = prefs.getString("data", null) ?: return HouseholdData(
            id = "local", name = "Unser Zuhause", ownName = "Ich", members = listOf(Member("local", "Ich"))
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
                    it.optDouble("price", 0.0), it.optString("listId", "standard"))
            },
            tasks = list("tasks") {
                HouseholdTask(it.optString("id"), it.optString("title"), it.optString("assignee", "Alle"),
                    it.optString("due"), it.optString("repeat", "Nie"), it.optBoolean("done"),
                    it.optString("doneBy"), it.optString("doneByName"))
            },
            stock = list("stock") {
                StockItem(it.optString("id"), it.optString("name"), it.optInt("quantity"),
                    it.optInt("minimum", 1), it.optString("unit", "Stk"))
            }
        )
    } catch (_: Exception) {
        HouseholdData(id = "local", name = "Unser Zuhause", members = listOf(Member("local", "Ich")), error = "Lokale Daten konnten nicht gelesen werden")
    }
    }

    private fun save(next: HouseholdData) {
        val root = JSONObject().put("name", next.name).put("ownName", next.ownName)
        root.put("lists", JSONArray().apply {
            next.lists.filterNot { it.id == "standard" }.forEach { l -> put(JSONObject().put("id",l.id).put("name",l.name)) }
        })
        root.put("shopping", JSONArray().apply {
            next.shopping.forEach { s -> put(JSONObject().put("id",s.id).put("name",s.name).put("quantity",s.quantity)
                .put("unit",s.unit).put("category",s.category).put("bought",s.bought)
                .put("doneBy",s.doneBy).put("doneByName",s.doneByName).put("price",s.price).put("listId",s.listId)) }
        })
        root.put("tasks", JSONArray().apply {
            next.tasks.forEach { t -> put(JSONObject().put("id",t.id).put("title",t.title)
                .put("assignee",t.assignee).put("due",t.due).put("repeat",t.repeat).put("done",t.done)
                .put("doneBy",t.doneBy).put("doneByName",t.doneByName)) }
        })
        root.put("stock", JSONArray().apply {
            next.stock.forEach { s -> put(JSONObject().put("id",s.id).put("name",s.name)
                .put("quantity",s.quantity).put("minimum",s.minimum).put("unit",s.unit)) }
        })
        // Apply is async and crash-safe at the preference-file level.
        prefs.edit().putString("data", root.toString()).apply()
        mutable.value = next
    }
    private fun mutate(fn: (HouseholdData) -> HouseholdData) = save(fn(mutable.value))
    override fun addShopping(name: String, quantity: String, unit: String, category: String, price: Double, listId: String) =
        mutate { it.copy(shopping = it.shopping + ShoppingItem(id(), name, quantity, unit, category, price = price, listId = listId)) }
    override fun addShoppingList(name: String) = mutate { it.copy(lists = it.lists + ShoppingList(id(), name.trim())) }
    override fun setShoppingDone(item: ShoppingItem, done: Boolean) = mutate { h ->
        h.copy(shopping = h.shopping.map { if(it.id == item.id) it.copy(bought = done, doneBy = if(done) "local" else "", doneByName = if(done) "Ich" else "") else it })
    }
    override fun deleteShopping(item: ShoppingItem) = mutate { it.copy(shopping = it.shopping.filterNot { s -> s.id == item.id }) }
    override fun addTask(title: String, assignee: String, due: String, repeat: String) = mutate {
        it.copy(tasks = it.tasks + HouseholdTask(id(), title, assignee, due, repeat))
    }
    override fun setTaskDone(item: HouseholdTask, done: Boolean) = mutate { h ->
        val updated = h.tasks.map { if(it.id == item.id) it.copy(done = done, doneBy = if(done) "local" else "", doneByName = if(done) "Ich" else "") else it }
        val next = if (done && !item.done && item.repeat != "Nie") try {
            val date = item.due.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now()
            val future = when(item.repeat) { "Täglich" -> date.plusDays(1); "Wöchentlich" -> date.plusWeeks(1);
                "Monatlich" -> date.plusMonths(1); else -> date }
            listOf(item.copy(id = id(), due = future.toString(), done = false, doneBy = "", doneByName = ""))
        } catch (_: Exception) { emptyList() } else emptyList()
        h.copy(tasks = updated + next)
    }
    override fun deleteTask(item: HouseholdTask) = mutate { it.copy(tasks = it.tasks.filterNot { t -> t.id == item.id }) }
    override fun addStock(name: String, quantity: Int, minimum: Int, unit: String) = mutate {
        it.copy(stock = it.stock + StockItem(id(), name, quantity, minimum, unit))
    }
    override fun changeStock(item: StockItem, amount: Int) = mutate { h ->
        h.copy(stock = h.stock.map { if(it.id == item.id) it.copy(quantity = (it.quantity + amount).coerceAtLeast(0)) else it })
    }
    override fun deleteStock(item: StockItem) = mutate { it.copy(stock = it.stock.filterNot { s -> s.id == item.id }) }
    override fun createInvite(reply: (String) -> Unit) = reply("Freigabe ist nur mit Cloud-Anmeldung möglich")
}
