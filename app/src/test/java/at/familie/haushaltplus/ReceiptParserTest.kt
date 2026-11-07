package at.familie.haushaltplus

import org.junit.Assert.*
import org.junit.Test

class ReceiptParserTest {
    @Test fun detectsGermanMerchantDateAndTotal() {
        val result = ReceiptParser.parse("SUPERMARKT\n07.11.2026\nBrot 2,49\nGESAMT 12,80 EUR")
        assertEquals("SUPERMARKT", result.merchant)
        assertEquals("2026-11-07", result.date)
        assertEquals("12,80", result.total)
    }
    @Test fun uncertainReceiptNeverInventsTotal() {
        val result = ReceiptParser.parse("Markt\nÄpfel 2,99\nMilch 1,29")
        assertEquals("", result.total)
        assertTrue(result.warning.isNotBlank())
    }
}
