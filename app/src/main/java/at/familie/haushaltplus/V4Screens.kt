package at.familie.haushaltplus

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.text.NumberFormat

private val OrangeV4 = Color(0xFFFF832D)
private val CyanV4 = Color(0xFF5CE5C8)
private val PinkV4 = Color(0xFFFF77B2)
private val GrayV4 = Color(0xFFBCBDD0)

@Composable
fun V4FeaturesPage(section: String, data: HouseholdData, store: HouseholdStore,
    onJump: (Int) -> Unit) {
    when (section) {
        "ocr" -> ReceiptOcrScreen(store)
        "expiry" -> ExpiryScreen(data, store, onJump)
        "prices" -> PriceHistoryScreen(data, store)
        "fairness" -> FairTasksScreen(data, store)
        "checklists" -> ChecklistScreen(data, store)
        "trash" -> RecycleBinScreen(data, store)
        "search" -> GlobalSearchScreen(data, onJump)
        "appearance" -> AppearanceScreen()
        "diagnostics" -> DiagnosticScreen(data)
    }
}

@Composable
private fun V4Heading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
    Text(subtitle, color = GrayV4, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ReceiptOcrScreen(store: HouseholdStore) {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var suggestion by remember { mutableStateOf<ReceiptSuggestion?>(null) }
    fun parseImage(uri: Uri) {
        busy = true
        feedback = "Bild wird lokal analysiert ..."
        try {
            val input = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    suggestion = ReceiptParser.parse(result.text)
                    feedback = "Texterkennung abgeschlossen – Daten bitte prüfen"
                }.addOnFailureListener { e ->
                    feedback = "Text konnte nicht erkannt werden: " +
                        (e.localizedMessage ?: "Unbekannter Fehler")
                }.addOnCompleteListener {
                    busy = false
                    recognizer.close()
                    file?.delete()
                    file = null
                }
        } catch (e: Exception) {
            busy = false
            feedback = "Bild konnte nicht gelesen werden: " + (e.localizedMessage ?: "")
            file?.delete()
            file = null
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = photoUri
        if (success && uri != null) parseImage(uri)
        else { feedback = "Foto abgebrochen"; file?.delete(); file = null }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) parseImage(uri)
    }

    V4Heading("Kassenbon scannen", "OCR auf dem Gerät. Es wird kein Foto in Firebase gespeichert.")
    Text("Geschäft, Datum und Gesamtbetrag werden als Vorschlag erkannt. Erst nach deiner Bestätigung wird ein Beleg gespeichert.",
        color = GrayV4, style = MaterialTheme.typography.bodyMedium)
    Button(enabled = !busy, onClick = {
        try {
            val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val tmp = File.createTempFile("receipt_", ".jpg", dir)
            file = tmp
            photoUri = FileProvider.getUriForFile(context,
                context.packageName + ".files", tmp)
            camera.launch(photoUri!!)
        } catch (e: Exception) { feedback = e.localizedMessage ?: "Kamera nicht verfügbar" }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Icon(Icons.Default.PhotoCamera, "Kassenbon fotografieren")
        Spacer(Modifier.width(8.dp)); Text("Kassenbon fotografieren")
    }
    OutlinedButton(enabled = !busy, onClick = { gallery.launch("image/*") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Icon(Icons.Default.Image, "Bild wählen"); Spacer(Modifier.width(8.dp))
        Text("Bild aus Galerie auswählen")
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (feedback.isNotBlank()) Text(feedback, color = CyanV4, style = MaterialTheme.typography.bodySmall)
    suggestion?.let { proposed ->
        var merchant by remember(proposed) { mutableStateOf(proposed.merchant) }
        var date by remember(proposed) { mutableStateOf(proposed.date) }
        var sum by remember(proposed) { mutableStateOf(proposed.total) }
        var note by remember(proposed) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { suggestion = null }, title = { Text("Beleg kontrollieren") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(proposed.warning, color = PinkV4, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(merchant, { merchant = it.take(100) }, label = { Text("Geschäft") })
                    OutlinedTextField(date, { date = it.take(10) }, label = { Text("Datum JJJJ-MM-TT") })
                    OutlinedTextField(sum, { sum = it.take(20) }, label = { Text("Tatsächlicher Gesamtbetrag €") })
                    OutlinedTextField(note, { note = it.take(250) }, label = { Text("Notiz (optional)") })
                }
            },
            confirmButton = { TextButton(enabled = merchant.isNotBlank() &&
                runCatching { LocalDate.parse(date) }.isSuccess &&
                ((sum.replace(',', '.').toDoubleOrNull() ?: -1.0) > 0.0),
                onClick = {
                    store.addReceipt(merchant, date, sum.replace(',', '.').toDoubleOrNull() ?: 0.0, note)
                    suggestion = null
                    feedback = "Beleg gespeichert"
                }) { Text("Geprüften Beleg speichern") } },
            dismissButton = { TextButton(onClick = { suggestion = null }) { Text("Abbrechen") } })
    }
}

@Composable
private fun ExpiryScreen(data: HouseholdData, store: HouseholdStore, onJump: (Int) -> Unit) {
    var disposal by remember { mutableStateOf<StockItem?>(null) }
    V4Heading("Haltbarkeit", "Lebensmittel frühzeitig verbrauchen statt wegwerfen")
    val products = data.stock.filter { V4Logic.expiryDays(it) != null }
        .sortedBy { V4Logic.expiryDays(it) }
    if (products.isEmpty()) Text("Noch keine MHDs erfasst. Im Reiter Vorrat kannst du ein Datum hinzufügen.", color = GrayV4)
    products.forEach { item ->
        val days = V4Logic.expiryDays(item) ?: 0L
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, null, tint = if (days <= 3) PinkV4 else CyanV4)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.Bold)
                    Text(when {
                        days < 0 -> "MHD seit " + -days + " Tagen überschritten"
                        days == 0L -> "MHD heute"
                        else -> "MHD in $days Tagen"
                    } + " · Bestand: " + item.quantity + " " + item.unit,
                        color = if (days <= 3) PinkV4 else GrayV4,
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { disposal = item }) {
                    Icon(Icons.Default.RemoveCircleOutline, "Vorrat aufgebraucht oder entsorgt")
                }
            }
        }
    }
    OutlinedButton(onClick = { onJump(3) }, modifier = Modifier.fillMaxWidth()) {
        Text("MHDs in Vorrat bearbeiten")
    }
    Text("Ein überschrittenes Mindesthaltbarkeitsdatum bedeutet nicht automatisch, dass ein Lebensmittel ungenießbar ist.",
        color = GrayV4, style = MaterialTheme.typography.bodySmall)
    disposal?.let { item ->
        AlertDialog(onDismissRequest = { disposal = null }, title = { Text("Bestand auf 0 setzen?") },
            text = { Text("Dadurch wird " + item.name + " als verbraucht oder entsorgt erfasst.") },
            confirmButton = { TextButton(onClick = {
                store.changeStock(item, -item.quantity)
                disposal = null
            }) { Text("Bestand auf 0") } },
            dismissButton = { TextButton(onClick = { disposal = null }) { Text("Abbrechen") } })
    }
}

