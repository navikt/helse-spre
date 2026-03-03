package no.nav.helse.forsikringsoppgaver

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

    private class Oppgavedings(
        val fødselsnummer: String,
        val oppgaveId: UUID,
        val årsak: Årsak,
    )

    private val forsikringsgrunnlagClient = object : ForsikringsgrunnlagClient {
        var forsikringsgrunnlag: Forsikringsgrunnlag? = null
        override fun forsikringsgrunnlag(behandlingId: BehandlingId): Forsikringsgrunnlag? {
            return forsikringsgrunnlag
        }
    }

    private val oppgaveClient = object : OppgaveoppretterClient {
        var oppgavedings: Oppgavedings? = null
        private set
        var finnesDetOppgaveFor: Boolean? = null
        override fun lagOppgave(gosysOppgaveId: UUID, fødselsnummer: String, årsak: Årsak) {
            oppgavedings = Oppgavedings(
                fødselsnummer = fødselsnummer,
                oppgaveId = gosysOppgaveId,
                årsak = årsak
            )
        }

        override fun finnesDetOppgaveFor(gosysOppgaveId: UUID): Boolean {
            return finnesDetOppgaveFor ?: false
        }
    }

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
        oppgaveClient.finnesDetOppgaveFor = false

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        val actual = oppgaveClient.oppgavedings
        assertNotNull(actual)
        assertEquals(Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent, actual.årsak)
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Test
    fun `mangler forsikring`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = null
        oppgaveClient.finnesDetOppgaveFor = false

        // then
        assertFailsWith<IllegalStateException> {
            // when
            testRapid.sendTestMessage(event.trimIndent())
        }

        assertNull(oppgaveClient.oppgavedings)
    }

    @Test
    fun `oppgave finnes allerede`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 1,
            dekningsgrad = 80,
            premiegrunnlag = null
        )
        oppgaveClient.finnesDetOppgaveFor = true

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        assertNull(oppgaveClient.oppgavedings)
    }

    @Test
    fun `får ikke utbetalt sykepenger fra dag én`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 17,
            dekningsgrad = 80,
            premiegrunnlag = null
        )
        oppgaveClient.finnesDetOppgaveFor = false

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        assertNull(oppgaveClient.oppgavedings)
    }

    @Test
    fun `ikke forsikret med 80 prosent dekningsgrad`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 1,
            dekningsgrad = 100,
            premiegrunnlag = null
        )
        oppgaveClient.finnesDetOppgaveFor = false

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        assertNull(oppgaveClient.oppgavedings)
    }

    @Language("JSON")
    private val event = """
            {
                "@event_name": "selvstendig_utbetalt_etter_ventetid",
                "fødselsnummer": "$fødselsnummer",
                "behandlingId": "${UUID.randomUUID()}",
                "vedtaksperiodeId": "${UUID.randomUUID()}"
            }
        """
}
