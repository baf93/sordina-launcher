package app.sordina.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** L'ora di una sveglia impostata da Sordina: oggi se non è ancora passata, se no domani. */
class NextAlarmTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone)

    @Test fun seraPerDomaniMattina() =
        assertEquals(at(18, 7, 15).toInstant().toEpochMilli(), nextAt(7, 15, at(17, 20, 44)))

    @Test fun piùTardiOggi() =
        assertEquals(at(17, 23, 57).toInstant().toEpochMilli(), nextAt(23, 57, at(17, 20, 44)))

    @Test fun stessoMinutoÈDomani() =
        assertEquals(at(18, 20, 44).toInstant().toEpochMilli(), nextAt(20, 44, at(17, 20, 44).withSecond(30)))
}