@Composable
private fun PriceHistoryScreen(data: HouseholdData, store: HouseholdStore) {
    var add by remember { mutableStateOf(false) }
    var product by rememberSaveable { mutableStateOf("") }
    V4Heading("Eigene Preisvergleiche", "Vergleiche deine zuletzt selbst erfassten Einkaufspreise")
    OutlinedTextField(product, { product = it }, label = { Text("Produkt suchen") },
        leadingIcon = { Icon(Icons.Default.Search, "Suchen") }, modifier = Modifier.fillMaxWidth())
    val items = data.prices.filter {
        product.isBlank() || it.product.contains(product, true)
    }.sortedByDescending { it.date }
    val best = items.filter { it.unitPrice > 0.0 }.groupBy { it.product.lowercase() + "|" + it.unit.lowercase() }
    best.forEach { (key, prices) ->
        val cheapest = prices.minByOrNull { it.unitPrice } ?: return@forEach
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(cheapest.product + " / " + cheapest.unit, fontWeight = FontWeight.Bold)
                Text("Bisher günstigster erfasster Preis: " + euro(cheapest.unitPrice) +
                    " · " + cheapest.shop, color = CyanV4)
                Text(prices.size.toString() + " eigene Preise erfasst", color = GrayV4,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, "Preis erfassen"); Text(" Neuen Preis speichern")
    }
    Text("Preisverlauf", fontWeight = FontWeight.Bold)
    items.take(40).forEach { item ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.product + " · " + item.shop)
                Text(item.date + " / " + item.unit, color = GrayV4,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(euro(item.unitPrice), color = CyanV4)
            IconButton(onClick = { store.deletePrice(item) }) { Icon(Icons.Default.DeleteOutline, "Preis löschen") }
        }
        HorizontalDivider()
    }
    Text("Nur selbst erfasste historische Werte; keine Live-Angebote. Preise mit unterschiedlichen Einheiten werden nicht miteinander verglichen.",
        style = MaterialTheme.typography.bodySmall, color = GrayV4)
    if (add) {
        var name by remember { mutableStateOf(product) }
        var shop by remember { mutableStateOf("") }
        var sum by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("Stk") }
        var date by remember { mutableStateOf(LocalDate.now().toString()) }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("Preis erfassen") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text("Produkt") })
                OutlinedTextField(shop, { shop = it.take(100) }, label = { Text("Geschäft") })
                OutlinedTextField(sum, { sum = it }, label = { Text("Preis für 1 Einheit (€)") })
                OutlinedTextField(unit, { unit = it }, label = { Text("Einheit, z. B. kg") })
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("Datum JJJJ-MM-TT") })
            } },
            confirmButton = { TextButton(enabled = name.isNotBlank() && shop.isNotBlank() &&
                ((sum.replace(',', '.').toDoubleOrNull() ?: -1.0) > 0.0) &&
                runCatching { LocalDate.parse(date) }.isSuccess,
                onClick = {
                    store.addPrice(name, shop, sum.replace(',', '.').toDoubleOrNull() ?: 0.0, unit, date)
                    add = false
                }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Abbrechen") } })
    }
}

