package at.familie.haushaltplus

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Orange = Color(0xFFFF781E)
private val Cyan = Color(0xFF4CE0C2)
private val Rose = Color(0xFFFF67A6)
private val Grey = Color(0xFFBFC0CB)

object DashboardOptions {
    val default = listOf("shopping", "tasks", "stock", "today", "progress", "meals", "budget")
    val labels = mapOf("shopping" to "Einkauf", "tasks" to "Aufgaben",
        "stock" to "Vorrat", "today" to "Heute fällig", "progress" to "Einkaufsfortschritt",
        "meals" to "Essensplan", "budget" to "Budget")
    fun read(context: Context): List<String> {
        val saved = context.getSharedPreferences("haushalt_v3_ui", Context.MODE_PRIVATE)
            .getString("dashboard_order", null)
        val order = saved?.split(',')?.filter { it in default }?.distinct() ?: default
        return order + default.filterNot { it in order }
    }
    fun visible(context: Context, key: String): Boolean =
        context.getSharedPreferences("haushalt_v3_ui", Context.MODE_PRIVATE)
            .getBoolean("dashboard_" + key, true)
}

@Composable
fun V3FeaturesPage(data: HouseholdData, store: HouseholdStore, section: String) {
    when(section) {
        "templates" -> TemplateScreen(data, store)
        "receipts" -> ReceiptScreen(data, store)
        "recipes" -> RecipeScreen(data, store)
        "calendar" -> CalendarScreen(data)
        "activity" -> ActivityScreen(data)
        "dashboard" -> DashboardSettingsScreen()
    }
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Grey)
    }
}

@Composable
private fun TemplateScreen(data: HouseholdData, store: HouseholdStore) {
    var source by rememberSaveable { mutableStateOf("standard") }
    var target by rememberSaveable { mutableStateOf("standard") }
    var name by rememberSaveable { mutableStateOf("") }
    val products = data.shopping.filter { !it.archived && !it.bought && it.listId == source }
    SectionHeader("Einkaufsvorlagen", "Regelmäßige Einkäufe mit einem Klick übernehmen")
    OutlinedTextField(name, { name = it.take(70) }, label = { Text("Vorlagenname, z. B. Wocheneinkauf") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    SimpleListPicker("Quelle", data.lists, source) { source = it }
    Button(onClick = {
        store.saveTemplate(name.trim(), products.map {
            TemplateProduct(it.name, it.quantity, it.unit, it.category)
        })
        name = ""
    }, enabled = name.isNotBlank() && products.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(8.dp))
        Text("Vorlage aus ${products.size} offenen Artikeln speichern")
    }
    HorizontalDivider()
    SimpleListPicker("Ziel-Einkaufsliste", data.lists, target) { target = it }
    if (data.templates.isEmpty()) Text("Noch keine Vorlagen gespeichert.", color = Grey)
    data.templates.forEach { template ->
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bookmarks, null, tint = Orange)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, fontWeight = FontWeight.Bold)
                        Text("${template.items.size} Artikel", color = Grey,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { store.deleteTemplate(template) }) {
                        Icon(Icons.Default.DeleteOutline, "Vorlage löschen")
                    }
                }
                Text(template.items.take(6).joinToString(" · ") { it.name }, color = Grey,
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    template.items.forEach { item ->
                        store.addShopping(item.name, item.quantity, item.unit, item.category, 0.0, target)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Auf Einkaufsliste setzen") }
            }
        }
    }
}

@Composable
private fun SimpleListPicker(title: String, lists: List<ShoppingList>, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$title: " + (lists.firstOrNull { it.id == value }?.name ?: "Allgemein"))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(text = { Text(list.name) }, onClick = {
                    onChange(list.id); expanded = false
                })
            }
        }
    }
}

