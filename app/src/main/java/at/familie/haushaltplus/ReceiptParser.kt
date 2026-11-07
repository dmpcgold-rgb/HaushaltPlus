package at.familie.haushaltplus

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReceiptSuggestion(val merchant: String, val date: String, val total: String,
    val warning: String = "")

/** Never stores raw OCR text or images; requires explicit user review before saving. */
object ReceiptParser {
    private val dateRe = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})\b""")
    private val totalRe = Regex(
        """(?i)(?:^|\b)(?:gesamtbetrag|gesamt|summe|total|zu\s+zahlen|betrag\s+eur|endbetrag)\b[^\d\n]*([0-9]{1,5}[,.][0-9]{2})""")
    fun parse(text: String): ReceiptSuggestion {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val merchant = lines.firstOrNull { it.length in 3..80 && it.any(Char::isLetter) }
            ?.take(80).orEmpty()
        val date = dateRe.find(text)?.let { result ->
            runCatching {
                val (d,m,y) = result.destructured
                LocalDate.of(if(y.length==2) 2000+y.toInt() else y.toInt(),m.toInt(),d.toInt())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrNull()
        } ?: LocalDate.now().toString()
        val amount = lines.asReversed().firstNotNullOfOrNull { line ->
            totalRe.find(line)?.groupValues?.getOrNull(1)
        }.orEmpty()
        return ReceiptSuggestion(merchant, date, amount,
            if (amount.isBlank()) "Gesamtbetrag nicht sicher erkannt – bitte manuell eingeben"
            else "Bitte Betrag, Datum und Geschäft mit dem Originalbeleg vergleichen")
    }
}
