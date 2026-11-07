package at.familie.haushaltplus

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
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
    private var lastUnavailableHomeId: String? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                homeWatch?.remove(); homeWatch = null
                activeStore?.close(); activeStore = null
                trackedUid = null
                displayName = "Ich"
                context.getSharedPreferences("haushalt_cloud", Context.MODE_PRIVATE).edit()
                    .remove("homeId").remove("homeUid").apply()
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
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnSuccessListener { result ->
            val user = result.user ?: return@addOnSuccessListener
            displayName = name.trim()
            // Save the name in both Auth and the Firestore profile. A fast auth-state
            // listener may initialize home discovery before either write completes.
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build())
                .addOnFailureListener(::fail)
            db.collection("users").document(user.uid)
                .set(mapOf("displayName" to displayName))
                .addOnFailureListener(::fail)
        }.addOnFailureListener(::fail)
    }
    private fun findHome(uid: String) {
        mutable.value = CloudState(signedIn = true, busy = true)
        db.collection("users").document(uid).get().addOnSuccessListener {
            displayName = it.getString("displayName")?.takeIf(String::isNotBlank)
                ?: auth.currentUser?.displayName?.takeIf(String::isNotBlank)
                ?: displayName
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
                        db.collection("households").document(houseId).collection("members").document(uid)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .addOnSuccessListener { member ->
                                if (member.exists()) {
                                    lastUnavailableHomeId = null
                                    activeStore = CloudStore(context, db, uid, displayName, houseId)
                                    mutable.value = CloudState(signedIn = true, store = activeStore, busy = false)
                                } else {
                                    lastUnavailableHomeId = houseId
                                    mutable.value = CloudState(signedIn = true, needsHousehold = true,
                                        busy = false, error = "Kein Zugriff auf den gespeicherten Haushalt")
                                }
                            }.addOnFailureListener { problem ->
                                // Never delete a household bookmark because of temporary offline errors.
                                if (problem is com.google.firebase.firestore.FirebaseFirestoreException &&
                                    problem.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                    lastUnavailableHomeId = houseId
                                    mutable.value = CloudState(signedIn = true, needsHousehold = true,
                                        busy = false, error = "Kein Zugriff auf den gespeicherten Haushalt")
                                } else {
                                    activeStore = CloudStore(context, db, uid, displayName, houseId)
                                    mutable.value = CloudState(signedIn = true, store = activeStore, busy = false)
                                }
                            }
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
    fun unlinkUnavailableHome() {
        val uid = auth.currentUser?.uid ?: return
        val homeId = lastUnavailableHomeId ?: return
        db.collection("users").document(uid).collection("homes").document(homeId).delete()
            .addOnSuccessListener {
                lastUnavailableHomeId = null
                findHome(uid)
            }.addOnFailureListener(::fail)
    }
    fun resetPassword(email: String, reply: (String) -> Unit) {
        if (email.trim().isEmpty()) { reply("Bitte E-Mail-Adresse eingeben"); return }
        auth.sendPasswordResetEmail(email.trim()).addOnSuccessListener {
            reply("Falls das Konto existiert, erhältst du eine E-Mail zum Zurücksetzen.")
        }.addOnFailureListener { reply(it.localizedMessage ?: "Zurücksetzen fehlgeschlagen") }
    }
    fun deleteOwnAccount(password: String, reply: (String) -> Unit) {
        val user = auth.currentUser ?: return reply("Nicht angemeldet")
        val store = activeStore ?: return reply("Bitte Haushalt laden")
        if (store.data.value.ownerUid == user.uid) {
            reply("Als Haushaltsbesitzer zuerst die Eigentümerschaft übertragen.")
            return
        }
        val credential = EmailAuthProvider.getCredential(user.email ?: return reply("E-Mail fehlt"), password)
        user.reauthenticate(credential).addOnSuccessListener {
            val homeId = store.houseId
            val batch = db.batch()
            batch.delete(db.collection("households").document(homeId).collection("members").document(user.uid))
            batch.delete(db.collection("users").document(user.uid).collection("homes").document(homeId))
            batch.delete(db.collection("users").document(user.uid))
            batch.commit().addOnSuccessListener {
                user.delete().addOnSuccessListener { reply("Konto gelöscht") }
                    .addOnFailureListener { reply("Haushaltszugang entfernt, Konto-Löschung fehlgeschlagen: " + it.localizedMessage) }
            }.addOnFailureListener { reply(it.localizedMessage ?: "Konto konnte nicht entfernt werden") }
        }.addOnFailureListener { reply(it.localizedMessage ?: "Passwort falsch") }
    }
    fun logout() {
        HaushaltWidgetUpdater.clear(context)
        WorkManager.getInstance(context).cancelUniqueWork("haushalt_free_notifications")
        context.getSharedPreferences("haushalt_cloud", Context.MODE_PRIVATE).edit()
            .remove("homeId").remove("homeUid").apply()
        auth.signOut()
    }
}

class CloudStore(
    private val context: Context, private val db: FirebaseFirestore, private val uid: String,
    private val userName: String, val houseId: String
) : HouseholdStore {
    private val home = db.collection("households").document(houseId)
    private val mutable = MutableStateFlow(HouseholdData(id = houseId, ownName = userName, ownUid = uid, cloud = true, loading = true))
    override val data: StateFlow<HouseholdData> = mutable
    private val listeners = mutableListOf<ListenerRegistration>()
    private fun setError(e: Exception) { mutable.value = mutable.value.copy(error = e.localizedMessage ?: "Speichern fehlgeschlagen") }

    init {
        context.getSharedPreferences("haushalt_cloud", Context.MODE_PRIVATE).edit()
            .putString("homeId", houseId).putString("homeUid", uid).apply()
        listeners += home.addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(
                name = snap?.getString("name") ?: "Familienhaushalt",
                ownerUid = snap?.getString("ownerUid") ?: "",
                monthlyBudget = snap?.getDouble("monthlyBudget") ?: 0.0,
                loading = false
            )
        }
        listeners += home.collection("members").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(members = snap?.documents?.map { Member(it.id, it.getString("name") ?: "Mitglied") } ?: emptyList())
        }
        listeners += home.collection("shoppingLists").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(lists = listOf(ShoppingList()) + (snap?.documents?.map { ShoppingList(it.id, it.getString("name") ?: "Liste") } ?: emptyList()))
        }
        listeners += home.collection("shoppingItems").addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(shopping = snap?.documents?.map { doc ->
                ShoppingItem(doc.id, doc.getString("name") ?: "", doc.getString("quantity") ?: "1",
                    doc.getString("unit") ?: "Stk", doc.getString("category") ?: "Sonstiges",
                    doc.getBoolean("bought") ?: false, doc.getString("doneBy") ?: "", doc.getString("doneByName") ?: "",
                    doc.getDouble("price") ?: 0.0, doc.getString("listId") ?: "standard",
                    doc.getString("boughtAt") ?: "", doc.getString("barcode") ?: "",
                    doc.getBoolean("archived") ?: false)
            } ?: emptyList(), syncStatus = when {
                snap?.metadata?.hasPendingWrites() == true -> "Wartet auf Sync"
                snap?.metadata?.isFromCache == true -> "Offline verfügbar"
                else -> "Synchronisiert"
            }, pendingWrites = snap?.metadata?.hasPendingWrites() == true,
            lastSyncedAt = if (snap != null && !snap.metadata.isFromCache && !snap.metadata.hasPendingWrites())
                System.currentTimeMillis() else mutable.value.lastSyncedAt)
            HaushaltWidgetUpdater.update(context, mutable.value)
        }
        listeners += home.collection("tasks").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            if (snap != null && !snap.metadata.isFromCache)
                FreeNotifications.dueReminders(context, uid, houseId, snap.documents)
            mutable.value = mutable.value.copy(tasks = snap?.documents?.map { doc ->
                HouseholdTask(doc.id, doc.getString("title") ?: "", doc.getString("assignee") ?: "Alle",
                    doc.getString("due") ?: "", doc.getString("repeat") ?: "Nie",
                    doc.getBoolean("done") ?: false, doc.getString("doneBy") ?: "", doc.getString("doneByName") ?: "",
                    doc.getBoolean("rotate") ?: false)
            } ?: emptyList())
            HaushaltWidgetUpdater.update(context, mutable.value)
        }
        listeners += home.collection("stock").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(stock = snap?.documents?.map { doc ->
                StockItem(doc.id, doc.getString("name") ?: "", (doc.getLong("quantity") ?: 0).toInt().coerceAtLeast(0),
                    (doc.getLong("minimum") ?: 1).toInt(), doc.getString("unit") ?: "Stk",
                    doc.getString("expiresOn") ?: "")
            } ?: emptyList())
            if (snap != null && !snap.metadata.isFromCache)
                FreeNotifications.expiryReminder(context, uid, houseId, mutable.value.stock)
        }
        listeners += home.collection("meals").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(meals = snap?.documents?.map { doc ->
                MealEntry(doc.id, doc.getString("date") ?: "", doc.getString("slot") ?: "Abendessen",
                    doc.getString("title") ?: "", doc.getString("ingredients") ?: "")
            } ?: emptyList())
        }
        listeners += home.collection("templates").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(templates = snap?.documents?.map { doc ->
                ShoppingTemplate(doc.id, doc.getString("name") ?: "",
                    V3Logic.itemsFromJson(doc.getString("itemsJson") ?: "[]"))
            } ?: emptyList())
        }
        listeners += home.collection("recipes").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(recipes = snap?.documents?.map { doc ->
                RecipeEntry(doc.id, doc.getString("name") ?: "",
                    (doc.getLong("portions") ?: 2L).toInt(), doc.getString("ingredients") ?: "")
            } ?: emptyList())
        }
        listeners += home.collection("receipts").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(receipts = snap?.documents?.map { doc ->
                ReceiptEntry(doc.id, doc.getString("merchant") ?: "", doc.getString("date") ?: "",
                    doc.getDouble("total") ?: 0.0, doc.getString("note") ?: "")
            } ?: emptyList())
        }
        listeners += home.collection("stockUsage").orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(300).addSnapshotListener { snap, e ->
                if (e != null) { setError(e); return@addSnapshotListener }
                mutable.value = mutable.value.copy(stockUsage = snap?.documents?.map { doc ->
                    StockUsage(doc.id, doc.getString("stockId") ?: "", doc.getString("date") ?: "",
                        (doc.getLong("quantity") ?: 0L).toInt())
                } ?: emptyList())
            }
        listeners += home.collection("prices").orderBy("date", Query.Direction.DESCENDING)
            .limit(200).addSnapshotListener { snap, e ->
                if (e != null) { setError(e); return@addSnapshotListener }
                mutable.value = mutable.value.copy(prices = snap?.documents?.map { d ->
                    PriceEntry(d.id, d.getString("product") ?: "", d.getString("shop") ?: "",
                        d.getDouble("unitPrice") ?: 0.0, d.getString("unit") ?: "Stk",
                        d.getString("date") ?: "")
                } ?: emptyList())
            }
        listeners += home.collection("checklists").addSnapshotListener { snap, e ->
            if (e != null) { setError(e); return@addSnapshotListener }
            mutable.value = mutable.value.copy(checklists = snap?.documents?.map { d ->
                ChecklistTemplate(d.id, d.getString("name") ?: "",
                    (d.get("tasks") as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
            } ?: emptyList())
        }
        listeners += home.collection("trash").orderBy("deletedAt", Query.Direction.DESCENDING)
            .limit(100).addSnapshotListener { snap, e ->
                if (e != null) { setError(e); return@addSnapshotListener }
                mutable.value = mutable.value.copy(trash = snap?.documents?.map { d ->
                    TrashEntry(d.id, d.getString("type") ?: "", d.getString("label") ?: "",
                        d.getString("payload") ?: "", d.getLong("deletedAt") ?: 0L)
                }?.filterNot { V4TrashCodec.isExpired(it) } ?: emptyList())
            }
        listeners += home.collection("activity").orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40).addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && !snapshot.metadata.isFromCache) {
                    FreeNotifications.process(context, uid, houseId, snapshot.documents)
                }
                if (snapshot != null) {
                    mutable.value = mutable.value.copy(activity = snapshot.documents.map { doc ->
                        ActivityEntry(doc.id, doc.getString("actorName") ?: "",
                            doc.getString("title") ?: "", doc.getString("kind") ?: "",
                            doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
                    })
                }
            }
        FreeNotifications.schedule(context)
    }
    private fun recordActivity(title: String, kind: String = "all") {
        home.collection("activity").add(mapOf(
            "actorUid" to uid, "actorName" to userName,
            "title" to title.take(180), "kind" to kind, "createdAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener(::setError)
    }
    override fun addShopping(name: String, quantity: String, unit: String, category: String, price: Double, listId: String) {
        val duplicate = mutable.value.shopping.firstOrNull {
            !it.bought && it.listId == listId && it.name.equals(name.trim(), ignoreCase = true) &&
                V4Logic.combine(it.quantity, it.unit, quantity, unit) != null
        }
        val qty = duplicate?.let { V4Logic.combine(it.quantity, it.unit, quantity, unit) }
        if (duplicate != null && qty != null) {
            updateShopping(duplicate.copy(quantity = qty, price = duplicate.price + price))
            return
        }
        home.collection("shoppingItems").add(mapOf("name" to name.trim(), "quantity" to quantity, "unit" to unit,
            "category" to category, "price" to price, "listId" to listId, "bought" to false, "doneBy" to "",
            "doneByName" to "", "createdBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { recordActivity(userName + " hat " + name.trim() + " hinzugefügt", "shopping") }
            .addOnFailureListener(::setError)
    }
    override fun addShoppingList(name: String) {
        home.collection("shoppingLists").add(mapOf("name" to name.trim(), "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp())).addOnFailureListener(::setError)
    }
    override fun renameShoppingList(list: ShoppingList, name: String) {
        if (list.id == "standard") return
        home.collection("shoppingLists").document(list.id).update("name", name.trim()).addOnFailureListener(::setError)
    }
    override fun updateShopping(item: ShoppingItem) {
        home.collection("shoppingItems").document(item.id).update(mapOf(
            "name" to item.name.trim(), "quantity" to item.quantity, "unit" to item.unit,
            "category" to item.category, "price" to item.price, "barcode" to item.barcode,
            "updatedAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener(::setError)
    }
    override fun setShoppingDone(item: ShoppingItem, done: Boolean) {
        home.collection("shoppingItems").document(item.id).update(mapOf("bought" to done,
            "doneBy" to if (done) uid else "", "doneByName" to if(done) userName else "",
            "boughtAt" to if (done) LocalDate.now().toString() else "",
            "archived" to if (done) item.archived else false,
            "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { recordActivity(userName + if (done) " hat " + item.name + " eingekauft" else " hat " + item.name + " wieder geöffnet", "shopping") }
            .addOnFailureListener(::setError)
    }
    override fun archiveCompletedShopping(listId: String) {
        // Keep purchased documents for month totals and frequently bought suggestions.
        // Chunk writes to stay below Firestore's batch-write limit.
        mutable.value.shopping.filter { it.listId == listId && it.bought && !it.archived }
            .chunked(300).forEach { items ->
                val batch = db.batch()
                items.forEach { item ->
                    batch.update(home.collection("shoppingItems").document(item.id), "archived", true)
                }
                batch.commit().addOnFailureListener(::setError)
            }
    }
    private fun trash(type: String, label: String, originalId: String,
        collection: String, payload: String) {
        val doc = home.collection("trash").document()
        val batch = db.batch()
        batch.set(doc, mapOf(
            "type" to type, "label" to label.take(120), "payload" to payload.take(12000),
            "deletedAt" to System.currentTimeMillis(), "deletedBy" to uid
        ))
        batch.delete(home.collection(collection).document(originalId))
        batch.commit().addOnFailureListener(::setError)
    }
    override fun deleteShopping(item: ShoppingItem) {
        trash("shopping", item.name, item.id, "shoppingItems", V4TrashCodec.shopping(item))
    }
    override fun addTask(title: String, assignee: String, due: String, repeat: String, rotate: Boolean) {
        home.collection("tasks").add(mapOf("title" to title.trim(), "assignee" to assignee,
            "due" to due, "repeat" to repeat, "rotate" to rotate, "done" to false, "doneBy" to "", "doneByName" to "",
            "createdBy" to uid, "updatedAt" to FieldValue.serverTimestamp())).addOnFailureListener(::setError)
    }
    override fun updateTask(task: HouseholdTask) {
        home.collection("tasks").document(task.id).update(mapOf(
            "title" to task.title.trim(), "assignee" to task.assignee,
            "due" to task.due, "repeat" to task.repeat, "rotate" to task.rotate,
            "updatedAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener(::setError)
    }
    private val taskChangesInFlight = mutableSetOf<String>()

    override fun setTaskDone(item: HouseholdTask, done: Boolean) {
        if (item.done == done) return
        // Debounce same task across rapid taps until Firebase acknowledges the change.
        synchronized(taskChangesInFlight) {
            if (!taskChangesInFlight.add(item.id)) return
        }
        val ref = home.collection("tasks").document(item.id)
        val update = mapOf(
            "done" to done, "doneBy" to if (done) uid else "",
            "doneByName" to if (done) userName else "",
            "updatedAt" to FieldValue.serverTimestamp(),
            "completedAt" to if (done) FieldValue.serverTimestamp() else FieldValue.delete()
        )
        val followUp = if (done) TaskPlanner.nextDue(item.due, item.repeat) else null
        val operation = if (followUp != null) {
            val batch = db.batch()
            batch.update(ref, update)
            val nextId = item.id + "--next-" + followUp
            val assignee = TaskPlanner.nextAssignee(
                item.assignee, item.rotate, mutable.value.members.map { it.name }
            )
            batch.set(home.collection("tasks").document(nextId), mapOf(
                "title" to item.title, "assignee" to assignee, "due" to followUp,
                "repeat" to item.repeat, "rotate" to item.rotate, "done" to false,
                "doneBy" to "", "doneByName" to "", "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            ))
            batch.commit()
        } else {
            ref.update(update)
        }
        operation.addOnCompleteListener { task ->
            synchronized(taskChangesInFlight) { taskChangesInFlight.remove(item.id) }
            if (task.isSuccessful) {
                recordActivity(
                    userName + if (done) " hat die Aufgabe " + item.title + " erledigt"
                    else " hat die Aufgabe " + item.title + " wieder geöffnet",
                    "tasks"
                )
            } else {
                task.exception?.let(::setError)
            }
        }
    }
    override fun deleteTask(item: HouseholdTask) {
        trash("task", item.title, item.id, "tasks", V4TrashCodec.task(item))
    }
    override fun addStock(name: String, quantity: Int, minimum: Int, unit: String, expiresOn: String) {
        home.collection("stock").add(mapOf("name" to name.trim(), "quantity" to quantity,
            "minimum" to minimum, "unit" to unit, "expiresOn" to expiresOn, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                if (quantity <= minimum) addMissingStockToShopping(name, unit)
            }.addOnFailureListener(::setError)
    }
    private fun addMissingStockToShopping(name: String, unit: String) {
        if (mutable.value.shopping.none { !it.bought && it.name.equals(name.trim(), ignoreCase = true) }) {
            addShopping(name, "1", unit, "Vorrat", 0.0, "standard")
        }
    }
    override fun updateStock(item: StockItem) {
        home.collection("stock").document(item.id).update(mapOf(
            "name" to item.name, "quantity" to item.quantity, "minimum" to item.minimum,
            "unit" to item.unit, "expiresOn" to item.expiresOn,
            "updatedAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener {
            if (item.quantity <= item.minimum) addMissingStockToShopping(item.name, item.unit)
        }.addOnFailureListener(::setError)
    }
    private var failedStockChange: Pair<String, Int>? = null
    override fun retryFailedStockChange() {
        val pending = failedStockChange ?: return
        val latest = mutable.value.stock.firstOrNull { it.id == pending.first } ?: return
        failedStockChange = null
        mutable.value = mutable.value.copy(stockRetryAvailable = false, error = "")
        changeStock(latest, pending.second)
    }
    override fun changeStock(item: StockItem, amount: Int) {
        if (amount == 0) return
        val amountSafe = if (amount < 0) amount.coerceAtLeast(-item.quantity.coerceAtLeast(0)) else amount
        if (amountSafe == 0) return
        // Atomic increment is persisted by Firestore offline and replayed on reconnect.
        // A server-side nonnegative quantity rule rejects conflicting decrements.
        home.collection("stock").document(item.id).update(mapOf(
            "quantity" to FieldValue.increment(amountSafe.toLong()),
            "updatedAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener {
            failedStockChange = null
            mutable.value = mutable.value.copy(stockRetryAvailable = false, error = "")
            if (amountSafe < 0) {
                home.collection("stockUsage").add(mapOf(
                    "stockId" to item.id, "quantity" to -amountSafe,
                    "date" to LocalDate.now().toString(),
                    "createdAt" to FieldValue.serverTimestamp()
                )).addOnFailureListener(::setError)
                recordActivity(userName + " hat " + item.name + " verbraucht", "stock")
            }
            if (item.quantity > item.minimum && item.quantity + amountSafe <= item.minimum)
                addMissingStockToShopping(item.name, item.unit)
        }.addOnFailureListener { error ->
            failedStockChange = item.id to amountSafe
            mutable.value = mutable.value.copy(stockRetryAvailable = true)
            setError(IllegalStateException("Bestandsänderung nicht übernommen: " +
                (error.localizedMessage ?: "Konflikt").take(120) + " – erneut versuchen"))
        }
    }
    override fun deleteStock(item: StockItem) {
        trash("stock", item.name, item.id, "stock", V4TrashCodec.stock(item))
    }
    override fun setBudget(value: Double) {
        if (mutable.value.ownerUid != uid) {
            setError(IllegalStateException("Nur die Haushaltsverwaltung darf das Budget ändern"))
            return
        }
        home.update("monthlyBudget", value.coerceAtLeast(0.0)).addOnFailureListener(::setError)
    }
    override fun addMeal(date: String, slot: String, title: String, ingredients: String) {
        home.collection("meals").add(mapOf(
            "date" to date, "slot" to slot, "title" to title.trim(),
            "ingredients" to ingredients.trim(), "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener(::setError)
    }
    override fun deleteMeal(item: MealEntry) {
        home.collection("meals").document(item.id).delete().addOnFailureListener(::setError)
    }
    override fun removeMember(memberUid: String) {
        if (mutable.value.ownerUid != uid || memberUid == uid) return
        home.collection("members").document(memberUid).delete().addOnSuccessListener {
            recordActivity(userName + " hat ein Mitglied entfernt", "family")
        }.addOnFailureListener(::setError)
    }
    override fun transferOwnership(newOwnerUid: String, reply: (String) -> Unit) {
        if (mutable.value.ownerUid != uid) return reply("Nur die Haushaltsverwaltung darf übertragen")
        if (newOwnerUid == uid || mutable.value.members.none { it.uid == newOwnerUid }) {
            reply("Neue Verwaltung muss bereits Haushaltsmitglied sein")
            return
        }
        home.update("ownerUid", newOwnerUid)
            .addOnSuccessListener { reply("Verwaltung übertragen") }
            .addOnFailureListener { reply(it.localizedMessage ?: "Übertragung fehlgeschlagen") }
    }
    override fun revokeInvite(code: String, reply: (String) -> Unit) {
        if (mutable.value.ownerUid != uid) return reply("Nur die Haushaltsverwaltung darf Einladungen widerrufen")
        db.collection("invites").document(code).update("active", false)
            .addOnSuccessListener { reply("Einladung widerrufen") }
            .addOnFailureListener { reply(it.localizedMessage ?: "Fehler beim Widerruf") }
    }
    override fun saveTemplate(name: String, products: List<TemplateProduct>) {
        if (products.isEmpty()) return
        home.collection("templates").add(mapOf(
            "name" to name.take(100), "itemsJson" to V3Logic.itemsToJson(products.take(100)),
            "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener(::setError)
    }
    override fun deleteTemplate(template: ShoppingTemplate) {
        home.collection("templates").document(template.id).delete().addOnFailureListener(::setError)
    }
    override fun addReceipt(merchant: String, date: String, total: Double, note: String) {
        home.collection("receipts").add(mapOf("merchant" to merchant.trim().take(100),
            "date" to date, "total" to total.coerceAtLeast(0.0),
            "note" to note.take(300), "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener(::setError)
        recordActivity(userName + " hat eine Ausgabe erfasst", "receipt")
    }
    override fun deleteReceipt(item: ReceiptEntry) {
        home.collection("receipts").document(item.id).delete().addOnFailureListener(::setError)
    }
    override fun addRecipe(name: String, portions: Int, ingredients: String) {
        home.collection("recipes").add(mapOf("name" to name.take(100),
            "portions" to portions.coerceAtLeast(1),
            "ingredients" to ingredients.take(3500),
            "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener(::setError)
    }
    override fun deleteRecipe(item: RecipeEntry) {
        trash("recipe", item.name, item.id, "recipes", V4TrashCodec.recipe(item))
    }
    override fun addPrice(product: String, shop: String, unitPrice: Double, unit: String, date: String) {
        if (product.isBlank() || shop.isBlank() || unitPrice <= 0.0) return
        home.collection("prices").add(mapOf(
            "product" to product.trim().take(100), "shop" to shop.trim().take(100),
            "unitPrice" to unitPrice, "unit" to unit, "date" to date,
            "createdBy" to uid
        )).addOnFailureListener(::setError)
    }
    override fun deletePrice(item: PriceEntry) {
        home.collection("prices").document(item.id).delete().addOnFailureListener(::setError)
    }
    override fun saveChecklist(name: String, titles: List<String>) {
        if (name.isBlank() || titles.isEmpty()) return
        home.collection("checklists").add(mapOf(
            "name" to name.trim().take(100), "tasks" to titles.take(80),
            "createdBy" to uid
        )).addOnFailureListener(::setError)
    }
    override fun deleteChecklist(item: ChecklistTemplate) {
        home.collection("checklists").document(item.id).delete().addOnFailureListener(::setError)
    }
    override fun restoreTrash(item: TrashEntry) {
        if (V4TrashCodec.isExpired(item)) return
        runCatching {
            val obj = org.json.JSONObject(item.payload)
            val original = obj.optString("id").takeIf(String::isNotBlank)
                ?: return@runCatching
            val (collection, data) = when(item.type) {
                "shopping" -> "shoppingItems" to mapOf<String, Any>(
                    "name" to obj.optString("name"), "quantity" to obj.optString("quantity", "1"),
                    "unit" to obj.optString("unit", "Stk"),
                    "category" to obj.optString("category", "Sonstiges"),
                    "price" to obj.optDouble("price", 0.0),
                    "listId" to obj.optString("listId", "standard"),
                    "bought" to obj.optBoolean("bought"),
                    "boughtAt" to obj.optString("boughtAt"),
                    "archived" to obj.optBoolean("archived"),
                    "doneBy" to "", "doneByName" to "")
                "task" -> "tasks" to mapOf<String, Any>(
                    "title" to obj.optString("title"), "assignee" to obj.optString("assignee", "Alle"),
                    "due" to obj.optString("due"), "repeat" to obj.optString("repeat", "Nie"),
                    "rotate" to obj.optBoolean("rotate"), "done" to obj.optBoolean("done"),
                    "doneBy" to "", "doneByName" to "")
                "recipe" -> "recipes" to mapOf<String, Any>(
                    "name" to obj.optString("name"), "portions" to obj.optInt("portions", 2),
                    "ingredients" to obj.optString("ingredients"))
                "stock" -> "stock" to mapOf<String, Any>(
                    "name" to obj.optString("name"), "quantity" to obj.optInt("quantity"),
                    "minimum" to obj.optInt("minimum", 1), "unit" to obj.optString("unit", "Stk"),
                    "expiresOn" to obj.optString("expiresOn"))
                else -> return@runCatching
            }
            // Restoring to a fresh ID avoids accidentally overwriting a newer family edit.
            val doc = home.collection(collection).document()
            val batch = db.batch()
            batch.set(doc, data)
            batch.delete(home.collection("trash").document(item.id))
            batch.commit().addOnFailureListener(::setError)
        }.onFailure(::setError)
    }
    override fun purgeTrash(item: TrashEntry) {
        home.collection("trash").document(item.id).delete().addOnFailureListener(::setError)
    }
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
