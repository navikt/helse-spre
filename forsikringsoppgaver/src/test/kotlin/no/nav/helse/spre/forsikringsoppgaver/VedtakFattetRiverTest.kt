package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.intellij.lang.annotations.Language

class VedtakFattetRiverTest {
    private val testRapid = TestRapid()
    private val fødselsnummer = "12345678910"

    private val forsikringsgrunnlagClient = TestForsikringsgrunnlagClient()
    private val oppgaveClient = TestOppgaveClient()

    init {
        VedtakFattetRiver(testRapid, oppgaveClient, forsikringsgrunnlagClient)
    }

    @Test
    fun `Lager oppgave når vi har for stort avvik`() {
        // given
        val premiegrunnlag = "200000"
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 1,
            dekningsgrad = 80,
            premiegrunnlag = premiegrunnlag
        )

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNotNull(actual)
        assertEquals(Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag("400000".toBigDecimal(), premiegrunnlag.toBigDecimal(), "66.67".toBigDecimal(2)), actual.årsak)
        assertEquals(fødselsnummer, actual.fødselsnummer)
    }

    @Test
    fun `Lager ingen oppgave når det ikke er for stort avvik`() {
        // given
        forsikringsgrunnlagClient.forsikringsgrunnlag = Forsikringsgrunnlag(
            dag1Eller17 = 1,
            dekningsgrad = 80,
            premiegrunnlag = "400000"
        )

        // when
        testRapid.sendTestMessage(event.trimIndent())

        // then
        val actual = oppgaveClient.oppgaveParams
        assertNull(actual)
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

    @Language("JSON")
    private val event = """
            {
                "@event_name": "vedtak_fattet",
                "@id": "${UUID.randomUUID()}",
                "yrkesaktivitetstype": "SELVSTENDIG",
                "tags": ["Førstegangsbehandling"],
                "skjæringstidspunkt": "2024-01-01",
                "sykepengegrunnlag": 400000,
                "fødselsnummer": "$fødselsnummer",
                "behandlingId": "${UUID.randomUUID()}"
            }
        """
}
