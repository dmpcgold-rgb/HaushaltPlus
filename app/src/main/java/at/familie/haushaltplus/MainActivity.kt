package at.familie.haushaltplus

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
import android.content.ActivityNotFoundException
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
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

private val Ink = Color(0xFFF9F7FF)
private val Mint = Color(0xFFFF781E)
private val Canvas = Color(0xFF090B0F)
private val Panel = Color(0xFF191B23)
private val AccentPink = Color(0xFFFF5EA8)
private val AccentBlue = Color(0xFF55B8FF)
private val AccentTeal = Color(0xFF4ADBBB)
private val Muted = Color(0xFFB7B9C7)

class MainActivity : ComponentActivity() {
    private var requestedTab by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab = intent?.getIntExtra("open_tab", 0) ?: 0
        val local = LocalStore(this)
        if (!BuildConfig.FIREBASE_ENABLED) FreeNotifications.scheduleLocal(this)
        val cloud = if (BuildConfig.FIREBASE_ENABLED) CloudSession(this) else null
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Mint, onPrimary = Color(0xFF1D0E04),
                secondary = AccentPink, tertiary = AccentTeal,
                surface = Panel, onSurface = Ink,
                background = Canvas, onBackground = Ink,
                surfaceVariant = Color(0xFF292A35), onSurfaceVariant = Ink,
                outline = Color(0xFF6D5864), error = AccentPink
            )) {
                if (cloud == null) HouseholdScreen(local, null, requestedTab)
                else {
                    val state by cloud.state.collectAsState()
                    when {
                        !state.signedIn -> LoginScreen(cloud, state)
                        state.needsHousehold -> CreateOrJoinScreen(cloud, state)
                        state.store != null -> HouseholdScreen(state.store!!, cloud, requestedTab)
                        state.busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else -> CreateOrJoinScreen(cloud, state)
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab = intent.getIntExtra("open_tab", 0)
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
    var resetMessage by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(26.dp),
        verticalArrangement = Arrangement.Center) {
        Text("HAUSHALT+", style = MaterialTheme.typography.displayMedium, color = Mint, fontWeight = FontWeight.Black)
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
        if (!creating) TextButton(onClick = { session.resetPassword(email) { resetMessage = it } }) { Text("Passwort vergessen?") }
        if (resetMessage.isNotBlank()) Text(resetMessage, color = AccentTeal)
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
        if (state.error.startsWith("Kein Zugriff auf den gespeicherten Haushalt")) {
            OutlinedButton(onClick = session::unlinkUnavailableHome) { Text("Alte Verknüpfung entfernen") }
        }
        TextButton(onClick = session::logout) { Text("Abmelden") }
    }
}

private val pages = listOf("Start", "Einkauf", "Aufgaben", "Vorrat", "Mehr")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HouseholdScreen(store: HouseholdStore, session: CloudSession?, requestedTab: Int = 0) {
    val data by store.data.collectAsState()
    var selected by rememberSaveable { mutableIntStateOf(requestedTab) }
    LaunchedEffect(requestedTab) { selected = requestedTab.coerceIn(0, 4) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var adding by remember { mutableStateOf(false) }
    var activeListId by rememberSaveable { mutableStateOf("standard") }
    var addListOpen by remember { mutableStateOf(false) }
    var houseOpen by remember { mutableStateOf(false) }
    var invitation by remember { mutableStateOf("") }
    var scannedName by remember { mutableStateOf("") }
    var scanStatus by remember { mutableStateOf("") }
    var quickAdd by remember { mutableStateOf(false) }
    var recognized by remember { mutableStateOf<List<TemplateProduct>>(emptyList()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        recognized = V3Logic.voiceItems(spoken)
    }
    val startVoice: () -> Unit = {
        try {
            voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-AT")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Was soll auf die Einkaufsliste?")
            })
        } catch (_: ActivityNotFoundException) {
            scanStatus = "Keine Spracherkennung auf diesem Gerät verfügbar"
        }
    }
    val scanBarcode: () -> Unit = {
        GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { barcode ->
                val code = barcode.rawValue.orEmpty()
                if (code.isNotBlank()) {
                    scanStatus = "Produkt wird gesucht ..."
                    scope.launch {
                        val product = ProductLookup.lookup(code)
                        scannedName = product ?: "Barcode " + code
                        scanStatus = if (product == null) "Produkt nicht gefunden – Barcode kann bearbeitet werden" else ""
                        selected = 1
                        adding = true
                    }
                }
            }
            .addOnFailureListener { error -> scanStatus = "Scanner nicht verfügbar: " + (error.localizedMessage ?: "") }
    }
    Scaffold(
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Haushalt+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(data.name + " · " + (if (data.cloud) data.syncStatus else "Lokal gespeichert"),
                        style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                }
            }, actions = { IconButton(onClick = { houseOpen = true }) { Icon(Icons.Default.People, "Haushalt") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas, titleContentColor = Ink))
        },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                pages.forEachIndexed { idx, title ->
                    NavigationBarItem(selected = selected == idx, onClick = { selected = idx },
                        icon = { Icon(when(idx) { 0 -> Icons.Default.Home; 1 -> Icons.Default.ShoppingCart; 2 -> Icons.Default.CheckCircle; 3 -> Icons.Default.Inventory2; else -> Icons.Default.MoreHoriz }, title) },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint,
                            indicatorColor = Color(0xFF3A241B), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (selected in 1..3) adding = true else quickAdd = true },
                containerColor = Mint, contentColor = Color.Black) { Icon(Icons.Default.Add, "Hinzufügen") }
        }
    ) { padding ->
        when (selected) {
            0 -> OverviewPage(data, modifier = Modifier.padding(padding), onJump = { selected = it })
            1 -> ShoppingPage(data, store, Modifier.padding(padding), activeListId, { activeListId = it },
                { addListOpen = true }, scanBarcode, startVoice, scanStatus, onToggle = { item, checked ->
                    store.setShoppingDone(item, checked)
                    val vibrate = !context.getSharedPreferences("haushalt_v3_ui", Context.MODE_PRIVATE)
                        .getBoolean("reduce_motion", false)
                    if (vibrate) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            if (checked) "Eingekauft: " + item.name else "Wieder geöffnet: " + item.name,
                            actionLabel = "Rückgängig", withDismissAction = false,
                            duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) store.setShoppingDone(item, !checked)
                    }
                })
            2 -> TasksPage(data, store, Modifier.padding(padding))
            3 -> StockPage(data, store, Modifier.padding(padding))
            else -> ExtrasPage(data, store, session, Modifier.padding(padding))
        }
    }
    if (quickAdd) AlertDialog(onDismissRequest = { quickAdd = false },
        title = { Text("Schnell hinzufügen") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Einkaufsartikel", 2 to "Haushaltsaufgabe", 3 to "Vorratsartikel").forEach { (tab, label) ->
                OutlinedButton(onClick = { selected = tab; adding = true; quickAdd = false },
                    modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        } },
        confirmButton = { TextButton(onClick = { quickAdd = false }) { Text("Schließen") } }
    )
    if (recognized.isNotEmpty()) AlertDialog(
        onDismissRequest = { recognized = emptyList() },
        title = { Text("Spracheingabe prüfen") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Erkannte Artikel bitte vor dem Übernehmen prüfen:", color = Muted)
            recognized.forEach { product ->
                Text(product.quantity + " " + product.unit + " " + product.name)
            }
        } },
        confirmButton = { TextButton(onClick = {
            recognized.forEach { p -> store.addShopping(p.name, p.quantity, p.unit, p.category, 0.0, activeListId) }
            recognized = emptyList()
        }) { Text("Artikel hinzufügen") } },
        dismissButton = { TextButton(onClick = { recognized = emptyList() }) { Text("Abbrechen") } }
    )
    if (adding) when (selected) {
        1 -> AddShoppingDialog(initialName = scannedName,
            onDismiss = { adding = false; scannedName = ""; scanStatus = "" },
            onAdd = { n,q,u,c,p -> store.addShopping(n,q,u,c,p,activeListId); adding = false; scannedName = ""; scanStatus = "" })
        2 -> AddTaskDialog(data, onDismiss = { adding = false },
            onAdd = { t,a,d,r,rotate -> store.addTask(t,a,d,r,rotate); adding = false })
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
                } else Text("Diese Version speichert nur lokal. Die Familienfreigabe funktioniert in der Firebase-Version.")
                if (session != null) TextButton(onClick = { houseOpen = false; session.logout() }) { Text("Abmelden") }
            }
        }, confirmButton = { TextButton(onClick = { houseOpen = false }) { Text("Schließen") } }
    )
}