@Composable
private fun FairTasksScreen(data: HouseholdData, store: HouseholdStore) {
    val names = data.members.map { it.name }.filter(String::isNotBlank).distinct()
    val recommended = V4Logic.fairAssignee(data.members, data.tasks)
    var newTask by remember { mutableStateOf("") }
    V4Heading("Aufgaben fair verteilen", "Erledigte und offene Tätigkeiten je Familienmitglied")
    names.forEach { name ->
        val done = data.tasks.count { it.done && it.doneByName == name }
        val open = data.tasks.count { !it.done && it.assignee == name }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = CyanV4)
                Spacer(Modifier.width(9.dp))
                Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("$done erledigt · $open offen", color = GrayV4)
            }
        }
    }
    Text("Empfohlene nächste Zuständigkeit: $recommended", color = CyanV4)
    OutlinedTextField(newTask, { newTask = it.take(140) },
        label = { Text("Neue Aufgabe") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = newTask.isNotBlank() && names.isNotEmpty(), onClick = {
        store.addTask(newTask.trim(), recommended, "", "Nie"); newTask = ""
    }, modifier = Modifier.fillMaxWidth()) { Text("Aufgabe fair zuteilen") }
    Text("Die Empfehlung berücksichtigt erledigte und noch offene Aufgaben. Neue Aufgaben werden nicht heimlich umverteilt.",
        color = GrayV4, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChecklistScreen(data: HouseholdData, store: HouseholdStore) {
    var title by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf("") }
    V4Heading("Haushalts-Checklisten", "Für Urlaub, Geburtstag, Großputz oder andere Abläufe")
    OutlinedTextField(title, { title = it.take(100) }, label = { Text("Checklistenname") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(tasks, { tasks = it.take(2500) },
        label = { Text("Eine Aufgabe pro Zeile") }, minLines = 3, maxLines = 6,
        modifier = Modifier.fillMaxWidth())
    Button(enabled = title.isNotBlank() && tasks.lines().any(String::isNotBlank), onClick = {
        store.saveChecklist(title.trim(), tasks.lines().map(String::trim).filter(String::isNotBlank).take(80))
        title = ""; tasks = ""
    }, modifier = Modifier.fillMaxWidth()) { Text("Checkliste speichern") }
    HorizontalDivider()
    if (data.checklists.isEmpty()) Text("Noch keine Checklisten gespeichert.", color = GrayV4)
    data.checklists.forEach { list ->
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(list.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { store.deleteChecklist(list) }) {
                        Icon(Icons.Default.DeleteOutline, "Checkliste löschen")
                    }
                }
                Text(list.tasks.take(5).joinToString(" · "), color = GrayV4,
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    val assigned = data.tasks.toMutableList()
                    list.tasks.forEach { name ->
                        val person = V4Logic.fairAssignee(data.members, assigned)
                        store.addTask(name, person, "", "Nie")
                        assigned += HouseholdTask(title = name, assignee = person)
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(list.tasks.size.toString() + " Aufgaben erstellen")
                }
            }
        }
    }
}

@Composable
private fun RecycleBinScreen(data: HouseholdData, store: HouseholdStore) {
    var delete by remember { mutableStateOf<TrashEntry?>(null) }
    V4Heading("Papierkorb", "Einkäufe, Aufgaben, Rezepte und Vorräte wiederherstellen")
    Text("Einträge sind bis zu 30 Tage wiederherstellbar. Endgültiges Löschen ist nicht rückgängig zu machen.",
        color = GrayV4, style = MaterialTheme.typography.bodySmall)
    val entries = data.trash.filterNot(V4TrashCodec::isExpired).sortedByDescending { it.deletedAt }
    if (entries.isEmpty()) Text("Papierkorb ist leer.", color = GrayV4)
    entries.take(100).forEach { item ->
        val days = (30 - ((System.currentTimeMillis()-item.deletedAt)/86_400_000L)).coerceIn(0,30)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(item.label, fontWeight = FontWeight.Bold)
                Text(item.type + " · noch " + days + " Tage wiederherstellbar",
                    color = GrayV4, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { store.restoreTrash(item) }) {
                        Icon(Icons.Default.Restore, "Wiederherstellen"); Text(" Wiederherstellen")
                    }
                    TextButton(onClick = { delete = item }) {
                        Icon(Icons.Default.DeleteForever, "Endgültig löschen"); Text(" Löschen", color = PinkV4)
                    }
                }
            }
        }
    }
    delete?.let { item ->
        AlertDialog(onDismissRequest = { delete = null },
            title = { Text("Endgültig löschen?") },
            text = { Text(item.label + " kann danach nicht mehr wiederhergestellt werden.") },
            confirmButton = { TextButton(onClick = {
                store.purgeTrash(item); delete = null
            }) { Text("Endgültig löschen", color = PinkV4) } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Abbrechen") } })
    }
}

@Composable
private fun GlobalSearchScreen(data: HouseholdData, onJump: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    V4Heading("Alles durchsuchen", "Einkäufe, Vorräte, Aufgaben, Rezepte und Ausgaben")
    OutlinedTextField(query, { query = it.take(100) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, label = { Text("Suchbegriff") },
        leadingIcon = { Icon(Icons.Default.Search, "Suchen") })
    if (query.isBlank()) {
        Text("Gib einen Artikel, eine Aufgabe, einen Rezeptnamen oder ein Geschäft ein.",
            color = GrayV4)
        return
    }
    data.shopping.filter { it.name.contains(query, true) }.take(15).forEach { item ->
        V4SearchHit("Einkauf", item.name + " · " + item.quantity + " " + item.unit, 1, onJump)
    }
    data.tasks.filter { it.title.contains(query, true) }.take(15).forEach { item ->
        V4SearchHit("Aufgabe", item.title + " · " + item.assignee, 2, onJump)
    }
    data.stock.filter { it.name.contains(query, true) }.take(15).forEach { item ->
        V4SearchHit("Vorrat", item.name + " · " + item.quantity + " " + item.unit, 3, onJump)
    }
    data.recipes.filter { it.name.contains(query, true) || it.ingredients.contains(query, true) }
        .take(10).forEach { V4SearchHit("Rezept", it.name, 4, onJump) }
    data.receipts.filter { it.merchant.contains(query, true) || it.note.contains(query, true) }
        .take(10).forEach { V4SearchHit("Beleg", it.merchant + " · " + euro(it.total), 4, onJump) }
    data.meals.filter { it.title.contains(query, true) }.take(10)
        .forEach { V4SearchHit("Essensplan", it.title + " · " + it.date, 4, onJump) }
    data.checklists.filter { it.name.contains(query, true) }.take(10)
        .forEach { V4SearchHit("Checkliste", it.name, 4, onJump) }
    Text("Treffer öffnen die entsprechende Hauptansicht; bei Rezepten und Belegen die Übersicht Mehr.",
        color = GrayV4, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun V4SearchHit(category: String, value: String, tab: Int, onJump: (Int) -> Unit) {
    ElevatedCard(onClick = { onJump(tab) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(category.uppercase(), color = OrangeV4, style = MaterialTheme.typography.labelSmall)
                Text(value, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, "Öffnen", tint = OrangeV4)
        }
    }
}

@Composable
private fun AppearanceScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("haushalt_v4_theme", Context.MODE_PRIVATE) }
    var accent by remember { mutableStateOf(prefs.getString("accent", "Orange") ?: "Orange") }
    var contrast by remember { mutableStateOf(prefs.getBoolean("high_contrast", false)) }
    var compact by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    V4Heading("Design und Bedienung", "RAUS0-Neon-Design auf deinen Alltag abstimmen")
    Text("Akzentfarbe", fontWeight = FontWeight.Bold)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf("Orange", "Pink", "Türkis", "Blau").forEach { name ->
            FilterChip(selected = name == accent, onClick = {
                accent = name; prefs.edit().putString("accent", name).apply()
            }, label = { Text(name) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Hoher Kontrast", fontWeight = FontWeight.Bold)
            Text("Stärkere Text- und Rahmenkontraste", color = GrayV4,
                style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = contrast, onCheckedChange = {
            contrast = it; prefs.edit().putBoolean("high_contrast", it).apply()
        })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Kompakte Darstellung", fontWeight = FontWeight.Bold)
            Text("Kleinere Kartenabstände, nicht kleinere Schrift", color = GrayV4,
                style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = compact, onCheckedChange = {
            compact = it; prefs.edit().putBoolean("compact", it).apply()
        })
    }
    Text("Schriftgröße und Vorlesefunktionen folgen den Android-Barrierefreiheitseinstellungen. Farbanpassungen werden nach einem Neustart der App vollständig übernommen.",
        color = GrayV4, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DiagnosticScreen(data: HouseholdData) {
    val context = LocalContext.current
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork
    val connected = network != null && cm.getNetworkCapabilities(network)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val whenSynced = if (data.lastSyncedAt <= 0) "Noch nicht bestätigt"
        else java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
            .format(java.util.Date(data.lastSyncedAt))
    val report = "Haushalt+ Diagnose v0.4\n" +
        "Cloud-Modus: " + data.cloud + "\n" +
        "Netzwerk verfügbar: " + connected + "\n" +
        "Sync-Status: " + data.syncStatus + "\n" +
        "Letzte bestätigte Synchronisierung: " + whenSynced + "\n" +
        "Ausstehende Einkaufsänderungen: " + data.pendingWrites + "\n" +
        "Einkaufseinträge: " + data.shopping.size + "\n" +
        "Aufgaben: " + data.tasks.size + "\n" +
        "Vorräte: " + data.stock.size + "\n" +
        "Fehler vorhanden: " + data.error.isNotBlank()
    V4Heading("Diagnose", "Verbindungen und lokale App-Zustände prüfen")
    listOf(
        "Netzwerk" to if(connected) "Verfügbar" else "Keine Verbindung erkannt",
        "Speicherung" to if(data.cloud) "Firebase" else "Nur lokal",
        "Synchronisierung" to data.syncStatus,
        "Letzte bestätigte Synchronisierung" to whenSynced,
        "Wartende Einkaufsänderungen" to if(data.pendingWrites) "Ja" else "Nicht erkannt",
        "Haushaltsdaten" to ("${data.shopping.size} Artikel, ${data.tasks.size} Aufgaben, ${data.stock.size} Vorräte")
    ).forEach { (key, value) ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(key, color = GrayV4, style = MaterialTheme.typography.bodySmall)
                Text(value, fontWeight = FontWeight.Bold)
            }
        }
    }
    if(data.error.isNotBlank()) {
        Text("Letzter App-Fehler:", color = PinkV4, fontWeight = FontWeight.Bold)
        Text(data.error.take(400), style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "Diagnosebericht teilen"))
    }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Share, "Diagnose exportieren")
        Spacer(Modifier.width(8.dp))
        Text("Anonymen Bericht teilen")
    }
    Text("Der Bericht enthält weder Einkaufsartikel-Namen noch E-Mail, UID, Passwörter oder Einladungs-Codes. Die Netzwerkanzeige beweist nicht, dass Firebase erreichbar ist.",
        color = GrayV4, style = MaterialTheme.typography.bodySmall)
}

private fun euro(value: Double) = NumberFormat.getCurrencyInstance(Locale.GERMANY).format(value)
