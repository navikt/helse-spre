package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.intellij.lang.annotations.Language

class SelvstendigUtbetaltEtterVentetidRiverTest {
    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private val forsikringsgrunnlagClient = TestForsikringsgrunnlagClient()
    private val oppgaveClient = TestOppgaveClient()

    init {
        SelvstendigUtbetaltEtterVentetidRiver(testRapid, oppgaveClient, forsikringsgrunnlagClient)
    }

    @Test
    fun `oppretter gosysoppgave`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 1,
            dekningsgrad = 80,
            premiegrunnlag = null
        )

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNotNull(actual)
        assertEquals(Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent, actual.årsak)
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Test
    fun `mangler forsikring`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = null

        // then
        assertFailsWith<IllegalStateException> {
            // when
            testRapid.sendTestMessage(event.trimIndent())
        }

        assertNull(oppgaveClient.oppgaveParams)
    }

    @Test
    fun `får ikke utbetalt sykepenger fra dag én`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 17,
            dekningsgrad = 80,
            premiegrunnlag = null
        )

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        assertNull(oppgaveClient.oppgaveParams)
    }

    @Test
    fun `ikke forsikret med 80 prosent dekningsgrad`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 1,
            dekningsgrad = 100,
            premiegrunnlag = null
        )

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        assertNull(oppgaveClient.oppgaveParams)
    }

    @Language("JSON")
    private val event = """
            {
                "@event_name": "selvstendig_utbetalt_etter_ventetid",
                "@id": "${UUID.randomUUID()}",
                "fødselsnummer": "$fødselsnummer",
                "skjæringstidspunkt": "2018-01-01",
                "behandlingId": "${UUID.randomUUID()}"
            }
        """
}