@Composable
private fun OverviewPage(data: HouseholdData, modifier: Modifier = Modifier, onJump: (Int) -> Unit) {
    val context = LocalContext.current
    val order = DashboardOptions.read(context)
    val missing = data.stock.count { it.quantity <= it.minimum }
    val activeShopping = data.shopping.filter { !it.archived }
    val open = activeShopping.count { !it.bought }
    val due = data.tasks.filter { !it.done && it.due.isNotBlank() && it.due <= LocalDate.now().toString() }
        .sortedBy { it.due }
    val month = java.time.YearMonth.now().toString()
    val spent = data.receipts.filter { it.date.startsWith(month) }.sumOf { it.total }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(
                brush = Brush.linearGradient(listOf(Color(0xFF63200A), Color(0xFF401B40), Color(0xFF142244))),
                shape = RoundedCornerShape(25.dp)
            ).padding(21.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("✦  DEIN ZUHAUSE", color = Color(0xFFFFBE96),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Alles im Griff.", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Einkaufen · Planen · Gemeinsam erledigen", color = Color(0xFFEEDAE7))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onJump(1) }) { Icon(Icons.Default.ShoppingCart, null); Text(" Einkauf") }
                        OutlinedButton(onClick = { onJump(2) }) { Icon(Icons.Default.CheckCircle, null); Text(" Aufgaben") }
                    }
                }
            }
            if (data.error.isNotBlank()) Text(data.error, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            if (data.cloud && data.syncStatus != "Synchronisiert") Text(
                "Cloud: " + data.syncStatus, color = AccentTeal, style = MaterialTheme.typography.bodySmall)
        }
        order.forEach { key ->
            if (DashboardOptions.visible(context, key)) {
                when(key) {
                    "shopping" -> item(key = "shopping") { DashboardCard("Einkauf", "$open offen",
                        "${activeShopping.count { it.bought }} bereits besorgt",
                        Icons.Default.ShoppingBasket) { onJump(1) } }
                    "tasks" -> item(key = "tasks") { DashboardCard("Aufgaben",
                        "${data.tasks.count { !it.done }} zu erledigen",
                        "${data.tasks.count { it.done }} abgeschlossen", Icons.Default.TaskAlt) { onJump(2) } }
                    "stock" -> item(key = "stock") { DashboardCard("Vorrat", "$missing Nachkäufe empfohlen",
                        "${data.stock.size} Artikel im Vorrat", Icons.Default.Inventory2) { onJump(3) } }
                    "today" -> if (due.isNotEmpty()) item(key = "today") {
                        ElevatedCard(onClick = { onJump(2) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("${due.size} Aufgaben heute oder überfällig", color = AccentPink,
                                    fontWeight = FontWeight.Bold)
                                due.take(3).forEach { Text("• " + it.title + " · " + it.assignee) }
                            }
                        }
                    }
                    "progress" -> item(key = "progress") {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Einkaufsfortschritt", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = {
                                    if (activeShopping.isEmpty()) 0f else
                                        activeShopping.count { it.bought }.toFloat() / activeShopping.size
                                }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Mint)
                                Text("${activeShopping.count { it.bought }} von ${activeShopping.size} Artikeln erledigt",
                                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    "meals" -> item(key = "meals") {
                        val today = LocalDate.now().toString()
                        val meals = data.meals.filter { it.date == today }
                        ElevatedCard(onClick = { onJump(4) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Heute essen", fontWeight = FontWeight.Bold, color = AccentPink)
                                Text(if (meals.isEmpty()) "Noch nichts geplant" else
                                    meals.joinToString(" · ") { it.slot + ": " + it.title })
                            }
                        }
                    }
                    "budget" -> item(key = "budget") {
                        ElevatedCard(onClick = { onJump(4) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Belege diesen Monat", fontWeight = FontWeight.Bold, color = AccentTeal)
                                Text("%.2f €".format(Locale.GERMANY, spent),
                                    style = MaterialTheme.typography.titleLarge)
                                Text("Monatsbudget: " + "%.2f €".format(Locale.GERMANY, data.monthlyBudget),
                                    style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(title: String, value: String, caption: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = when(title) {
                "Einkauf" -> Color(0xFF42261B)
                "Aufgaben" -> Color(0xFF3C1F39)
                else -> Color(0xFF183945)
            }) {
                Icon(icon, null, Modifier.padding(14.dp).size(28.dp), tint = when(title) {
                    "Einkauf" -> Mint
                    "Aufgaben" -> AccentPink
                    else -> AccentBlue
                })
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
    activeListId: String, onSelectList: (String) -> Unit, onNewList: () -> Unit,
    onScan: () -> Unit, onVoice: () -> Unit, scanStatus: String,
    onToggle: (ShoppingItem, Boolean) -> Unit) {
    var showBought by remember { mutableStateOf(true) }
    var shoppingMode by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var listExpanded by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<ShoppingItem?>(null) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var renaming by remember { mutableStateOf<ShoppingList?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("Alle") }
    val context = LocalContext.current
    val inList = data.shopping.filter { it.listId == activeListId &&
        (showArchived || !it.archived) }
    val archivableCount = data.shopping.count { it.listId == activeListId && it.bought && !it.archived }
    val categories = listOf("Alle") + inList.map { it.category }.distinct().sorted()
    val ordered = inList.filter { (showBought || !it.bought) &&
        (categoryFilter == "Alle" || it.category == categoryFilter) &&
        it.name.contains(search, ignoreCase = true) }
        .sortedWith(compareBy<ShoppingItem> { it.bought }.thenBy { it.category }.thenBy { it.name.lowercase() })
    val favorites = data.shopping.filter { it.bought }.groupingBy { it.name.trim() }.eachCount().entries
        .sortedByDescending { it.value }.take(4).map { it.key }
    val total = inList.filter { !it.bought }.sumOf { it.price }
    if (shoppingMode) {
        ShoppingModePage(data, activeListId, onToggle = onToggle, onClose = { shoppingMode = false })
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Header("Einkaufslisten", "Gemeinsam einkaufen, live abhaken")
            Button(onClick = { shoppingMode = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Storefront, "Einkaufsmodus")
                Spacer(Modifier.width(8.dp)); Text("Einkaufsmodus starten")
            }
            if (scanStatus.isNotBlank()) Text(scanStatus, color = AccentTeal, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, "Barcode scannen"); Text(" Barcode") }
                OutlinedButton(onClick = onVoice) {
                    Icon(Icons.Default.Mic, "Spracheingabe"); Text(" Diktieren")
                }
                OutlinedButton(onClick = {
                    val listName = data.lists.firstOrNull { it.id == activeListId }?.name ?: "Einkaufsliste"
                    val content = inList.filter { !it.bought }.sortedBy { it.category }
                        .joinToString("\n") { "☐ " + it.quantity + " " + it.unit + " " + it.name }
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, listName + "\n\n" +
                            content.ifBlank { "Keine offenen Artikel" })
                    }
                    context.startActivity(Intent.createChooser(share, "Einkaufsliste teilen"))
                }) { Icon(Icons.Default.Share, "Liste teilen"); Text(" Teilen") }
            }
            OutlinedTextField(search, { search = it }, label = { Text("Artikel suchen") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { showFilters = !showFilters }) {
                Icon(if (showFilters) Icons.Default.ExpandLess else Icons.Default.Tune, "Filter")
                Text(if (showFilters) " Weniger Filter" else " Kategorien filtern")
            }
            AnimatedVisibility(showFilters) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                    FilterChip(selected = (categoryFilter == category),
                        onClick = { categoryFilter = category }, label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mint, selectedLabelColor = Color.Black))
                    }
                }
            }
            if (favorites.isNotEmpty()) {
                Text("HÄUFIG GEKAUFT", color = AccentPink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    favorites.forEach { name ->
                        val old = data.shopping.firstOrNull { it.name == name }
                        AssistChip(onClick = { store.addShopping(name, "1", old?.unit ?: "Stk",
                            old?.category ?: "Lebensmittel", old?.price ?: 0.0, activeListId) },
                            label = { Text("+ " + name) })
                    }
                }
            }
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
                val currentList = data.lists.firstOrNull { it.id == activeListId }
                if (currentList != null && currentList.id != "standard")
                    IconButton(onClick = { renaming = currentList }) { Icon(Icons.Default.Edit, "Liste umbenennen") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Offen: ${inList.count { !it.bought }}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Richtwert: ${"%.2f".format(Locale.GERMANY, total)} €", color = Mint)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showBought, { showBought = it })
                Text("Erledigte Artikel anzeigen")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showArchived, { showArchived = it })
                Text("Archivierte anzeigen")
            }
            if (archivableCount > 0) TextButton(onClick = { confirmArchive = true }) {
                Icon(Icons.Default.Inventory2, "Archivieren")
                Spacer(Modifier.width(6.dp))
                Text("$archivableCount erledigte archivieren")
            }
        }
        if (ordered.isEmpty()) item { EmptyMessage("Noch nichts auf der Liste", "Mit + kannst du einen Artikel hinzufügen.") }
        items(ordered, key = { it.id }) { item ->
            ElevatedCard(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.bought, onCheckedChange = { onToggle(item, it) })
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Medium,
                            textDecoration = if (item.bought) TextDecoration.LineThrough else null)
                        Text("${item.quantity} ${item.unit} · ${item.category}" +
                            (if(item.price > 0) " · ${"%.2f".format(Locale.GERMANY, item.price)} €" else ""),
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        if (item.bought && item.doneByName.isNotBlank()) Text("Erledigt von ${item.doneByName}", style = MaterialTheme.typography.labelSmall, color = Mint)
                    }
                    var options by remember(item.id) { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreVert, "Artikeloptionen") }
                        DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                            DropdownMenuItem(text = { Text("Artikel bearbeiten") }, onClick = {
                                options = false; editing = item
                            }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                            if (item.bought) DropdownMenuItem(text = { Text("Erneut kaufen") }, onClick = {
                                options = false
                                store.addShopping(item.name, item.quantity, item.unit,
                                    item.category, item.price, activeListId)
                            }, leadingIcon = { Icon(Icons.Default.Replay, null) })
                            DropdownMenuItem(text = { Text("Löschen") }, onClick = {
                                options = false; pendingDeletion = item
                            }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
                        }
                    }
                }
            }
        }
    }
    if (confirmArchive) AlertDialog(
        onDismissRequest = { confirmArchive = false },
        title = { Text("Einkäufe archivieren?") },
        text = { Text("$archivableCount erledigte Artikel werden aus der aktiven Liste ausgeblendet. Sie bleiben für Monatsbudget und Einkaufshistorie gespeichert.") },
        confirmButton = { TextButton(onClick = {
            store.archiveCompletedShopping(activeListId)
            confirmArchive = false
            showArchived = false
        }) { Text("Archivieren") } },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Abbrechen") } }
    )
    pendingDeletion?.let { item -> ConfirmDelete("${item.name} entfernen?") { yes -> if (yes) store.deleteShopping(item); pendingDeletion = null } }
    editing?.let { item -> EditShoppingDialog(item, onDismiss = { editing = null }, onSave = {
        store.updateShopping(it); editing = null
    }) }
    renaming?.let { list ->
        var name by remember(list.id) { mutableStateOf(list.name) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Liste umbenennen") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                store.renameShoppingList(list, name); renaming = null
            }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Abbrechen") } })
    }

}

