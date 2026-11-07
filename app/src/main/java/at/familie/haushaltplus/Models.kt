package at.familie.haushaltplus

data class ShoppingList(val id: String = "standard", val name: String = "Allgemein")

data class ShoppingItem(
    val id: String = "", val name: String = "", val quantity: String = "1",
    val unit: String = "Stk", val category: String = "Sonstiges", val bought: Boolean = false,
    val doneBy: String = "", val doneByName: String = "", val price: Double = 0.0,
    val listId: String = "standard", val boughtAt: String = "", val barcode: String = "",
    val archived: Boolean = false
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
data class TemplateProduct(val name: String = "", val quantity: String = "1",
    val unit: String = "Stk", val category: String = "Lebensmittel")
data class ShoppingTemplate(val id: String = "", val name: String = "",
    val items: List<TemplateProduct> = emptyList())
data class ReceiptEntry(val id: String = "", val merchant: String = "",
    val date: String = "", val total: Double = 0.0, val note: String = "")
data class RecipeEntry(val id: String = "", val name: String = "",
    val portions: Int = 2, val ingredients: String = "")
data class StockUsage(val id: String = "", val stockId: String = "",
    val date: String = "", val quantity: Int = 0)
data class ActivityEntry(val id: String = "", val actor: String = "", val title: String = "",
    val kind: String = "", val createdAt: Long = 0L)

data class Member(val uid: String = "", val name: String = "")
data class HouseholdData(
    val id: String = "", val name: String = "Mein Haushalt", val ownName: String = "Ich",
    val inviteCode: String = "", val members: List<Member> = emptyList(),
    val lists: List<ShoppingList> = listOf(ShoppingList()),
    val shopping: List<ShoppingItem> = emptyList(), val tasks: List<HouseholdTask> = emptyList(),
    val stock: List<StockItem> = emptyList(), val cloud: Boolean = false,
    val loading: Boolean = false, val error: String = "",
    val meals: List<MealEntry> = emptyList(), val monthlyBudget: Double = 0.0,
    val ownerUid: String = "", val syncStatus: String = "Lokal", val ownUid: String = "",
    val templates: List<ShoppingTemplate> = emptyList(), val receipts: List<ReceiptEntry> = emptyList(),
    val recipes: List<RecipeEntry> = emptyList(), val stockUsage: List<StockUsage> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val stockRetryAvailable: Boolean = false
)

interface HouseholdStore {
    val data: kotlinx.coroutines.flow.StateFlow<HouseholdData>
    fun addShopping(name: String, quantity: String, unit: String, category: String, price: Double, listId: String = "standard")
    fun addShoppingList(name: String)
    fun renameShoppingList(list: ShoppingList, name: String)
    fun updateShopping(item: ShoppingItem)
    fun setShoppingDone(item: ShoppingItem, done: Boolean)
    fun archiveCompletedShopping(listId: String)
    fun deleteShopping(item: ShoppingItem)
    fun addTask(title: String, assignee: String, due: String, repeat: String, rotate: Boolean = false)
    fun updateTask(task: HouseholdTask)
    fun setTaskDone(item: HouseholdTask, done: Boolean)
    fun deleteTask(item: HouseholdTask)
    fun addStock(name: String, quantity: Int, minimum: Int, unit: String)
    fun updateStock(item: StockItem)
    fun changeStock(item: StockItem, amount: Int)
    fun retryFailedStockChange()
    fun deleteStock(item: StockItem)
    fun setBudget(value: Double)
    fun addMeal(date: String, slot: String, title: String, ingredients: String)
    fun deleteMeal(item: MealEntry)
    fun removeMember(uid: String)
    fun transferOwnership(uid: String, reply: (String) -> Unit)
    fun revokeInvite(code: String, reply: (String) -> Unit)
    fun saveTemplate(name: String, products: List<TemplateProduct>)
    fun deleteTemplate(template: ShoppingTemplate)
    fun addReceipt(merchant: String, date: String, total: Double, note: String)
    fun deleteReceipt(item: ReceiptEntry)
    fun addRecipe(name: String, portions: Int, ingredients: String)
    fun deleteRecipe(item: RecipeEntry)
    fun createInvite(reply: (String) -> Unit)
    fun close() {}
}
