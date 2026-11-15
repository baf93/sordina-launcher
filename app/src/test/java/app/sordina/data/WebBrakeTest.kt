package app.sordina.data

import app.sordina.service.WebBrake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebBrakeTest {
    private val yt = "com.google.android.youtube"
    private val ig = "com.instagram.android"
    private val braked = setOf(yt, ig)

    @Test fun siti() {
        assertEquals(yt, WebBrake.appFor("youtube.com/watch?v=abc", braked))
        assertEquals(yt, WebBrake.appFor("https://m.youtube.com/shorts/x", braked))
        assertEquals(yt, WebBrake.appFor("youtu.be/abc", braked))
        assertEquals(ig, WebBrake.appFor("www.instagram.com", braked))
        assertEquals(ig, WebBrake.appFor("https://www.instagram.com/reel/1", braked))
    }

    @Test fun nonSonoLoro() {
        assertNull(WebBrake.appFor("notyoutube.com", braked))
        assertNull(WebBrake.appFor("google.com/search?q=youtube.com", braked))
        assertNull(WebBrake.appFor("youtube video gatti", braked))
        // Frenato solo YouTube: instagram.com passa
        assertNull(WebBrake.appFor("instagram.com", setOf(yt)))
    }

    @Test fun browserSoloSeServe() {
        assertEquals(emptySet<String>(), WebBrake.browsers(setOf("com.whatsapp")))
        assert("com.android.chrome" in WebBrake.browsers(setOf(yt)))
        assert("com.sec.android.app.sbrowser" in WebBrake.browsers(setOf(yt)))
    }
}