@Composable
private fun ShoppingModePage(data: HouseholdData, activeListId: String,
    onToggle: (ShoppingItem, Boolean) -> Unit, onClose: () -> Unit) {
    val all = data.shopping.filter { it.listId == activeListId && !it.archived }
    val open = all.filter { !it.bought }.sortedWith(
        compareBy<ShoppingItem> { it.category }.thenBy { it.name.lowercase() })
    val done = all.size - open.size
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 92.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, "Einkaufsmodus", tint = Mint)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Im Geschäft", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold)
                    Text("$done von ${all.size} eingekauft", color = Muted)
                }
                TextButton(onClick = onClose) { Text("Fertig") }
            }
            LinearProgressIndicator(progress = {
                if (all.isEmpty()) 0f else done.toFloat() / all.size
            }, modifier = Modifier.fillMaxWidth().height(10.dp), color = AccentTeal)
            Text("Große Felder – erledigte Artikel verschwinden automatisch.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (open.isEmpty()) item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, "Alles erledigt", tint = AccentTeal, modifier = Modifier.size(46.dp))
                    Text("Alles eingekauft!", style = MaterialTheme.typography.titleLarge)
                    Text("Beende den Einkaufsmodus oder füge weitere Produkte hinzu.", color = Muted)
                }
            }
        }
        items(open, key = { it.id }) { item ->
            Surface(onClick = { onToggle(item, true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                shape = RoundedCornerShape(18.dp), color = Panel) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF3D281B)) {
                        Icon(Icons.Default.CheckCircleOutline, "Einkaufen",
                            modifier = Modifier.padding(12.dp).size(28.dp), tint = Mint)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(item.quantity + " " + item.unit + " · " + item.category,
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksPage(data: HouseholdData, store: HouseholdStore, modifier: Modifier = Modifier) {
    var pendingDeletion by remember { mutableStateOf<HouseholdTask?>(null) }
    var editing by remember { mutableStateOf<HouseholdTask?>(null) }
    var filter by rememberSaveable { mutableStateOf("Offen") }
    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
    val filtered = data.tasks.filter { task ->
        when (filter) {
            "Heute" -> !task.done && task.due.isNotBlank() && task.due <= today
            "Meine" -> !task.done && (task.assignee.equals(data.ownName, true) ||
                task.assignee == "Alle")
            "Offen" -> !task.done
            else -> true
        }
    }
    val sorted = filtered.sortedWith(compareBy<HouseholdTask> { it.done }.thenBy { it.due.ifBlank { "9999" } })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Header("Haushaltsaufgaben", "Zuständig, fällig, erledigt")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Offen", "Heute", "Meine", "Alle").forEach { label ->
                    FilterChip(selected = filter == label, onClick = { filter = label },
                        label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Mint, selectedLabelColor = Color.Black))
                }
            }
            Text(sorted.size.toString() + if (sorted.size == 1) " Aufgabe" else " Aufgaben",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (sorted.isEmpty()) item {
            EmptyMessage("Keine passenden Aufgaben", "Wähle einen anderen Filter oder lege eine neue Aufgabe an.")
        }
        items(sorted, key = { it.id }) { task ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(task.done, { store.setTaskDone(task, it) })
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold, textDecoration = if (task.done) TextDecoration.LineThrough else null)
                        Text("Zuständig: ${task.assignee}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        if (task.due.isNotBlank()) Text("Fällig: ${task.due}" + if(!task.done && task.due < today) " · Überfällig" else "",
                            style = MaterialTheme.typography.bodySmall, color = if(!task.done && task.due < today) Color(0xFFB64029) else Color.Gray)
                        if (task.repeat != "Nie") Text("Wiederholt sich: " + task.repeat +
                            if (task.rotate) " · Wechselnde Zuständigkeit" else "",
                            style = MaterialTheme.typography.labelSmall, color = Mint)
                        if (task.done && task.doneByName.isNotBlank()) Text("Erledigt von ${task.doneByName}", style = MaterialTheme.typography.labelSmall, color = Mint)
                    }
                    var options by remember(task.id) { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreVert, "Aufgabenoptionen") }
                        DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                            DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = {
                                options = false; editing = task
                            }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                            DropdownMenuItem(text = { Text("Löschen") }, onClick = {
                                options = false; pendingDeletion = task
                            }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
                        }
                    }
                }
            }
        }
    }
    pendingDeletion?.let { task -> ConfirmDelete("${task.title} entfernen?") { yes -> if (yes) store.deleteTask(task); pendingDeletion = null } }
    editing?.let { task -> EditTaskDialog(task, data, onDismiss = { editing = null }, onSave = {
        store.updateTask(it); editing = null
    }) }

}

