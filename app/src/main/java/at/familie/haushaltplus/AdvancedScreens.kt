package at.familie.haushaltplus

import android.content.Intent
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Neon = Color(0xFFFF7519)
private val Pink = Color(0xFFFF5FA5)
private val Blue = Color(0xFF50B7FF)
private val Teal = Color(0xFF45DFB1)
private val Ink = Color(0xFF16171C)
private val Muted = Color(0xFFBFC0C8)

@Composable
fun ExtrasPage(
    data: HouseholdData,
    store: HouseholdStore,
    session: CloudSession?,
    modifier: Modifier = Modifier,
    onJump: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<String?>(null) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw java.io.IOException("Speicherort ist nicht beschreibbar")
            stream.use { it.write(BackupCodec.encode(data).toByteArray(Charsets.UTF_8)) }
            status = "Backup erfolgreich gespeichert"
        } catch (e: Exception) { status = e.localizedMessage ?: "Speichern fehlgeschlagen" }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("Datei konnte nicht gelesen werden")
            val text = stream.bufferedReader().use { it.readText().take(2_000_001) }
            if (text.length > 2_000_000) {
                imported = null
                status = "Sicherung ist zu groß (maximal 2 MB)"
            } else imported = text
        } catch (e: Exception) { status = e.localizedMessage ?: "Datei konnte nicht gelesen werden" }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (section.isNotEmpty()) {
            TextButton(onClick = { section = "" }) {
                Icon(Icons.Default.ArrowBack, "Zurück")
                Spacer(Modifier.width(7.dp))
                Text("Zur Übersicht")
            }
        }
        when(section) {
            "" -> {
                Text("MEIN HAUSHALT", color = Neon, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                Text("Alles im Griff", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text("Gemeinsam planen, einkaufen und organisieren.", color = Muted)
                Spacer(Modifier.height(2.dp))
                ExtraTile(Icons.Default.Restaurant, "Essensplan", "Mahlzeiten und Zutaten für die Woche", Pink) { section = "meals" }
                ExtraTile(Icons.Default.Bookmarks, "Einkaufsvorlagen", "Listen für regelmäßige Einkäufe", Neon) { section = "templates" }
                ExtraTile(Icons.Default.ReceiptLong, "Kassenbelege", "Beträge und Geschäfte vergleichen", Teal) { section = "receipts" }
                ExtraTile(Icons.Default.MenuBook, "Rezeptbuch", "Portionen umrechnen und Zutaten einkaufen", Pink) { section = "recipes" }
                ExtraTile(Icons.Default.CalendarMonth, "Familienkalender", "Woche und Monat gemeinsam planen", Blue) { section = "calendar" }
                ExtraTile(Icons.Default.History, "Aktivitätsverlauf", "Änderungen in der Familie nachvollziehen", Teal) { section = "activity" }
                ExtraTile(Icons.Default.DashboardCustomize, "Startseite gestalten", "Kacheln sortieren und ausblenden", Neon) { section = "dashboard" }
                ExtraTile(Icons.Default.Paid, "Budget & Ausgaben", "Einkäufe und Monatslimit überblicken", Teal) { section = "budget" }
                ExtraTile(Icons.Default.People, "Familie & Einladungen", "Haushaltsmitglieder und Freigaben", Blue) { section = "family" }
                ExtraTile(Icons.Default.NotificationsActive, "Benachrichtigungen", "Einkäufe und Aufgaben auswählen", Neon) { section = "notifications" }
                ExtraTile(Icons.Default.PhotoCamera, "Kassenbon scannen", "Beleg fotografieren und lokal erkennen", Orange) { section = "ocr" }
                ExtraTile(Icons.Default.Event, "Mindesthaltbarkeit", "Ablaufende Vorräte und Lebensmittel", Pink) { section = "expiry" }
                ExtraTile(Icons.Default.PriceChange, "Eigener Preisvergleich", "Preise je Geschäft und Produkt vergleichen", Teal) { section = "prices" }
                ExtraTile(Icons.Default.Groups, "Faire Aufgabenverteilung", "Aufgaben zwischen Mitgliedern ausbalancieren", Blue) { section = "fairness" }
                ExtraTile(Icons.Default.Checklist, "Haushalts-Checklisten", "Vorlagen für wiederkehrende Abläufe", Neon) { section = "checklists" }
                ExtraTile(Icons.Default.RestoreFromTrash, "Papierkorb", "Versehentlich gelöschte Einträge retten", Pink) { section = "trash" }
                ExtraTile(Icons.Default.ManageSearch, "Alles durchsuchen", "Einkauf, Aufgaben, Rezepte und Belege", Blue) { section = "search" }
                ExtraTile(Icons.Default.Palette, "Design & Bedienung", "Akzente, Kontrast und kompakte Ansichten", Neon) { section = "appearance" }
                ExtraTile(Icons.Default.MonitorHeart, "Diagnose & Fehler", "Synchronisierung und Geräteverbindung prüfen", Teal) { section = "diagnostics" }
                ExtraTile(Icons.Default.SaveAlt, "Sicherung & Wiederherstellung", "Deine Daten in eine JSON-Datei exportieren", Pink) { section = "backup" }
                ExtraTile(Icons.Default.Security, "Konto & Sicherheit", "Passwort und Konto verwalten", Teal) { section = "account" }
            }
            "meals" -> MealsSection(data, store)
            "budget" -> BudgetSection(data, store)
            "family" -> FamilySection(data, store)
            "notifications" -> NotificationsSection()
            "backup" -> {
                SectionTitle("Datensicherung", "Dateien bleiben in deinem ausgewählten Speicherort.")
                Text("Export enthält Listen, Einkäufe, Aufgaben, Vorräte, Mahlzeiten und Budget. Keine Passwörter.", color = Muted)
                Button(onClick = { saveFile.launch("HaushaltPlus-" + LocalDate.now() + ".json") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sicherung exportieren")
                }
                OutlinedButton(onClick = { openFile.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sicherung importieren")
                }
                Text("Import ergänzt fehlende Artikel und Aufgaben, statt vorhandene Daten zu löschen. Erledigtstatus und ursprüngliche Listen-IDs werden bei einem Import in einen anderen Haushalt nicht übertragen.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            "account" -> AccountSection(data, session)
            "templates", "receipts", "recipes", "calendar", "activity", "dashboard" ->
                V3FeaturesPage(data, store, section)
            "ocr", "expiry", "prices", "fairness", "checklists", "trash", "search",
            "appearance", "diagnostics" -> V4FeaturesPage(section, data, store, onJump)
        }
        if (status.isNotBlank()) Text(status, color = Teal)
        Spacer(Modifier.height(90.dp))
    }
    imported?.let { json ->
        AlertDialog(
            onDismissRequest = { imported = null },
            title = { Text("Daten importieren?") },
            text = { Text("Fehlende Einträge aus der Datei werden zu deinem Haushalt hinzugefügt. Bestehende Einträge bleiben erhalten.") },
            confirmButton = {
                TextButton(onClick = {
                    status = BackupCodec.importInto(json, store, data)
                    imported = null
                }) { Text("Importieren") }
            },
            dismissButton = { TextButton(onClick = { imported = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun ExtraTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = tint.copy(alpha = 0.18f)) {
                Icon(icon, null, modifier = Modifier.padding(13.dp).size(24.dp), tint = tint)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, "Öffnen", tint = tint)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MealsSection(data: HouseholdData, store: HouseholdStore) {
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var add by remember { mutableStateOf(false) }
    SectionTitle("Unser Essensplan", "Sieben Tage gemeinsam planen")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..6).forEach { offset ->
            val date = LocalDate.now().plusDays(offset.toLong()).toString()
            FilterChip(selected = date == day, onClick = { day = date },
                label = { Text(if (offset == 0) "Heute" else LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE dd.MM", Locale.GERMAN))) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon, selectedLabelColor = Color.Black))
        }
    }
    val entries = data.meals.filter { it.date == day }.sortedBy { it.slot }
    if (entries.isEmpty()) Text("Für diesen Tag ist noch kein Essen geplant.", color = Muted)
    entries.forEach { item ->
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.slot.uppercase(), color = Pink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if(item.ingredients.isNotBlank()) Text("Zutaten: " + item.ingredients, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        item.ingredients.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }.forEach {
                            store.addShopping(it, "1", "Stk", "Lebensmittel", 0.0, "standard")
                        }
                    }) { Icon(Icons.Default.AddShoppingCart, null); Text("Zutaten zur Liste") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { store.deleteMeal(item) }) { Icon(Icons.Default.DeleteOutline, "Mahlzeit löschen") }
                }
            }
        }
    }
    Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Mahlzeit planen")
    }
    if (add) {
        var title by remember { mutableStateOf("") }
        var ingredients by remember { mutableStateOf("") }
        var slot by remember { mutableStateOf("Abendessen") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("Essen für " + day) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Frühstück", "Mittagessen", "Abendessen", "Snack").forEach { label ->
                        FilterChip(selected = slot == label, onClick = { slot = label }, label = { Text(label) })
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Gericht") }, singleLine = true)
                OutlinedTextField(ingredients, { ingredients = it }, label = { Text("Zutaten, durch Komma getrennt") }, minLines = 2)
            } },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = {
                store.addMeal(day, slot, title, ingredients); add = false
            }) { Text("Planen") } },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Abbrechen") } })
    }
}

@Composable
private fun BudgetSection(data: HouseholdData, store: HouseholdStore) {
    val month = YearMonth.now().toString()
    val bought = data.shopping.filter { it.bought && it.boughtAt.startsWith(month) }
    val spent = bought.sumOf { it.price }
    val categories = bought.filter { it.price > 0.0 }.groupBy { it.category }
        .mapValues { (_, items) -> items.sumOf { it.price } }
        .toList().sortedByDescending { it.second }
    val withoutPrice = bought.count { it.price <= 0.0 }
    val budget = data.monthlyBudget
    val number = java.text.NumberFormat.getCurrencyInstance(Locale.GERMANY)
    var editing by remember { mutableStateOf(false) }
    SectionTitle("Haushaltsbudget", "Ausgaben im laufenden Monat")
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("DIESEN MONAT", color = Teal, fontWeight = FontWeight.Bold)
            Text(number.format(spent), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            Text(if (budget > 0) "Monatslimit: " + number.format(budget) else "Noch kein Monatslimit festgelegt", color = Muted)
            if (budget > 0) {
                LinearProgressIndicator(progress = { (spent / budget).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(9.dp), color = if(spent > budget) Pink else Teal)
                Text(if (spent <= budget) "Verfügbar: " + number.format(budget - spent)
                    else "Über Budget: " + number.format(spent - budget), color = if(spent > budget) Pink else Teal)
            }
            OutlinedButton(onClick = { editing = true }) { Text("Monatsbudget festlegen") }
        }
    }
    Text("Ausgaben nach Kategorie", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    if (categories.isEmpty()) Text("Sobald du Preise einträgst, siehst du hier deine Ausgaben nach Kategorien.", color = Muted)
    categories.forEach { (name, sum) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(number.format(sum), color = Teal, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { if (spent > 0.0) (sum / spent).toFloat().coerceIn(0f, 1f) else 0f },
            color = Neon, modifier = Modifier.fillMaxWidth().height(5.dp)
        )
    }
    if (withoutPrice > 0) Text("$withoutPrice gekaufte Artikel ohne Preis werden nicht in der Summe berücksichtigt.",
        color = Muted, style = MaterialTheme.typography.bodySmall)
    HorizontalDivider(color = Color(0xFF32333B))
    Text("Gebuchte Einkäufe", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    if (bought.isEmpty()) Text("Noch keine Einkäufe mit Datum in diesem Monat.", color = Muted)
    bought.sortedByDescending { it.boughtAt }.forEach {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(it.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(number.format(it.price), color = Teal)
        }
        HorizontalDivider(color = Color(0xFF333239))
    }
    Text("Preise stammen aus den Einkaufsartikeln. Den tatsächlichen Kassenbetrag kannst du beim Artikel über Bearbeiten eintragen.", color = Muted, style = MaterialTheme.typography.bodySmall)
    if (editing) {
        var amount by remember { mutableStateOf(if(budget == 0.0) "" else budget.toString()) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Monatslimit") },
            text = { OutlinedTextField(amount, { amount = it }, label = { Text("Betrag in €") }, singleLine = true) },
            confirmButton = { TextButton(enabled = amount.replace(',', '.').toDoubleOrNull() != null, onClick = {
                store.setBudget(amount.replace(',', '.').toDoubleOrNull() ?: 0.0); editing = false
            }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Abbrechen") } })
    }
}

@Composable
private fun FamilySection(data: HouseholdData, store: HouseholdStore) {
    val context = LocalContext.current
    val owner = !data.cloud || (data.ownUid.isNotBlank() && data.ownerUid == data.ownUid)
    var code by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf<Member?>(null) }
    var transfer by remember { mutableStateOf<Member?>(null) }
    SectionTitle("Unsere Familie", "Mitglieder und Einladungscodes")
    data.members.forEach { m ->
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = Blue.copy(alpha = .2f)) {
                    Icon(Icons.Default.Person, null, Modifier.padding(10.dp), tint = Blue)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, fontWeight = FontWeight.Bold)
                    Text(if (m.uid == data.ownerUid) "Haushaltsverwaltung" else "Mitglied",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                if(owner && m.uid != data.ownerUid && data.cloud) {
                    IconButton(onClick = { transfer = m }) { Icon(Icons.Default.AdminPanelSettings, "Verwaltung übertragen") }
                    IconButton(onClick = { remove = m }) { Icon(Icons.Default.PersonRemove, "Mitglied entfernen") }
                }
            }
        }
    }
    if (data.cloud) {
        Button(onClick = { store.createInvite { code = it } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Einladungscode erstellen")
        }
        if(code.isNotBlank()) {
            Text("Einladung: " + code, color = Neon, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Tritt unserem Haushalt+ bei. Einladungscode: " + code)
                    }
                    context.startActivity(Intent.createChooser(send, "Einladung teilen"))
                }) { Text("Teilen") }
                if(owner) TextButton(onClick = { store.revokeInvite(code) { info = it; if(it.startsWith("Einladung widerrufen")) code = "" } }) {
                    Text("Widerrufen", color = Pink)
                }
            }
            Text("Code 30 Tage gültig. Nur mit einer angemeldeten Person teilen.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    } else Text("Familienfreigabe benötigt den Firebase-Modus.", color = Muted)
    if(info.isNotBlank()) Text(info, color = Teal)
    remove?.let { m ->
        AlertDialog(onDismissRequest = { remove = null }, title = { Text("Mitglied entfernen?") },
            text = { Text("Der Zugang von " + m.name + " zu den gemeinsamen Haushaltsdaten wird gesperrt.") },
            confirmButton = { TextButton(onClick = { store.removeMember(m.uid); remove = null }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Abbrechen") } })
    }
    transfer?.let { m ->
        AlertDialog(onDismissRequest = { transfer = null }, title = { Text("Haushaltsverwaltung übertragen?") },
            text = { Text(m.name + " erhält die Eigentümerschaft. Nur diese Person kann danach das Budget und Mitglieder verwalten.") },
            confirmButton = { TextButton(onClick = {
                store.transferOwnership(m.uid) { info = it }
                transfer = null
            }) { Text("Übertragen") } },
            dismissButton = { TextButton(onClick = { transfer = null }) { Text("Abbrechen") } })
    }
}

@Composable
private fun NotificationsSection() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("haushalt_notifications", android.content.Context.MODE_PRIVATE) }
    var permissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    var shopping by remember { mutableStateOf(prefs.getBoolean("notify_shopping", true)) }
    var tasks by remember { mutableStateOf(prefs.getBoolean("notify_tasks", true)) }
    var family by remember { mutableStateOf(prefs.getBoolean("notify_family", true)) }
    var due by remember { mutableStateOf(prefs.getBoolean("notify_due", true)) }
    var expiry by remember { mutableStateOf(prefs.getBoolean("notify_expiry", true)) }
    var eco by remember { mutableStateOf(prefs.getBoolean("eco_mode", true)) }
    var daysBeforeExpiry by remember {
        mutableIntStateOf(prefs.getInt("expiry_before_days", 3))
    }
    var quiet by remember { mutableStateOf(prefs.getBoolean("quiet_enabled", true)) }
    var summary by remember { mutableStateOf(prefs.getBoolean("notify_summary", true)) }
    var before by remember { mutableIntStateOf(prefs.getInt("notify_before_days", 1)) }
    var from by remember { mutableStateOf(prefs.getString("quiet_start", "22:00") ?: "22:00") }
    var until by remember { mutableStateOf(prefs.getString("quiet_end", "07:00") ?: "07:00") }
    SectionTitle("Benachrichtigungen", "Du entscheidest, wofür dein Handy klingelt.")
    if (!permissionGranted) {
        ElevatedCard(shape = RoundedCornerShape(17.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Meldungen noch nicht freigegeben", fontWeight = FontWeight.Bold, color = Neon)
                Text("Damit neue Einkäufe und fällige Aufgaben als Handy-Benachrichtigung erscheinen, " +
                    "muss Android die Erlaubnis erhalten. Deine Listen funktionieren auch ohne diese Berechtigung.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else permissionGranted = true
                }) { Text("Benachrichtigungen erlauben") }
            }
        }
    } else {
        Text("Handy-Benachrichtigungen erlaubt", color = Teal,
            style = MaterialTheme.typography.bodySmall)
    }
    SettingToggle("Einkaufsliste", "Neue und erledigte Produkte", shopping) {
        shopping = it; prefs.edit().putBoolean("notify_shopping", it).apply()
    }
    SettingToggle("Haushaltsaufgaben", "Erledigte und erneut geöffnete Aufgaben", tasks) {
        tasks = it; prefs.edit().putBoolean("notify_tasks", it).apply()
    }
    SettingToggle("Familie", "Änderungen bei Mitgliedern", family) {
        family = it; prefs.edit().putBoolean("notify_family", it).apply()
    }
    SettingToggle("Fällige Aufgaben", "Erinnerung am Tag der Fälligkeit", due) {
        due = it; prefs.edit().putBoolean("notify_due", it).apply()
    }
    SettingToggle("Mindesthaltbarkeitsdatum", "Lebensmittel vor Ablauf erinnern", expiry) {
        expiry = it; prefs.edit().putBoolean("notify_expiry", it).apply()
    }
    if (expiry) {
        Text("MHD-Erinnerung vorher", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(0, 1, 3, 5, 7, 14).forEach { n ->
                FilterChip(selected = daysBeforeExpiry == n, onClick = {
                    daysBeforeExpiry = n; prefs.edit().putInt("expiry_before_days", n).apply()
                }, label = { Text(if (n == 0) "Am Tag" else "$n Tage") })
            }
        }
    }
    SettingToggle("Akku sparen", "Hintergrundchecks ca. stündlich statt alle 15 Minuten", eco) {
        eco = it
        prefs.edit().putBoolean("eco_mode", it).apply()
        if (BuildConfig.FIREBASE_ENABLED) FreeNotifications.schedule(context)
        else FreeNotifications.scheduleLocal(context)
    }
    SettingToggle("Änderungen zusammenfassen", "Mehrere Meldungen in einer Benachrichtigung", summary) {
        summary = it; prefs.edit().putBoolean("notify_summary", it).apply()
    }
    SettingToggle("Ruhezeiten", "Währenddessen keine neuen Meldungen", quiet) {
        quiet = it; prefs.edit().putBoolean("quiet_enabled", it).apply()
    }
    if (quiet) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(from, { from = it.take(5) }, modifier = Modifier.weight(1f),
                label = { Text("Von (HH:mm)") }, singleLine = true)
            OutlinedTextField(until, { until = it.take(5) }, modifier = Modifier.weight(1f),
                label = { Text("Bis (HH:mm)") }, singleLine = true)
        }
        TextButton(enabled = runCatching { java.time.LocalTime.parse(from) }.isSuccess &&
            runCatching { java.time.LocalTime.parse(until) }.isSuccess, onClick = {
            prefs.edit().putString("quiet_start", from).putString("quiet_end", until).apply()
        }) { Text("Ruhezeiten speichern") }
    }
    Text("Aufgaben vor der Fälligkeit erinnern", fontWeight = FontWeight.Bold)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(0, 1, 2, 3, 7).forEach { d ->
            FilterChip(selected = before == d, onClick = {
                before = d; prefs.edit().putInt("notify_before_days", d).apply()
            }, label = { Text(if(d == 0) "Am Tag" else "$d Tage") })
        }
    }
    Text("Benachrichtigungen führen direkt zur passenden Aufgaben- oder Einkaufsansicht. Bei geschlossener App prüft Android ungefähr alle 15 Minuten oder später; sofortige Push-Zustellung ist nicht garantiert.",
        color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SettingToggle(title: String, description: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Switch(enabled, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun AccountSection(data: HouseholdData, session: CloudSession?) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf(false) }
    SectionTitle("Konto & Sicherheit", "Kontrolle über deinen Zugang")
    if (session == null || !data.cloud) {
        Text("Der lokale Modus verwendet kein Online-Konto. Die Daten bleiben nur auf dem Gerät.", color = Muted)
        return
    }
    Text("Passwort zurücksetzen", fontWeight = FontWeight.Bold)
    OutlinedTextField(email, { email = it }, label = { Text("Konto-E-Mail") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedButton(onClick = { session.resetPassword(email) { message = it } }, enabled = email.isNotBlank()) {
        Text("Zurücksetzungslink senden")
    }
    HorizontalDivider()
    Text("Konto dauerhaft löschen", color = Pink, fontWeight = FontWeight.Bold)
    Text("Mitgliedskonten können gelöscht werden. Haushaltsbesitzer müssen die Verwaltung zuerst übertragen. Sichere deine Daten bei Bedarf vorher.", color = Muted)
    OutlinedButton(onClick = { delete = true }) { Text("Konto löschen", color = Pink) }
    if (message.isNotBlank()) Text(message, color = Teal)
    if(delete) AlertDialog(
        onDismissRequest = { delete = false }, title = { Text("Konto wirklich löschen?") },
        text = { Column {
            Text("Diese Aktion entfernt deinen Zugang zum gemeinsamen Haushalt und löscht dein Login. Sie kann nicht rückgängig gemacht werden.")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Aktuelles Passwort") },
                visualTransformation = PasswordVisualTransformation())
        } },
        confirmButton = { TextButton(enabled = password.isNotBlank(), onClick = {
            session.deleteOwnAccount(password) { message = it }
            delete = false; password = ""
        }) { Text("Endgültig löschen", color = Pink) } },
        dismissButton = { TextButton(onClick = { delete = false; password = "" }) { Text("Abbrechen") } }
    )
}
