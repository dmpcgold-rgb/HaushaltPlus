package at.familie.haushaltplus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF1E3031)
private val Mint = Color(0xFF097967)
private val Canvas = Color(0xFFF5F8F6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.FIREBASE_ENABLED && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 22)
        }
        val local = LocalStore(this)
        val cloud = if (BuildConfig.FIREBASE_ENABLED) CloudSession(this) else null
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Mint, onPrimary = Color.White, surface = Color.White,
                background = Canvas, onBackground = Ink, onSurface = Ink,
                surfaceVariant = Color(0xFFE2EEE9)
            )) {
                if (cloud == null) HouseholdScreen(local, null)
                else {
                    val state by cloud.state.collectAsState()
                    when {
                        !state.signedIn -> LoginScreen(cloud, state)
                        state.needsHousehold -> CreateOrJoinScreen(cloud, state)
                        state.store != null -> HouseholdScreen(state.store!!, cloud)
                        state.busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else -> CreateOrJoinScreen(cloud, state)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(heading: String, description: String) {
    Column(Modifier.padding(top = 12.dp, bottom = 18.dp)) {
        Text(heading, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@Composable
private fun LoginScreen(session: CloudSession, state: CloudState) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(26.dp),
        verticalArrangement = Arrangement.Center) {
        Text("Haushalt+", style = MaterialTheme.typography.displayMedium, color = Mint, fontWeight = FontWeight.Black)
        Text("Einkaufen. Organisieren. Gemeinsam erledigen.", Modifier.padding(bottom = 34.dp))
        Header(if (creating) "Konto erstellen" else "Anmelden", "Damit alle im Haushalt dieselben Listen sehen")
        if (creating) OutlinedTextField(name, { name = it }, label = { Text("Dein Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(email, { email = it }, label = { Text("E-Mail") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Passwort (mindestens 6 Zeichen)") },
            modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
        if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(20.dp))
        Button(onClick = { if (creating) session.signUp(email, password, name) else session.signIn(email, password) },
            enabled = !state.busy && email.isNotBlank() && password.length >= 6 && (!creating || name.isNotBlank()),
            modifier = Modifier.fillMaxWidth()) { Text(if (creating) "Konto erstellen" else "Anmelden") }
        TextButton(onClick = { creating = !creating }) { Text(if (creating) "Ich habe schon ein Konto" else "Noch kein Konto? Registrieren") }
    }
}

@Composable
private fun CreateOrJoinScreen(session: CloudSession, state: CloudState) {
    var title by remember { mutableStateOf("Unser Zuhause") }
    var code by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Header("Willkommen zu Hause", "Haushalt anlegen oder mit einem Einladungscode beitreten")
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Neuen Haushalt gründen", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Haushaltsname") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { session.createHouse(title) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Haushalt erstellen") }
            }
        }
        Spacer(Modifier.height(20.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Familieneinladung erhalten?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(code, { code = it.uppercase().take(20) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("20-stelliger Code") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { session.joinHouse(code) }, enabled = !state.busy && code.length == 20,
                    modifier = Modifier.fillMaxWidth()) { Text("Haushalt beitreten") }
            }
        }
        if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 14.dp))
        TextButton(onClick = session::logout) { Text("Abmelden") }
    }
}

