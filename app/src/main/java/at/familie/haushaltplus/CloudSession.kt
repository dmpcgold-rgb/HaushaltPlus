package at.familie.haushaltplus

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom
import java.util.Date
import java.time.LocalDate
import com.google.firebase.firestore.Query
import androidx.work.WorkManager

/** Creates/join homes and routes Firestore data into a single observable store. */
data class CloudState(
    val signedIn: Boolean = false, val needsHousehold: Boolean = false,
    val busy: Boolean = true, val error: String = "", val store: HouseholdStore? = null
)

class CloudSession(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val mutable = MutableStateFlow(CloudState())
    val state: StateFlow<CloudState> = mutable
    private var homeWatch: ListenerRegistration? = null
    private var activeStore: CloudStore? = null
    private var displayName: String = "Ich"
    private var trackedUid: String? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                homeWatch?.remove(); homeWatch = null
                activeStore?.close(); activeStore = null
                trackedUid = null
                mutable.value = CloudState(busy = false)
            } else if (user.uid != trackedUid) {
                trackedUid = user.uid
                findHome(user.uid)
            }
        }
    }
    private fun fail(e: Exception?) {
        mutable.value = mutable.value.copy(busy = false, error = e?.localizedMessage ?: "Unbekannter Fehler")
    }
    fun signIn(email: String, password: String) {
        mutable.value = mutable.value.copy(busy = true, error = "")
        auth.signInWithEmailAndPassword(email.trim(), password).addOnFailureListener(::fail)
    }
    fun signUp(email: String, password: String, name: String) {
        if (name.trim().isBlank()) { fail(IllegalArgumentException("Bitte Namen eingeben")); return }
        displayName = name.trim()
        mutable.value = mutable.value.copy(busy = true, error = "")
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnSuccessListener {
            val uid = it.user?.uid ?: return@addOnSuccessListener
            displayName = name.trim()
            db.collection("users").document(uid).set(mapOf("displayName" to displayName))
        }.addOnFailureListener(::fail)
    }
    private fun findHome(uid: String) {
        mutable.value = CloudState(signedIn = true, busy = true)
        db.collection("users").document(uid).get().addOnSuccessListener {
            displayName = it.getString("displayName")?.takeIf(String::isNotBlank) ?: displayName
            homeWatch?.remove()
            homeWatch = db.collection("users").document(uid).collection("homes")
                .limit(1).addSnapshotListener { snapshot, error ->
                    if (error != null) { fail(error); return@addSnapshotListener }
                    val houseId = snapshot?.documents?.firstOrNull()?.id
                    if (houseId == null) {
                        activeStore?.close(); activeStore = null
                        mutable.value = CloudState(signedIn = true, needsHousehold = true, busy = false)
                    } else if (activeStore?.houseId != houseId) {
                        activeStore?.close()
                        activeStore = CloudStore(context, db, uid, displayName, houseId)
                        mutable.value = CloudState(signedIn = true, store = activeStore, busy = false)
                    }
                }
        }.addOnFailureListener(::fail)
    }
    fun createHouse(name: String) {
        val uid = auth.currentUser?.uid ?: return
        val title = name.trim().ifBlank { "Unser Zuhause" }
        mutable.value = mutable.value.copy(busy = true, error = "")
        val home = db.collection("households").document()
        // Create household first. Rules permit its owner to add the first member.
        home.set(mapOf("name" to title, "ownerUid" to uid, "createdAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                home.collection("members").document(uid).set(mapOf("name" to displayName, "joinedAt" to FieldValue.serverTimestamp(), "notifyComplete" to true))
                    .addOnSuccessListener {
                        db.collection("users").document(uid).collection("homes").document(home.id)
                            .set(mapOf("name" to title)).addOnFailureListener(::fail)
                    }.addOnFailureListener(::fail)
            }.addOnFailureListener(::fail)
    }
    fun joinHouse(code: String) {
        val uid = auth.currentUser?.uid ?: return
        val clean = code.trim().uppercase()
        if (!Regex("[A-F0-9]{20}").matches(clean)) { fail(IllegalArgumentException("Ungültiger Einladungscode")); return }
        mutable.value = mutable.value.copy(busy = true, error = "")
        db.collection("invites").document(clean).get().addOnSuccessListener { invite ->
            val homeId = invite.getString("householdId")
            val expiry = invite.getTimestamp("expiresAt")?.toDate()
            if (!invite.exists() || homeId == null || invite.getBoolean("active") != true || expiry == null || expiry.before(Date())) {
                fail(IllegalArgumentException("Einladung ungültig oder abgelaufen")); return@addOnSuccessListener
            }
            val member = db.collection("households").document(homeId).collection("members").document(uid)
            member.set(mapOf("name" to displayName, "joinedAt" to FieldValue.serverTimestamp(), "notifyComplete" to true, "inviteCode" to clean))
                .addOnSuccessListener {
                    db.collection("users").document(uid).collection("homes").document(homeId)
                        .set(mapOf("name" to "Familienhaushalt")).addOnFailureListener(::fail)
                }.addOnFailureListener(::fail)
        }.addOnFailureListener(::fail)
    }
    fun logout() {
        WorkManager.getInstance(context).cancelUniqueWork("haushalt_free_notifications")
        auth.signOut()
    }
}

