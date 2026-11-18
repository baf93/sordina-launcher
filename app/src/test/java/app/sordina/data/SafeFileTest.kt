package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SafeFileTest {
    @Test fun replacesWholeAndLeavesNoTemp() {
        val dir = Files.createTempDirectory("safe").toFile()
        val f = File(dir, "held.json")
        f.writeSafely("[1]")
        f.writeSafely("[1,2]")
        assertEquals("[1,2]", f.readText())
        assertFalse(File(dir, "held.json.tmp").exists())
        dir.deleteRecursively()
    }
}