private val pages = listOf("Übersicht", "Einkauf", "Aufgaben", "Vorrat")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HouseholdScreen(store: HouseholdStore, session: CloudSession?) {
    val data by store.data.collectAsState()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var activeListId by rememberSaveable { mutableStateOf("standard") }
    var addListOpen by remember { mutableStateOf(false) }
    var houseOpen by remember { mutableStateOf(false) }
    var invitation by remember { mutableStateOf("") }
    val context = LocalContext.current
    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Haushalt+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(data.name + if (data.cloud) " · synchronisiert" else " · nur auf diesem Gerät",
                        style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                }
            }, actions = { IconButton(onClick = { houseOpen = true }) { Icon(Icons.Default.People, "Haushalt") } })
        },
        bottomBar = {
            NavigationBar {
                pages.forEachIndexed { idx, title ->
                    NavigationBarItem(selected = selected == idx, onClick = { selected = idx },
                        icon = { Icon(when(idx) { 0 -> Icons.Default.Home; 1 -> Icons.Default.ShoppingCart; 2 -> Icons.Default.CheckCircle; else -> Icons.Default.Inventory2 }, null) },
                        label = { Text(title) })
                }
            }
        },
        floatingActionButton = {
            if (selected != 0) FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Hinzufügen") }
        }
    ) { padding ->
        when (selected) {
            0 -> OverviewPage(data, modifier = Modifier.padding(padding), onJump = { selected = it })
            1 -> ShoppingPage(data, store, Modifier.padding(padding), activeListId, { activeListId = it }, { addListOpen = true })
            2 -> TasksPage(data, store, Modifier.padding(padding))
            else -> StockPage(data, store, Modifier.padding(padding))
        }
    }
    if (adding) when (selected) {
        1 -> AddShoppingDialog(onDismiss = { adding = false }, onAdd = { n,q,u,c,p -> store.addShopping(n,q,u,c,p,activeListId); adding = false })
        2 -> AddTaskDialog(data, onDismiss = { adding = false }, onAdd = { t,a,d,r -> store.addTask(t,a,d,r); adding = false })
        3 -> AddStockDialog(onDismiss = { adding = false }, onAdd = { n,q,m,u -> store.addStock(n,q,m,u); adding = false })
    }
    if (addListOpen) {
        var listName by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addListOpen = false }, title = { Text("Neue Einkaufsliste") },
            text = { OutlinedTextField(listName, { listName = it }, label = { Text("Name, z. B. Wochenmarkt") }, singleLine = true) },
            confirmButton = { TextButton(enabled = listName.isNotBlank() && data.lists.none { it.name.equals(listName.trim(), ignoreCase = true) },
                onClick = { store.addShoppingList(listName.trim()); addListOpen = false }) { Text("Erstellen") } },
            dismissButton = { TextButton(onClick = { addListOpen = false }) { Text("Abbrechen") } })
    }
    if (houseOpen) AlertDialog(onDismissRequest = { houseOpen = false },
        title = { Text("Familienhaushalt") },
        text = {
            Column {
                Text(data.name, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Mitglieder: " + data.members.joinToString { it.name }.ifBlank { "Ich" })
                Spacer(Modifier.height(14.dp))
                if (data.cloud) {
                    Text("Einladungscode (30 Tage gültig)", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { store.createInvite { invitation = it } }) { Text("Neuen Code erstellen") }
                    if (invitation.isNotEmpty()) {
                        Text(invitation, fontWeight = FontWeight.Bold, color = Mint)
                        TextButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Tritt unserem Haushalt+ bei. Einladungscode: $invitation") }
                            context.startActivity(Intent.createChooser(share, "Einladung teilen"))
                        }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Code teilen") }
                    }
                } else Text("Für Echtzeit-Freigabe und Push muss Firebase eingerichtet werden. Diese Version speichert nur lokal.")
                if (session != null) TextButton(onClick = { houseOpen = false; session.logout() }) { Text("Abmelden") }
            }
        }, confirmButton = { TextButton(onClick = { houseOpen = false }) { Text("Schließen") } }
    )
}