class CloudStore(
    private val context: Context, private val db: FirebaseFirestore, private val uid: String,
    private val userName: String, val houseId: String
) : HouseholdStore {
    private val home = db.collection("households").document(houseId)
    private val mutable = MutableStateFlow(HouseholdData(id = houseId, ownName = userName, cloud = true, loading = true))
    override val data: StateFlow<HouseholdData> = mutable
    private val listeners = mutableListOf<ListenerRegistration>()
    private fun setError(e: Exception) { mutable.value = mutable.value.copy(error = e.localizedMessage ?: "Speichern fehlgeschlagen") }

    init {
        context.getSharedPreferences("haushalt_cloud", Context.MODE_PRIVATE).edit().putString("homeId", houseId).apply()
        listeners += home.addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(name = snap?.getString("name") ?: "Familienhaushalt", loading = false)
        }
        listeners += home.collection("members").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(members = snap?.documents?.map { Member(it.id, it.getString("name") ?: "Mitglied") } ?: emptyList())
        }
        listeners += home.collection("shoppingLists").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(lists = listOf(ShoppingList()) + (snap?.documents?.map { ShoppingList(it.id, it.getString("name") ?: "Liste") } ?: emptyList()))
        }
        listeners += home.collection("shoppingItems").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(shopping = snap?.documents?.map { doc ->
                ShoppingItem(doc.id, doc.getString("name") ?: "", doc.getString("quantity") ?: "1",
                    doc.getString("unit") ?: "Stk", doc.getString("category") ?: "Sonstiges",
                    doc.getBoolean("bought") ?: false, doc.getString("doneBy") ?: "", doc.getString("doneByName") ?: "",
                    doc.getDouble("price") ?: 0.0, doc.getString("listId") ?: "standard")
            } ?: emptyList())
        }
        listeners += home.collection("tasks").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(tasks = snap?.documents?.map { doc ->
                HouseholdTask(doc.id, doc.getString("title") ?: "", doc.getString("assignee") ?: "Alle",
                    doc.getString("due") ?: "", doc.getString("repeat") ?: "Nie",
                    doc.getBoolean("done") ?: false, doc.getString("doneBy") ?: "", doc.getString("doneByName") ?: "")
            } ?: emptyList())
        }
        listeners += home.collection("stock").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(stock = snap?.documents?.map { doc ->
                StockItem(doc.id, doc.getString("name") ?: "", (doc.getLong("quantity") ?: 0).toInt(),
                    (doc.getLong("minimum") ?: 1).toInt(), doc.getString("unit") ?: "Stk")
            } ?: emptyList())
        }
        registerDevice()
        listeners += home.collection("activity").orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40).addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && !snapshot.metadata.isFromCache) {
                    FreeNotifications.process(context, uid, houseId, snapshot.documents)
                }
            }
        FreeNotifications.schedule(context)
    }
    private fun registerDevice() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            home.collection("devices").document(uid).set(mapOf("token" to token, "name" to userName,
                "updatedAt" to FieldValue.serverTimestamp()))
        }
    }
    private fun recordActivity(title: String) {
        home.collection("activity").add(mapOf(
            "actorUid" to uid, "actorName" to userName,
            "title" to title.take(180), "createdAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener(::setError)
    }
    override fun addShopping(name: String, quantity: String, unit: String, category: String, price: Double, listId: String) {
        home.collection("shoppingItems").add(mapOf("name" to name.trim(), "quantity" to quantity, "unit" to unit,
            "category" to category, "price" to price, "listId" to listId, "bought" to false, "doneBy" to "", "doneByName" to "",
            "createdBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { recordActivity(userName + " hat " + name.trim() + " hinzugefügt") }
            .addOnFailureListener(::setError)
    }
    override fun addShoppingList(name: String) {
        home.collection("shoppingLists").add(mapOf("name" to name.trim(), "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp())).addOnFailureListener(::setError)
    }
    override fun setShoppingDone(item: ShoppingItem, done: Boolean) {
        home.collection("shoppingItems").document(item.id).update(mapOf("bought" to done,
            "doneBy" to if (done) uid else "", "doneByName" to if(done) userName else "",
            "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { recordActivity(userName + if (done) " hat " + item.name + " eingekauft" else " hat " + item.name + " wieder geöffnet") }
            .addOnFailureListener(::setError)
    }
    override fun deleteShopping(item: ShoppingItem) { home.collection("shoppingItems").document(item.id).delete().addOnFailureListener(::setError) }
    override fun addTask(title: String, assignee: String, due: String, repeat: String) {
        home.collection("tasks").add(mapOf("title" to title.trim(), "assignee" to assignee,
            "due" to due, "repeat" to repeat, "done" to false, "doneBy" to "", "doneByName" to "",
            "createdBy" to uid, "updatedAt" to FieldValue.serverTimestamp())).addOnFailureListener(::setError)
    }
    override fun setTaskDone(item: HouseholdTask, done: Boolean) {
        val ref = home.collection("tasks").document(item.id)
        val update = mapOf(
            "done" to done, "doneBy" to if (done) uid else "",
            "doneByName" to if (done) userName else "",
            "updatedAt" to FieldValue.serverTimestamp(),
            "completedAt" to if (done) FieldValue.serverTimestamp() else FieldValue.delete()
        )
        if (done && item.repeat in setOf("Täglich", "Wöchentlich", "Monatlich")) {
            val due = runCatching { LocalDate.parse(item.due) }.getOrDefault(LocalDate.now())
            val base = if (due.isBefore(LocalDate.now())) LocalDate.now() else due
            val next = when (item.repeat) {
                "Täglich" -> base.plusDays(1)
                "Wöchentlich" -> base.plusWeeks(1)
                else -> base.plusMonths(1)
            }.toString()
            val batch = db.batch()
            batch.update(ref, update)
            val nextId = item.id + "--next-" + next
            batch.set(home.collection("tasks").document(nextId), mapOf(
                "title" to item.title, "assignee" to item.assignee, "due" to next,
                "repeat" to item.repeat, "done" to false, "doneBy" to "", "doneByName" to "",
                "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp()
            ))
            batch.commit().addOnSuccessListener {
                recordActivity(userName + " hat die Aufgabe " + item.title + " erledigt")
            }.addOnFailureListener(::setError)
        } else {
            ref.update(update).addOnSuccessListener {
                recordActivity(userName + if (done) " hat die Aufgabe " + item.title + " erledigt"
                    else " hat die Aufgabe " + item.title + " wieder geöffnet")
            }.addOnFailureListener(::setError)
        }
    }
    override fun deleteTask(item: HouseholdTask) { home.collection("tasks").document(item.id).delete().addOnFailureListener(::setError) }
    override fun addStock(name: String, quantity: Int, minimum: Int, unit: String) {
        home.collection("stock").add(mapOf("name" to name.trim(), "quantity" to quantity,
            "minimum" to minimum, "unit" to unit, "updatedAt" to FieldValue.serverTimestamp())).addOnFailureListener(::setError)
    }
    override fun changeStock(item: StockItem, amount: Int) {
        home.collection("stock").document(item.id).update(mapOf("quantity" to (item.quantity + amount).coerceAtLeast(0),
            "updatedAt" to FieldValue.serverTimestamp())).addOnFailureListener(::setError)
    }
    override fun deleteStock(item: StockItem) { home.collection("stock").document(item.id).delete().addOnFailureListener(::setError) }
    override fun createInvite(reply: (String) -> Unit) {
        val chars = "ABCDEF0123456789"
        val random = SecureRandom()
        val code = (1..20).map { chars[random.nextInt(chars.length)] }.joinToString("")
        val expiry = Timestamp(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
        db.collection("invites").document(code).set(mapOf("householdId" to houseId,
            "createdBy" to uid, "active" to true, "createdAt" to FieldValue.serverTimestamp(), "expiresAt" to expiry))
            .addOnSuccessListener { reply(code) }.addOnFailureListener { setError(it); reply("Fehler: ${it.localizedMessage}") }
    }
    override fun close() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }
}