@Composable
private fun ReceiptScreen(data: HouseholdData, store: HouseholdStore) {
    var create by remember { mutableStateOf(false) }
    val month = YearMonth.now().toString()
    val receipts = data.receipts.filter { it.date.startsWith(month) }
    val monthly = receipts.sumOf { it.total }
    val sumItems = data.shopping.filter { it.bought && it.boughtAt.startsWith(month) }.sumOf { it.price }
    val euro = java.text.NumberFormat.getCurrencyInstance(Locale.GERMANY)
    SectionHeader("Einkaufsbelege", "Ausgaben und Geschäfte im Überblick")
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("BELEGE DIESEN MONAT", color = Cyan, fontWeight = FontWeight.Bold)
            Text(euro.format(monthly), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black)
            Text("Einzelartikel mit erfasstem Preis: " + euro.format(sumItems), color = Grey)
            Text("Belege und Einzelartikel werden separat ausgewertet, um Doppelzählungen zu vermeiden.",
                style = MaterialTheme.typography.bodySmall, color = Grey)
        }
    }
    Button(onClick = { create = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.ReceiptLong, null); Spacer(Modifier.width(8.dp))
        Text("Kassenbetrag erfassen")
    }
    Text("Nach Geschäft", fontWeight = FontWeight.Bold)
    val stores = receipts.groupBy { it.merchant.trim().ifBlank { "Sonstiges" } }
        .mapValues { e -> e.value.sumOf { it.total } }.toList().sortedByDescending { it.second }
    if (stores.isEmpty()) Text("Noch keine Belege in diesem Monat.", color = Grey)
    stores.forEach { (shop, total) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(shop, Modifier.weight(1f)); Text(euro.format(total), color = Cyan)
        }
        LinearProgressIndicator(
            progress = { if (monthly > 0.0) (total/monthly).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp), color = Orange)
    }
    Text("Alle Belege", fontWeight = FontWeight.Bold)
    data.receipts.sortedByDescending { it.date }.take(35).forEach { receipt ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(receipt.merchant, fontWeight = FontWeight.SemiBold)
                    Text(receipt.date + if (receipt.note.isBlank()) "" else " · " + receipt.note,
                        color = Grey, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                }
                Text(euro.format(receipt.total), color = Cyan)
                IconButton(onClick = { store.deleteReceipt(receipt) }) {
                    Icon(Icons.Default.DeleteOutline, "Beleg löschen")
                }
            }
        }
    }
    if(create) {
        var shop by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now().toString()) }
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { create = false }, title = { Text("Ausgabe erfassen") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(shop, { shop = it }, label = { Text("Geschäft") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Datum YYYY-MM-DD") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Gesamtbetrag €") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, maxLines = 3)
            } }, confirmButton = {
                TextButton(enabled = shop.isNotBlank() &&
                    runCatching { LocalDate.parse(date) }.isSuccess &&
                    (amount.replace(',', '.').toDoubleOrNull() ?: -1.0) >= 0.0,
                    onClick = { store.addReceipt(shop, date, amount.replace(',', '.').toDoubleOrNull() ?: 0.0, note)
                        create = false }) { Text("Speichern") }
            }, dismissButton = { TextButton(onClick = { create = false }) { Text("Abbrechen") } })
    }
}

@Composable
private fun RecipeScreen(data: HouseholdData, store: HouseholdStore) {
    var add by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RecipeEntry?>(null) }
    SectionHeader("Rezeptbuch", "Portionen berechnen und Zutaten übertragen")
    Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null); Text(" Rezept speichern")
    }
    if (data.recipes.isEmpty()) Text("Speichere ein Rezept mit Zutaten pro Portion.", color = Grey)
    data.recipes.forEach { recipe ->
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, null, tint = Rose)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(recipe.name, fontWeight = FontWeight.Bold)
                    Text("${recipe.portions} Portionen · ${recipe.ingredients.lines().count { it.isNotBlank() }} Zutaten",
                        color = Grey, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { selected = recipe }) { Icon(Icons.Default.AddShoppingCart, "Zutaten") }
                IconButton(onClick = { store.deleteRecipe(recipe) }) { Icon(Icons.Default.DeleteOutline, "Löschen") }
            }
        }
    }
    if(add) {
        var name by remember { mutableStateOf("") }
        var portions by remember { mutableStateOf("2") }
        var ingredients by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("Neues Rezept") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text("Rezeptname") })
                OutlinedTextField(portions, { portions = it }, label = { Text("Portionen") })
                Text("Eine Zutat pro Zeile: 2|kg|Kartoffeln oder einfach Milch",
                    style = MaterialTheme.typography.bodySmall, color = Grey)
                OutlinedTextField(ingredients, { ingredients = it.take(3500) },
                    label = { Text("Zutaten") }, minLines = 4, maxLines = 8)
            } }, confirmButton = {
                TextButton(enabled = name.isNotBlank() && ingredients.isNotBlank() &&
                    (portions.toIntOrNull() ?: 0) in 1..100, onClick = {
                    store.addRecipe(name, portions.toIntOrNull() ?: 2, ingredients); add = false
                }) { Text("Speichern") }
            }, dismissButton = { TextButton(onClick = { add = false }) { Text("Abbrechen") } })
    }
    selected?.let { recipe ->
        var desired by remember(recipe.id) { mutableStateOf(recipe.portions.toString()) }
        val parts = V3Logic.recipeItems(recipe.ingredients, recipe.portions, desired.toIntOrNull() ?: recipe.portions)
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("Zutaten für " + recipe.name) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(desired, { desired = it }, label = { Text("Gewünschte Portionen") })
                Text(parts.joinToString("\n") { it.quantity + " " + it.unit + " " + it.name },
                    style = MaterialTheme.typography.bodyMedium)
            } }, confirmButton = { TextButton(enabled = (desired.toIntOrNull() ?: 0) in 1..100, onClick = {
                parts.forEach { p -> store.addShopping(p.name, p.quantity, p.unit, p.category, 0.0) }
                selected = null
            }) { Text("Auf Einkaufsliste") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Abbrechen") } })
    }
}

