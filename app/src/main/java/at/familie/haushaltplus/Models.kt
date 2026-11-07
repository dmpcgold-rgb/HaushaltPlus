package at.familie.haushaltplus

data class ShoppingList(val id: String = "standard", val name: String = "Allgemein")

data class ShoppingItem(
    val id: String = "", val name: String = "", val quantity: String = "1",
    val unit: String = "Stk", val category: String = "Sonstiges", val bought: Boolean = false,
    val doneBy: String = "", val doneByName: String = "", val price: Double = 0.0,
    val listId: String = "standard"
)
data class HouseholdTask(
    val id: String = "", val title: String = "", val assignee: String = "Alle",
    val due: String = "", val repeat: String = "Nie", val done: Boolean = false,
    val doneBy: String = "", val doneByName: String = ""
)
data class StockItem(
    val id: String = "", val name: String = "", val quantity: Int = 0,
    val minimum: Int = 1, val unit: String = "Stk"
)
data class Member(val uid: String = "", val name: String = "")
data class HouseholdData(
    val id: String = "", val name: String = "Mein Haushalt", val ownName: String = "Ich",
    val inviteCode: String = "", val members: List<Member> = emptyList(),
    val lists: List<ShoppingList> = listOf(ShoppingList()),
    val shopping: List<ShoppingItem> = emptyList(), val tasks: List<HouseholdTask> = emptyList(),
    val stock: List<StockItem> = emptyList(), val cloud: Boolean = false,
    val loading: Boolean = false, val error: String = ""
)

interface HouseholdStore {
    val data: kotlinx.coroutines.flow.StateFlow<HouseholdData>
    fun addShopping(name: String, quantity: String, unit: String, category: String, price: Double, listId: String = "standard")
    fun addShoppingList(name: String)
    fun setShoppingDone(item: ShoppingItem, done: Boolean)
    fun deleteShopping(item: ShoppingItem)
    fun addTask(title: String, assignee: String, due: String, repeat: String)
    fun setTaskDone(item: HouseholdTask, done: Boolean)
    fun deleteTask(item: HouseholdTask)
    fun addStock(name: String, quantity: Int, minimum: Int, unit: String)
    fun changeStock(item: StockItem, amount: Int)
    fun deleteStock(item: StockItem)
    fun createInvite(reply: (String) -> Unit)
    fun close() {}
}
