package at.familie.haushaltplus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Single product lookup after a user-initiated barcode scan.
 * No search-as-you-type, no paid API or key, low volume and a conservative timeout.
 */
object ProductLookup {
    suspend fun lookup(barcode: String): String? = withContext(Dispatchers.IO) {
        if (!barcode.matches(Regex("[0-9]{8,14}"))) return@withContext null
        val url = URL("https://world.openfoodfacts.org/api/v3/product/" + barcode + ".json?fields=product_name,brands")
        var conn: HttpsURLConnection? = null
        try {
            conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = 5500
            conn.readTimeout = 5500
            conn.setRequestProperty("User-Agent", "HaushaltPlus/0.2 (https://github.com/dmpcgold-rgb/HaushaltPlus)")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optJSONObject("product")?.optString("product_name")
                ?.trim()?.takeIf { it.isNotEmpty() }?.take(110)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
