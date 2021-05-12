package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.nyttDokumentData
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtaksperiodeEndretData
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtaksperiodeGodkjent
import no.nav.helse.spre.saksbehandlingsstatistikk.TestUtil.finnSøknadDokumentId
import no.nav.helse.spre.saksbehandlingsstatistikk.TestUtil.json
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TilstandTest {
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
    fun `lagrer søknad til basen`() {
        val nyttDokument = nyttDokumentData()

        testRapid.sendTestMessage(nyttDokument.json())

        val søknadDokumentId = finnSøknadDokumentId(nyttDokument.hendelseId)
        assertEquals(nyttDokument.søknadId, søknadDokumentId)
    }

    @Test
    fun `håndterer duplikate dokumenter`() {
        val nyttDokument = nyttDokumentData()

        testRapid.sendTestMessage(nyttDokument.json("sendt_søknad_arbeidsgiver"))
        testRapid.sendTestMessage(nyttDokument.json())

        val søknadDokumentId = finnSøknadDokumentId(nyttDokument.hendelseId)
        assertEquals(nyttDokument.søknadId, søknadDokumentId)
    }

    @Test
    fun `lagrer saksbehandlingsløp for søknad`() {
        val nyttDokumentData = nyttDokumentData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData()
            .hendelse(nyttDokumentData.hendelseId)

        val vedtaksperiodeGodkjent = vedtaksperiodeGodkjent()
            .vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)

        val søknad = nyttDokumentData.asSøknad
            .saksbehandlerIdent(vedtaksperiodeGodkjent.saksbehandlerIdent)
            .vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)
            .vedtakFattet(vedtaksperiodeGodkjent.vedtakFattet)

        testRapid.sendTestMessage(nyttDokumentData.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret.json())
        testRapid.sendTestMessage(vedtaksperiodeGodkjent.json)

        assertEquals(søknad, søknadDao.finnSøknad(listOf(nyttDokumentData.hendelseId)))
        assertEquals(søknad, søknadDao.finnSøknad(vedtaksperiodeEndret.vedtaksperiodeId))
    }

}



