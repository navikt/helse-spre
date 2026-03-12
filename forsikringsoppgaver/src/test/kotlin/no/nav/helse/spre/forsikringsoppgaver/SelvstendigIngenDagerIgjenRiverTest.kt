package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.intellij.lang.annotations.Language

class SelvstendigIngenDagerIgjenRiverTest {
    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private val forsikringsgrunnlagClient = TestForsikringsgrunnlagClient()
    private val oppgaveClient = TestOppgaveClient()

    init {
        SelvstendigIngenDagerIgjenRiver(testRapid, oppgaveClient, forsikringsgrunnlagClient)
    }

    @Test
    fun `oppretter gosysoppgave`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            premiegrunnlag = "1000",
            dag1Eller17 = 1,
            dekningsgrad = 100,
        )

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNotNull(actual)
        assertEquals(Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød, actual.årsak)
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Test
    fun `oppretter ikke gosysoppgave hvis behandling-id ikke er tilknyttet noen forsikring`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = null

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNull(actual)
    }

    @Language("JSON")
    private val event = """
            {
                "@event_name": "selvstendig_ingen_dager_igjen",
                "@id": "${UUID.randomUUID()}",
                "fødselsnummer": "$fødselsnummer",
                "skjæringstidspunkt": "2018-01-01",
                "behandlingId": "${UUID.randomUUID()}"
            }
        """
}
