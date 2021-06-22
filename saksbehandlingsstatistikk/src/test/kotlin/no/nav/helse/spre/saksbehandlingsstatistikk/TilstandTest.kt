package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.søknadData
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
        val søknadData = søknadData()

        testRapid.sendTestMessage(søknadData.json())

        val søknadDokumentId = finnSøknadDokumentId(søknadData.hendelseId)
        assertEquals(søknadData.søknadId, søknadDokumentId)
    }

    @Test
    fun `håndterer duplikate dokumenter`() {
        val søknadData = søknadData()

        testRapid.sendTestMessage(søknadData.json("sendt_søknad_arbeidsgiver"))
        testRapid.sendTestMessage(søknadData.json())

        val søknadDokumentId = finnSøknadDokumentId(søknadData.hendelseId)
        assertEquals(søknadData.søknadId, søknadDokumentId)
    }

    @Test
    fun `lagrer saksbehandlingsløp for automatisk behandlet søknad`() {
        val søknadData = søknadData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData(
            hendelse = søknadData.hendelseId
        )

        val vedtaksperiodeGodkjent = vedtaksperiodeGodkjent(
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        )

        val expected = søknadData.asSøknad
            .saksbehandlerIdent(vedtaksperiodeGodkjent.saksbehandlerIdent)
            .vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)
            .vedtakFattet(vedtaksperiodeGodkjent.vedtakFattet)

        testRapid.sendTestMessage(søknadData.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret.json())
        testRapid.sendTestMessage(vedtaksperiodeGodkjent.json)

        val lagretSøknad = søknadDao.finnSøknader(listOf(søknadData.hendelseId)).first()

        assertEquals(expected.saksbehandlerIdent, lagretSøknad.saksbehandlerIdent)
        assertEquals(expected.vedtaksperiodeId, lagretSøknad.vedtaksperiodeId)
        assertEquals(expected.vedtakFattet, lagretSøknad.vedtakFattet)
        assertEquals(true, lagretSøknad.automatiskBehandling)

        val lagretSøknadVedtaksperiode = søknadDao.finnSøknader(vedtaksperiodeEndret.vedtaksperiodeId).first()

        assertEquals(expected.saksbehandlerIdent, lagretSøknadVedtaksperiode.saksbehandlerIdent)
        assertEquals(expected.vedtaksperiodeId, lagretSøknadVedtaksperiode.vedtaksperiodeId)
        assertEquals(expected.vedtakFattet, lagretSøknadVedtaksperiode.vedtakFattet)
        assertEquals(true, lagretSøknadVedtaksperiode.automatiskBehandling)
    }

    @Test
    fun `lagrer saksbehandlingsløp for manuelt behandlet søknad`() {
        val søknadData = søknadData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData(
            hendelse = søknadData.hendelseId
        )

        val vedtaksperiodeGodkjent = vedtaksperiodeGodkjent(
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        ).automatiskBehandling(false)

        val expected = søknadData.asSøknad
            .saksbehandlerIdent(vedtaksperiodeGodkjent.saksbehandlerIdent)
            .vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)
            .vedtakFattet(vedtaksperiodeGodkjent.vedtakFattet)

        testRapid.sendTestMessage(søknadData.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret.json())
        testRapid.sendTestMessage(vedtaksperiodeGodkjent.json)

        val lagretSøknad = søknadDao.finnSøknader(listOf(søknadData.hendelseId)).first()

        assertEquals(expected.saksbehandlerIdent, lagretSøknad.saksbehandlerIdent)
        assertEquals(expected.vedtaksperiodeId, lagretSøknad.vedtaksperiodeId)
        assertEquals(expected.vedtakFattet, lagretSøknad.vedtakFattet)
        assertEquals(false, lagretSøknad.automatiskBehandling)

        val lagretSøknadVedtaksperiode = søknadDao.finnSøknader(vedtaksperiodeEndret.vedtaksperiodeId).first()

        assertEquals(expected.saksbehandlerIdent, lagretSøknadVedtaksperiode.saksbehandlerIdent)
        assertEquals(expected.vedtaksperiodeId, lagretSøknadVedtaksperiode.vedtaksperiodeId)
        assertEquals(expected.vedtakFattet, lagretSøknadVedtaksperiode.vedtakFattet)
        assertEquals(false, lagretSøknadVedtaksperiode.automatiskBehandling)
    }

}