@Composable
private fun OverviewPage(data: HouseholdData, modifier: Modifier = Modifier, onJump: (Int) -> Unit) {
    val missing = data.stock.count { it.quantity <= it.minimum }
    val shoppingOpen = data.shopping.count { !it.bought }
    val taskOpen = data.tasks.count { !it.done }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Header("Alles im Blick", "Dein Haushalt auf einen Blick")
            if (data.error.isNotBlank()) Text(data.error, color = MaterialTheme.colorScheme.error)
        }
        item { DashboardCard("Einkauf", "$shoppingOpen offen", "${data.shopping.count { it.bought }} bereits besorgt", Icons.Default.ShoppingBasket, onClick = { onJump(1) }) }
        item { DashboardCard("Aufgaben", "$taskOpen zu erledigen", "${data.tasks.count { it.done }} abgeschlossen", Icons.Default.TaskAlt, onClick = { onJump(2) }) }
        item { DashboardCard("Vorrat", "$missing Nachkäufe empfohlen", "${data.stock.size} Artikel im Vorrat", Icons.Default.Inventory2, onClick = { onJump(3) }) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Einkaufsfortschritt", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    val progress = if (data.shopping.isEmpty()) 0f else data.shopping.count { it.bought }.toFloat() / data.shopping.size
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(9.dp), color = Mint)
                    Text("${data.shopping.count { it.bought }} von ${data.shopping.size} Artikeln erledigt", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (missing > 0) item {
            Text("Bald aufgebraucht", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(data.stock.filter { it.quantity <= it.minimum }.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DashboardCard(title: String, value: String, caption: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFD9EEE8)) {
                Icon(icon, null, Modifier.padding(14.dp).size(28.dp), tint = Mint)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun ShoppingPage(data: HouseholdData, store: HouseholdStore, modifier: Modifier = Modifier,
    activeListId: String, onSelectList: (String) -> Unit, onNewList: () -> Unit) {
    var showBought by remember { mutableStateOf(true) }
    var listExpanded by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<ShoppingItem?>(null) }
    val inList = data.shopping.filter { it.listId == activeListId }
    val ordered = inList.filter { showBought || !it.bought }.sortedWith(compareBy<ShoppingItem> { it.bought }.thenBy { it.category }.thenBy { it.name.lowercase() })
    val total = inList.filter { !it.bought }.sumOf { it.price }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Header("Einkaufslisten", "Gemeinsam einkaufen, live abhaken")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { listExpanded = true }) {
                        Text(data.lists.firstOrNull { it.id == activeListId }?.name ?: "Allgemein", maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = listExpanded, onDismissRequest = { listExpanded = false }) {
                        data.lists.forEach { list -> DropdownMenuItem(text = { Text(list.name) }, onClick = {
                            onSelectList(list.id); listExpanded = false
                        }) }
                    }
                }
                TextButton(onClick = onNewList) { Icon(Icons.Default.Add, null); Text("Neue Liste") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Offen: ${inList.count { !it.bought }}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Richtwert: ${"%.2f".format(Locale.GERMANY, total)} €", color = Mint)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showBought, { showBought = it })
                Text("Erledigte Artikel anzeigen")
            }
        }
        if (ordered.isEmpty()) item { EmptyMessage("Noch nichts auf der Liste", "Mit + kannst du einen Artikel hinzufügen.") }
        items(ordered, key = { it.id }) { item ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.bought, onCheckedChange = { store.setShoppingDone(item, it) })
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Medium,
                            textDecoration = if (item.bought) TextDecoration.LineThrough else null)
                        Text("${item.quantity} ${item.unit} · ${item.category}" +
                            (if(item.price > 0) " · ${"%.2f".format(Locale.GERMANY, item.price)} €" else ""),
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        if (item.bought && item.doneByName.isNotBlank()) Text("Erledigt von ${item.doneByName}", style = MaterialTheme.typography.labelSmall, color = Mint)
                    }
                    IconButton(onClick = { pendingDeletion = item }) { Icon(Icons.Default.DeleteOutline, "Löschen") }
                }
            }
        }
    }
    pendingDeletion?.let { item -> ConfirmDelete("${item.name} entfernen?") { yes -> if (yes) store.deleteShopping(item); pendingDeletion = null } }
}

@Composable
private fun TasksPage(data: HouseholdData, store: HouseholdStore, modifier: Modifier = Modifier) {
    var pendingDeletion by remember { mutableStateOf<HouseholdTask?>(null) }
    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
    val sorted = data.tasks.sortedWith(compareBy<HouseholdTask> { it.done }.thenBy { it.due.ifBlank { "9999" } })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Header("Haushaltsaufgaben", "Zuständig, fällig, erledigt") }
        if (sorted.isEmpty()) item { EmptyMessage("Keine Aufgaben", "Lege über + eine Aufgabe an.") }
        items(sorted, key = { it.id }) { task ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(task.done, { store.setTaskDone(task, it) })
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold, textDecoration = if (task.done) TextDecoration.LineThrough else null)
                        Text("Zuständig: ${task.assignee}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        if (task.due.isNotBlank()) Text("Fällig: ${task.due}" + if(!task.done && task.due < today) " · Überfällig" else "",
                            style = MaterialTheme.typography.bodySmall, color = if(!task.done && task.due < today) Color(0xFFB64029) else Color.Gray)
                        if (task.repeat != "Nie") Text("Wiederholt sich: ${task.repeat}", style = MaterialTheme.typography.labelSmall, color = Mint)
                        if (task.done && task.doneByName.isNotBlank()) Text("Erledigt von ${task.doneByName}", style = MaterialTheme.typography.labelSmall, color = Mint)
                    }
                    IconButton(onClick = { pendingDeletion = task }) { Icon(Icons.Default.DeleteOutline, "Löschen") }
                }
            }
        }
    }
    pendingDeletion?.let { task -> ConfirmDelete("${task.title} entfernen?") { yes -> if (yes) store.deleteTask(task); pendingDeletion = null } }
}