@Composable
private fun StockPage(data: HouseholdData, store: HouseholdStore, modifier: Modifier = Modifier) {
    var pendingDeletion by remember { mutableStateOf<StockItem?>(null) }
    var editing by remember { mutableStateOf<StockItem?>(null) }
    val sorted = data.stock.sortedWith(compareBy<StockItem> { it.quantity > it.minimum }.thenBy { it.name })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Header("Vorratskammer", "Was ist noch da – was fehlt bald?")
            TextButton(onClick = {
                data.stock.filter { it.quantity <= it.minimum }.forEach { stock ->
                    if (data.shopping.none { !it.bought && it.name.equals(stock.name, ignoreCase = true) }) {
                        store.addShopping(stock.name, "1", stock.unit, "Vorrat", 0.0)
                    }
                }
            }) { Icon(Icons.Default.AddShoppingCart, null); Text("Fehlende Produkte übernehmen") }
        }
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
                        IconButton(onClick = { editing = stock }) { Icon(Icons.Default.Edit, "Vorrat bearbeiten") }
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
    editing?.let { stock -> EditStockDialog(stock, onDismiss = { editing = null }, onSave = {
        store.updateStock(it); editing = null
    }) }

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
private fun AddShoppingDialog(initialName: String = "", onDismiss: () -> Unit, onAdd: (String, String, String, String, Double) -> Unit) {
    var name by remember(initialName) { mutableStateOf(initialName) }
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
private fun AddTaskDialog(data: HouseholdData, onDismiss: () -> Unit, onAdd: (String, String, String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf("Alle") }
    var due by remember { mutableStateOf("Ohne Termin") }
    var repeat by remember { mutableStateOf("Nie") }
    var rotate by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Aufgabe erstellen") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Was ist zu tun?") }, singleLine = true)
            PickOne("Zuständig", listOf("Alle") + data.members.map { it.name }.distinct(), assignee) { assignee = it }
            PickOne("Fällig", listOf("Ohne Termin", "Heute", "Morgen", "In 7 Tagen"), due) { due = it }
            PickOne("Rhythmus", listOf("Nie", "Täglich", "Wöchentlich", "Monatlich"), repeat) { repeat = it }
            if (repeat != "Nie") Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rotate, { rotate = it })
                Text("Zuständigkeit abwechseln", style = MaterialTheme.typography.bodySmall)
            }
        } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
            val day = when(due) { "Heute" -> LocalDate.now(); "Morgen" -> LocalDate.now().plusDays(1)
                "In 7 Tagen" -> LocalDate.now().plusDays(7); else -> null }
            onAdd(title.trim(), assignee, day?.toString() ?: "", repeat, rotate)
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

