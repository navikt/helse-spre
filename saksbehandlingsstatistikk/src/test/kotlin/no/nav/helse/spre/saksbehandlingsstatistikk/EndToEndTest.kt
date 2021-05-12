package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.nyttDokumentData
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtakFattet
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtaksperiodeEndretData
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtaksperiodeGodkjent
import no.nav.helse.spre.saksbehandlingsstatistikk.TestUtil.json
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class EndToEndTest {
    private val testRapid = TestRapid()
    private val dataSource = TestUtil.dataSource
    private val søknadDao = SøknadDao(dataSource)
    private val utgiver = LokalUtgiver()
    private val spreService = SpreService(utgiver, søknadDao)

    init {
        testRapid.setupRivers(spreService, søknadDao)
    }

    @BeforeEach
    fun setup() {
        global.setVersjon("kremfjes")
        testRapid.reset()
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "TRUNCATE TABLE søknad"
            session.run(queryOf(query).asExecute)
        }
    }

    @Test
    fun `Happy path`() {
        val nyttDokumentData = nyttDokumentData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData()
            .hendelse(nyttDokumentData.hendelseId)

        val vedtaksperiodeGodkjent = vedtaksperiodeGodkjent()
            .vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)

        val vedtakFattet = vedtakFattet()
            .hendelse(nyttDokumentData.hendelseId)

        testRapid.sendTestMessage(nyttDokumentData.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret.json())
        testRapid.sendTestMessage(vedtaksperiodeGodkjent.json)
        testRapid.sendTestMessage(vedtakFattet.json)

        assertEquals(1, utgiver.meldinger.size)

        val sendtTilDVH = utgiver.meldinger[0]

        val expected = StatistikkEvent(
            aktorId = vedtakFattet.aktørId,
            behandlingId = nyttDokumentData.søknadId,
            tekniskTid = sendtTilDVH.tekniskTid,
            funksjonellTid = vedtaksperiodeGodkjent.vedtakFattet,
            mottattDato = nyttDokumentData.hendelseOpprettet.toString(),
            registrertDato = nyttDokumentData.hendelseOpprettet.toString(),
            saksbehandlerIdent = vedtaksperiodeGodkjent.saksbehandlerIdent,
        )

        assertEquals(expected, sendtTilDVH)
    }

}



