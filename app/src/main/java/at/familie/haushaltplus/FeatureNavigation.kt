package at.familie.haushaltplus

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Single navigation registry for the existing features.
 * A route has exactly one home; data is unchanged when routes move.
 */
data class FeatureShortcut(val id: String, val label: String)
object FeatureNavigation {
    val shopping = listOf(
        FeatureShortcut("templates", "Vorlagen"),
        FeatureShortcut("receipts", "Belege"),
        FeatureShortcut("ocr", "Beleg scannen"),
        FeatureShortcut("prices", "Preisvergleich"),
        FeatureShortcut("budget", "Budget")
    )
    val tasks = listOf(
        FeatureShortcut("calendar", "Kalender"),
        FeatureShortcut("checklists", "Checklisten"),
        FeatureShortcut("fairness", "Verteilung")
    )
    val stock = listOf(
        FeatureShortcut("expiry", "MHD"),
        FeatureShortcut("recipes", "Rezepte"),
        FeatureShortcut("meals", "Essensplan")
    )
    val familyAndSettings = listOf("family", "activity", "search", "notifications",
        "dashboard", "appearance", "backup", "trash", "account", "diagnostics")
    fun targetTab(route: String): Int? = when {
        shopping.any { it.id == route } -> 1
        tasks.any { it.id == route } -> 2
        stock.any { it.id == route } -> 3
        route in familyAndSettings -> 4
        else -> null
    }
    fun allRoutes(): List<String> = shopping.map { it.id } + tasks.map { it.id } +
        stock.map { it.id } + familyAndSettings
    fun label(id: String): String = (shopping + tasks + stock).firstOrNull { it.id == id }?.label
        ?: id
}

@Composable
fun HouseholdCategoryTab(
    category: String,
    shortcuts: List<FeatureShortcut>,
    selectedFeature: String,
    onSelectFeature: (String) -> Unit,
    data: HouseholdData,
    store: HouseholdStore,
    modifier: Modifier = Modifier,
    onJump: (Int) -> Unit = {},
    primaryScreen: @Composable (Modifier) -> Unit
) {
    Column(modifier.fillMaxSize()) {
        if (selectedFeature.isBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("MEHR ZU " + category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    shortcuts.forEach { feature ->
                        OutlinedButton(
                            onClick = { onSelectFeature(feature.id) },
                            modifier = Modifier.heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)
                        ) {
                            Icon(when(feature.id) {
                                "templates" -> Icons.Default.Bookmarks
                                "receipts" -> Icons.Default.ReceiptLong
                                "ocr" -> Icons.Default.PhotoCamera
                                "prices" -> Icons.Default.PriceChange
                                "budget" -> Icons.Default.Paid
                                "calendar" -> Icons.Default.CalendarMonth
                                "checklists" -> Icons.Default.Checklist
                                "fairness" -> Icons.Default.Groups
                                "expiry" -> Icons.Default.Event
                                "recipes" -> Icons.Default.MenuBook
                                "meals" -> Icons.Default.Restaurant
                                else -> Icons.Default.ChevronRight
                            }, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(feature.label)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                primaryScreen(Modifier.fillMaxSize())
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelectFeature("") },
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Zurück zu " + category)
                }
                Column(Modifier.weight(1f)) {
                    Text(category, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(FeatureNavigation.label(selectedFeature),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HouseholdFeatureContent(selectedFeature, data, store, onJump)
                Spacer(Modifier.height(76.dp))
            }
        }
    }
}
