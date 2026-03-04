package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.intellij.lang.annotations.Language

class VedtakFattetRiverTest {
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
        VedtakFattetRiver(testRapid, oppgaveClient, forsikringsgrunnlagClient)
    }

    @Test
    fun `Lager oppgave når vi har for stort avvik`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 1,
            dekningsgrad = 80,
            premiegrunnlag = BigDecimal("200000")
        )

        oppgaveClient.finnesDetOppgaveFor = false

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        val actual = oppgaveClient.oppgavedings
        assertNotNull(actual)
        assertEquals(Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag, actual.årsak)
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Test
    fun `Lager ingen oppgave når det ikke er for stort avvik`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 1,
            dekningsgrad = 80,
            premiegrunnlag = BigDecimal("400000")
        )

        oppgaveClient.finnesDetOppgaveFor = false

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        val actual = oppgaveClient.oppgavedings
        assertNull(actual)
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

    @Language("JSON")
    private val event = """
            {
                "@event_name": "vedtak_fattet",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "tags": ["Førstegangsbehandling"],
                "skjæringstidspunkt": "2024-01-01",
                "sykepengegrunnlag": 400000,
                "fødselsnummer": "$fødselsnummer",
                "behandlingId": "${UUID.randomUUID()}",
                "vedtaksperiodeId": "${UUID.randomUUID()}"
            }
        """

}