@Composable
private fun StockPage(data: HouseholdData, store: HouseholdStore, modifier: Modifier = Modifier) {
    var pendingDeletion by remember { mutableStateOf<StockItem?>(null) }
    val sorted = data.stock.sortedWith(compareBy<StockItem> { it.quantity > it.minimum }.thenBy { it.name })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Header("Vorratskammer", "Was ist noch da – was fehlt bald?") }
        if (sorted.isEmpty()) item { EmptyMessage("Noch keine Vorräte", "Mit + legst du den ersten Vorrat an.") }
        items(sorted, key = { it.id }) { stock ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stock.name, fontWeight = FontWeight.SemiBold)
                            Text("Minimum: ${stock.minimum} ${stock.unit}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        if (stock.quantity <= stock.minimum) Text("Nachkaufen", color = Color(0xFFB64029), style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { pendingDeletion = stock }) { Icon(Icons.Default.DeleteOutline, "Entfernen") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedIconButton(onClick = { store.changeStock(stock, -1) }, enabled = stock.quantity > 0) { Icon(Icons.Default.Remove, "Weniger") }
                        Text("${stock.quantity} ${stock.unit}", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                        OutlinedIconButton(onClick = { store.changeStock(stock, 1) }) { Icon(Icons.Default.Add, "Mehr") }
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { store.addShopping(stock.name, "1", stock.unit, "Vorrat", 0.0) }) { Text("Auf Liste") }
                    }
                }
            }
        }
    }
    pendingDeletion?.let { stock -> ConfirmDelete("${stock.name} entfernen?") { yes -> if (yes) store.deleteStock(stock); pendingDeletion = null } }
}

@Composable
private fun EmptyMessage(title: String, subtitle: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

@Composable
private fun ConfirmDelete(message: String, confirm: (Boolean) -> Unit) {
    AlertDialog(onDismissRequest = { confirm(false) }, title = { Text("Löschen?") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { confirm(true) }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirm(false) }) { Text("Abbrechen") } })
}

@Composable
private fun PickOne(label: String, options: List<String>, choice: String, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $choice", maxLines = 1, overflow = TextOverflow.Ellipsis); Icon(Icons.Default.ArrowDropDown, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { change(option); expanded = false }) }
        }
    }
}

@Composable
private fun AddShoppingDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("Stk") }
    var category by remember { mutableStateOf("Lebensmittel") }
    var price by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Artikel hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Artikel") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(qty, { qty = it.take(10) }, modifier = Modifier.weight(1f), label = { Text("Menge") }, singleLine = true)
                    OutlinedTextField(unit, { unit = it.take(12) }, modifier = Modifier.weight(1f), label = { Text("Einheit") }, singleLine = true)
                }
                PickOne("Kategorie", listOf("Lebensmittel", "Obst & Gemüse", "Getränke", "Drogerie", "Haushalt", "Vorrat", "Sonstiges"), category) { category = it }
                OutlinedTextField(price, { price = it }, label = { Text("Gesamtpreis des Eintrags (€), optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onAdd(name.trim(), qty.ifBlank { "1" }, unit.ifBlank { "Stk" }, category, price.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0)
        }) { Text("Hinzufügen") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun AddTaskDialog(data: HouseholdData, onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf("Alle") }
    var due by remember { mutableStateOf("Ohne Termin") }
    var repeat by remember { mutableStateOf("Nie") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Aufgabe erstellen") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Was ist zu tun?") }, singleLine = true)
            PickOne("Zuständig", listOf("Alle") + data.members.map { it.name }.distinct(), assignee) { assignee = it }
            PickOne("Fällig", listOf("Ohne Termin", "Heute", "Morgen", "In 7 Tagen"), due) { due = it }
            PickOne("Rhythmus", listOf("Nie", "Täglich", "Wöchentlich", "Monatlich"), repeat) { repeat = it }
        } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
            val day = when(due) { "Heute" -> LocalDate.now(); "Morgen" -> LocalDate.now().plusDays(1)
                "In 7 Tagen" -> LocalDate.now().plusDays(7); else -> null }
            onAdd(title.trim(), assignee, day?.toString() ?: "", repeat)
        }) { Text("Erstellen") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun AddStockDialog(onDismiss: () -> Unit, onAdd: (String, Int, Int, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var min by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("Stk") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Vorrat erfassen") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Artikelname") }, singleLine = true)
            OutlinedTextField(qty, { qty = it }, label = { Text("Aktuell vorhanden") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(min, { min = it }, label = { Text("Ab welcher Menge nachkaufen?") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(unit, { unit = it }, label = { Text("Einheit") }, singleLine = true)
        } }, confirmButton = { TextButton(enabled = name.isNotBlank() && qty.toIntOrNull() != null && min.toIntOrNull() != null, onClick = {
            onAdd(name.trim(), (qty.toIntOrNull() ?: 0).coerceAtLeast(0), (min.toIntOrNull() ?: 1).coerceAtLeast(0), unit.ifBlank { "Stk" })
        }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}
