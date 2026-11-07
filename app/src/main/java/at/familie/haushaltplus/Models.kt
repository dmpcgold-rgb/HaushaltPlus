package at.familie.haushaltplus

data class ShoppingList(val id: String = "standard", val name: String = "Allgemein")

data class ShoppingItem(
    val id: String = "", val name: String = "", val quantity: String = "1",
    val unit: String = "Stk", val category: String = "Sonstiges", val bought: Boolean = false,
    val doneBy: String = "", val doneByName: String = "", val price: Double = 0.0,
    val listId: String = "standard", val boughtAt: String = "", val barcode: String = ""
)
data class HouseholdTask(
    val id: String = "", val title: String = "", val assignee: String = "Alle",
    val due: String = "", val repeat: String = "Nie", val done: Boolean = false,
    val doneBy: String = "", val doneByName: String = "", val rotate: Boolean = false
)
data class StockItem(
    val id: String = "", val name: String = "", val quantity: Int = 0,
    val minimum: Int = 1, val unit: String = "Stk"
)
data class MealEntry(
    val id: String = "", val date: String = "", val slot: String = "Abendessen",
    val title: String = "", val ingredients: String = ""
)
data class Member(val uid: String = "", val name: String = "")
data class HouseholdData(
    val id: String = "", val name: String = "Mein Haushalt", val ownName: String = "Ich",
    val inviteCode: String = "", val members: List<Member> = emptyList(),
    val lists: List<ShoppingList> = listOf(ShoppingList()),
    val shopping: List<ShoppingItem> = emptyList(), val tasks: List<HouseholdTask> = emptyList(),
    val stock: List<StockItem> = emptyList(), val cloud: Boolean = false,
    val loading: Boolean = false, val error: String = "",
    val meals: List<MealEntry> = emptyList(), val monthlyBudget: Double = 0.0,
    val ownerUid: String = "", val syncStatus: String = "Lokal"
)

interface HouseholdStore {
    val data: kotlinx.coroutines.flow.StateFlow<HouseholdData>
    fun addShopping(name: String, quantity: String, unit: String, category: String, price: Double, listId: String = "standard")
    fun addShoppingList(name: String)
    fun renameShoppingList(list: ShoppingList, name: String)
    fun updateShopping(item: ShoppingItem)
    fun setShoppingDone(item: ShoppingItem, done: Boolean)
    fun deleteShopping(item: ShoppingItem)
    fun addTask(title: String, assignee: String, due: String, repeat: String, rotate: Boolean = false)
    fun updateTask(task: HouseholdTask)
    fun setTaskDone(item: HouseholdTask, done: Boolean)
    fun deleteTask(item: HouseholdTask)
    fun addStock(name: String, quantity: Int, minimum: Int, unit: String)
    fun updateStock(item: StockItem)
    fun changeStock(item: StockItem, amount: Int)
    fun deleteStock(item: StockItem)
    fun setBudget(value: Double)
    fun addMeal(date: String, slot: String, title: String, ingredients: String)
    fun deleteMeal(item: MealEntry)
    fun removeMember(uid: String)
    fun revokeInvite(code: String, reply: (String) -> Unit)
    fun createInvite(reply: (String) -> Unit)
    fun close() {}
}
