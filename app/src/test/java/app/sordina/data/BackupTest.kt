package app.sordina.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cosa esce dal telefono nella copia giornaliera, e cosa si rimette. */
class BackupTest {

    private val prefs: Map<String, Any?> = mapOf(
        "onboarded" to true,
        "categoryOrder" to "WORK,SOCIAL",
        "inApps" to setOf("com.whatsapp"),
        "lastSeen" to 1_700_000_000_000L,
        "budgetMin" to 30,
        "scale" to 1.1f,
        // Restano qui
        "haToken" to "segreto",
        "haTokenEnc" to "cifrato",
        "personRules" to "{\"org.telegram.messenger|elisa\":\"RIEPILOGO\"}",
        "holdAlways" to setOf("com.whatsapp|marco"),
        "personAliases" to "{\"org.telegram.messenger|s:42\":\"elisa rossi\"}",
        "aliasRefused" to setOf("org.telegram.messenger|s:7"),
        "ruleNames" to "{\"com.shop|promo\":\"Shop · Offerte\",\"com.whatsapp|marco\":\"WhatsApp · Marco\"}",
    )

    @Test fun secretsAndChatNamesStayOnThePhone() {
        val o = Backup.encode(prefs, offTypes = setOf("com.shop|promo"))
        listOf("haToken", "haTokenEnc", "personRules", "holdAlways", "personAliases", "aliasRefused").forEach { assertFalse(it, o.has(it)) }
        val names = JSONObject(o.getJSONObject("ruleNames").getString("v"))
        assertTrue(names.has("com.shop|promo"))
        assertFalse(names.has("com.whatsapp|marco"))
        assertFalse(o.toString().contains("Marco"))
        assertFalse(o.toString().contains("elisa"))
    }

    @Test fun roundTripKeepsTypes() {
        val back = Backup.decode(JSONObject(Backup.encode(prefs, emptySet()).toString()))
        assertEquals(true, back["onboarded"])
        assertEquals("WORK,SOCIAL", back["categoryOrder"])
        assertEquals(setOf("com.whatsapp"), back["inApps"])
        assertEquals(1_700_000_000_000L, back["lastSeen"])
        assertEquals(30, back["budgetMin"])
        assertEquals(1.1f, back["scale"] as Float, 0.0001f)
    }

    @Test fun aTamperedCopyCannotOverwriteLocalKeys() {
        val bad = JSONObject()
            .put("haToken", JSONObject().put("t", "s").put("v", "altro"))
            .put("personAliases", JSONObject().put("t", "s").put("v", "{\"x|s:1\":\"elisa\"}"))
            .put("strano", JSONObject().put("t", "???").put("v", 1))
            .put("rotto", "non un oggetto")
            .put("ok", JSONObject().put("t", "b").put("v", false))
        assertEquals(mapOf("ok" to false), Backup.decode(bad))
    }

    @Test fun ruleNamesSurviveBrokenJson() {
        assertEquals("{}", Backup.onlyTypes("non json", setOf("a|b")))
    }
}
