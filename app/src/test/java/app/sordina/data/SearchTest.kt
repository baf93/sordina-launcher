package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test fun tolleranza() {
        assertEquals(90, Search.score("whats", "whatsapp"))
        assertTrue(Search.score("spotfy", "spotify") >= 50)
        assertTrue(Search.score("ha", "home assistant") >= 75)
        assertEquals(0, Search.score("sela", "pulsee"))
    }

    @Test fun normalizza() {
        assertEquals("caffe liberta", Search.normalize("Caffè & Libertà!"))
    }
}
