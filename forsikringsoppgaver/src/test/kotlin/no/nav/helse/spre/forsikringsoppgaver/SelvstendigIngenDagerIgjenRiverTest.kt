package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.time.LocalDate
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.intellij.lang.annotations.Language

class SelvstendigIngenDagerIgjenRiverTest {
    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private class Oppgavedings(
        val fødselsnummer: String,
        val oppgaveId: UUID,
        val årsak: Årsak,
    )

    private val oppgaveClient = object : OppgaveoppretterClient {
        var oppgavedings: Oppgavedings? = null
            private set

        override fun lagOppgave(duplikatkontrollId: UUID, fødselsnummer: String, årsak: Årsak, skjæringstidspunkt: LocalDate) {
            oppgavedings = Oppgavedings(
                fødselsnummer = fødselsnummer,
                oppgaveId = duplikatkontrollId,
                årsak = årsak
            )
        }
    }

    init {
        SelvstendigIngenDagerIgjenRiver(testRapid, oppgaveClient)
    }

    @Test
    fun `oppretter gosysoppgave`() {
        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        val actual = oppgaveClient.oppgavedings
        assertNotNull(actual)
        assertEquals(Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød, actual.årsak)
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Language("JSON")
    private val event = """
            {
                "@event_name": "selvstendig_ingen_dager_igjen",
                "@id": "${UUID.randomUUID()}",
                "fødselsnummer": "$fødselsnummer",
                "skjæringstidspunkt": "2018-01-01"
            }
        """
}