@Composable
private fun EditShoppingDialog(item: ShoppingItem, onDismiss: () -> Unit, onSave: (ShoppingItem) -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var quantity by remember(item.id) { mutableStateOf(item.quantity) }
    var unit by remember(item.id) { mutableStateOf(item.unit) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var price by remember(item.id) { mutableStateOf(item.price.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Artikel bearbeiten") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Artikelname") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(quantity, { quantity = it }, modifier = Modifier.weight(1f), label = { Text("Menge") })
                OutlinedTextField(unit, { unit = it }, modifier = Modifier.weight(1f), label = { Text("Einheit") })
            }
            PickOne("Kategorie", listOf("Lebensmittel", "Obst & Gemüse", "Getränke", "Drogerie", "Haushalt", "Vorrat", "Sonstiges"), category) { category = it }
            OutlinedTextField(price, { price = it }, label = { Text("Tatsächlicher / geplanter Gesamtpreis €") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onSave(item.copy(name = name.trim(), quantity = quantity.ifBlank { "1" }, unit = unit.ifBlank { "Stk" },
                category = category, price = (price.replace(',', '.').toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)))
        }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun EditTaskDialog(task: HouseholdTask, data: HouseholdData, onDismiss: () -> Unit, onSave: (HouseholdTask) -> Unit) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var assignee by remember(task.id) { mutableStateOf(task.assignee) }
    var due by remember(task.id) { mutableStateOf(task.due) }
    var repeat by remember(task.id) { mutableStateOf(task.repeat) }
    var rotate by remember(task.id) { mutableStateOf(task.rotate) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Aufgabe bearbeiten") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Aufgabe") })
            PickOne("Zuständig", listOf("Alle") + data.members.map { it.name }.distinct(), assignee) { assignee = it }
            OutlinedTextField(due, { due = it }, label = { Text("Datum (JJJJ-MM-TT), optional") }, singleLine = true)
            PickOne("Rhythmus", listOf("Nie", "Täglich", "Wöchentlich", "Monatlich"), repeat) { repeat = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rotate, { rotate = it }); Text("Zuständigkeit rotieren")
            }
        } },
        confirmButton = { TextButton(enabled = title.isNotBlank() && (due.isBlank() || runCatching { LocalDate.parse(due) }.isSuccess),
            onClick = { onSave(task.copy(title = title.trim(), assignee = assignee, due = due, repeat = repeat, rotate = rotate)) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun EditStockDialog(item: StockItem, onDismiss: () -> Unit, onSave: (StockItem) -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var qty by remember(item.id) { mutableStateOf(item.quantity.toString()) }
    var min by remember(item.id) { mutableStateOf(item.minimum.toString()) }
    var unit by remember(item.id) { mutableStateOf(item.unit) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Vorrat bearbeiten") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Artikelname") })
            OutlinedTextField(qty, { qty = it }, label = { Text("Bestand") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(min, { min = it }, label = { Text("Mindestbestand") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(unit, { unit = it }, label = { Text("Einheit") })
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank() && qty.toIntOrNull() != null && min.toIntOrNull() != null,
            onClick = { onSave(item.copy(name = name.trim(), quantity = (qty.toIntOrNull() ?: 0).coerceAtLeast(0),
                minimum = (min.toIntOrNull() ?: 0).coerceAtLeast(0), unit = unit)) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}