@Composable
private fun CalendarScreen(data: HouseholdData) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var weekOnly by rememberSaveable { mutableStateOf(true) }
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    SectionHeader("Familienkalender", "Aufgaben und Mahlzeiten in einer Übersicht")
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Vorheriger Monat") }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
            modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Nächster Monat") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = weekOnly, onClick = { weekOnly = true }, label = { Text("Woche") })
        FilterChip(selected = !weekOnly, onClick = { weekOnly = false }, label = { Text("Monat") })
        TextButton(onClick = { month = YearMonth.now(); selected = LocalDate.now() }) { Text("Heute") }
    }
    val weeks = (0..41).map { start.plusDays(it.toLong()) }.chunked(7)
    val shown = if (weekOnly) weeks.filter { row -> row.contains(selected) }.ifEmpty { weeks.take(1) } else weeks
    Row(Modifier.fillMaxWidth()) {
        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { Text(it, Modifier.weight(1f), color = Grey) }
    }
    shown.forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            week.forEach { day ->
                val count = data.tasks.count { !it.done && it.due == day.toString() } +
                    data.meals.count { it.date == day.toString() }
                val selectedDay = day == selected
                Surface(onClick = { selected = day; month = YearMonth.from(day) },
                    modifier = Modifier.weight(1f).heightIn(min = 62.dp),
                    color = if(selectedDay) Orange else Color(0xFF25252F),
                    shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.dayOfMonth.toString(),
                            color = if (day.month == month.month) Color.White else Grey,
                            fontWeight = if(selectedDay) FontWeight.Bold else FontWeight.Normal)
                        if(count > 0) Text("● " + count, color = if(selectedDay) Color.Black else Cyan,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    HorizontalDivider()
    Text(selected.format(DateTimeFormatter.ofPattern("EEEE, dd. MMMM", Locale.GERMAN)),
        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    val tasks = data.tasks.filter { it.due == selected.toString() }
    val meals = data.meals.filter { it.date == selected.toString() }
    if (tasks.isEmpty() && meals.isEmpty()) Text("Noch keine Termine für diesen Tag.", color = Grey)
    tasks.forEach { t ->
        Text((if(t.done) "✓ " else "☐ ") + t.title + " · " + t.assignee,
            color = if(t.done) Cyan else Color.White)
    }
    meals.forEach { m -> Text("🍽 " + m.slot + ": " + m.title) }
}

@Composable
private fun ActivityScreen(data: HouseholdData) {
    SectionHeader("Familienaktivität", "Nachvollziehen, was im Haushalt geändert wurde")
    if (data.activity.isEmpty()) Text("Noch keine Aktivitäten aufgezeichnet.", color = Grey)
    val df = java.text.SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
    data.activity.sortedByDescending { it.createdAt }.take(45).forEach { activity ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(when(activity.kind) {
                    "shopping" -> Icons.Default.ShoppingCart
                    "tasks" -> Icons.Default.TaskAlt
                    "stock" -> Icons.Default.Inventory2
                    else -> Icons.Default.History
                }, null, tint = when(activity.kind) {
                    "shopping" -> Orange
                    "tasks" -> Cyan
                    else -> Rose
                })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(activity.title, style = MaterialTheme.typography.bodyMedium)
                    Text(activity.actor + if(activity.createdAt > 0) " · " +
                        df.format(java.util.Date(activity.createdAt)) else "",
                        color = Grey, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text("„Rückgängig“ erscheint beim direkten Abhaken von Einkaufsartikeln. Andere Änderungen können nicht automatisch rückgängig gemacht werden.",
        style = MaterialTheme.typography.bodySmall, color = Grey)
}

@Composable
private fun DashboardSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("haushalt_v3_ui", Context.MODE_PRIVATE) }
    var order by remember { mutableStateOf(DashboardOptions.read(context)) }
    var version by remember { mutableIntStateOf(0) }
    SectionHeader("Startseite gestalten", "Kacheln ein- und ausblenden oder in der Reihenfolge verschieben")
    order.forEachIndexed { index, key ->
        val enabled = prefs.getBoolean("dashboard_" + key, true)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = enabled, onCheckedChange = {
                    prefs.edit().putBoolean("dashboard_" + key, it).apply()
                    version++
                })
                Text(DashboardOptions.labels[key] ?: key, Modifier.weight(1f))
                IconButton(enabled = index > 0, onClick = {
                    order = order.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }
                    prefs.edit().putString("dashboard_order", order.joinToString(",")).apply()
                }) { Icon(Icons.Default.KeyboardArrowUp, "Nach oben") }
                IconButton(enabled = index < order.lastIndex, onClick = {
                    order = order.toMutableList().also { java.util.Collections.swap(it, index, index + 1) }
                    prefs.edit().putString("dashboard_order", order.joinToString(",")).apply()
                }) { Icon(Icons.Default.KeyboardArrowDown, "Nach unten") }
            }
        }
    }
    Text("Änderungen erscheinen beim nächsten Öffnen der Startseite auf diesem Gerät.",
        color = Grey, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = {
        prefs.edit().remove("dashboard_order").apply()
        DashboardOptions.default.forEach { prefs.edit().remove("dashboard_" + it).apply() }
        order = DashboardOptions.default
        version++
    }) { Text("Standardansicht wiederherstellen") }
}
