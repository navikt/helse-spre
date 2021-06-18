package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.ikkeGodkjentGodkjenningBehovsLøsning
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.søknadData
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtakFattet
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtaksperiodeAvvist
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtaksperiodeEndretData
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtaksperiodeForkastet
import no.nav.helse.spre.saksbehandlingsstatistikk.TestData.vedtaksperiodeGodkjent
import no.nav.helse.spre.saksbehandlingsstatistikk.TestUtil.json
import no.nav.helse.spre.saksbehandlingsstatistikk.TestUtil.jsonAvsluttetUtenGodkjenning
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

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
    fun `Innvilget av Spesialist eller menneske`() {
        val søknadData = søknadData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData()
            .hendelse(søknadData.hendelseId)

        val vedtaksperiodeGodkjent = vedtaksperiodeGodkjent()
            .vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)

        val vedtakFattet = vedtakFattet()
            .hendelse(søknadData.hendelseId)
            .vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)

        testRapid.sendTestMessage(søknadData.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret.json())
        testRapid.sendTestMessage(vedtaksperiodeGodkjent.json)
        testRapid.sendTestMessage(vedtakFattet.json)

        assertEquals(1, utgiver.meldinger.size)

        val sendtTilDVH = utgiver.meldinger[0]

        val expected = StatistikkEvent(
            aktorId = vedtakFattet.aktørId,
            behandlingId = søknadData.søknadId,
            tekniskTid = sendtTilDVH.tekniskTid,
            funksjonellTid = vedtakFattet.avsluttetISpleis,
            mottattDato = søknadData.hendelseOpprettet.toString(),
            registrertDato = søknadData.hendelseOpprettet.toString(),
            saksbehandlerIdent = vedtaksperiodeGodkjent.saksbehandlerIdent,
            automatiskbehandling = true,
            resultat = Resultat.INNVILGET,
        )

        assertEquals(expected, sendtTilDVH)
    }

    @Test
    fun `Innvilget av spleis`() {
        val søknadData = søknadData()

        val vedtakFattet = vedtakFattet()
            .hendelse(søknadData.hendelseId)
            .vedtaksperiodeId(UUID.randomUUID())

        testRapid.sendTestMessage(søknadData.json())
        testRapid.sendTestMessage(vedtakFattet.jsonAvsluttetUtenGodkjenning)

        assertEquals(1, utgiver.meldinger.size)

        val sendtTilDVH = utgiver.meldinger[0]

        val expected = StatistikkEvent(
            aktorId = vedtakFattet.aktørId,
            behandlingId = søknadData.søknadId,
            tekniskTid = sendtTilDVH.tekniskTid,
            funksjonellTid = vedtakFattet.avsluttetISpleis,
            mottattDato = søknadData.hendelseOpprettet.toString(),
            registrertDato = søknadData.hendelseOpprettet.toString(),
            saksbehandlerIdent = "SPLEIS",
            automatiskbehandling = true,
            resultat = Resultat.INNVILGET,
        )

        assertEquals(expected, sendtTilDVH)
    }

    @Test
    fun `Avvist av spleis`() {
        val søknadData = søknadData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData()
            .hendelse(søknadData.hendelseId)

        val vedtaksperiodeForkastet = vedtaksperiodeForkastet().vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)

        testRapid.sendTestMessage(søknadData.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret.json())
        testRapid.sendTestMessage(vedtaksperiodeForkastet.json)

        assertEquals(1, utgiver.meldinger.size)

        val sendtTilDVH = utgiver.meldinger[0]

        val expected = StatistikkEvent(
            aktorId = vedtaksperiodeForkastet.aktørId,
            behandlingId = søknadData.søknadId,
            tekniskTid = sendtTilDVH.tekniskTid,
            funksjonellTid = vedtaksperiodeForkastet.vedtaksperiodeForkastet,
            mottattDato = søknadData.hendelseOpprettet.toString(),
            registrertDato = søknadData.hendelseOpprettet.toString(),
            saksbehandlerIdent = "SPLEIS",
            automatiskbehandling = true,
            resultat = Resultat.AVVIST,
        )

        assertEquals(expected, sendtTilDVH)
    }

    @Test
    fun `Avvist av spesialist eller menneske`() {
        val søknadData = søknadData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData()
            .hendelse(søknadData.hendelseId)

        val vedtaksperiodeAvvist = vedtaksperiodeAvvist()
            .vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)

        val vedtaksperiodeForkastet = vedtaksperiodeForkastet().vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)


        testRapid.sendTestMessage(søknadData.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret.json())
        testRapid.sendTestMessage(vedtaksperiodeAvvist.json)
        testRapid.sendTestMessage(vedtaksperiodeForkastet.json)

        assertEquals(1, utgiver.meldinger.size)

        val sendtTilDVH = utgiver.meldinger[0]

        val expected = StatistikkEvent(
            aktorId = vedtaksperiodeForkastet.aktørId,
            behandlingId = søknadData.søknadId,
            tekniskTid = sendtTilDVH.tekniskTid,
            funksjonellTid = vedtaksperiodeForkastet.vedtaksperiodeForkastet,
            mottattDato = søknadData.hendelseOpprettet.toString(),
            registrertDato = søknadData.hendelseOpprettet.toString(),
            saksbehandlerIdent = vedtaksperiodeAvvist.saksbehandlerIdent,
            automatiskbehandling = true,
            resultat = Resultat.AVVIST,
        )

        assertEquals(expected, sendtTilDVH)
    }

    @Test
    fun `Avvist av spesialist eller menneske før avvist event`() {
        val søknadData = søknadData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData()
            .hendelse(søknadData.hendelseId)

        val ikkegodkjentBehovsLøsning = ikkeGodkjentGodkjenningBehovsLøsning()
            .vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)

        val vedtaksperiodeForkastet = vedtaksperiodeForkastet().vedtaksperiodeId(vedtaksperiodeEndret.vedtaksperiodeId)

        testRapid.sendTestMessage(søknadData.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret.json())
        testRapid.sendTestMessage(ikkegodkjentBehovsLøsning.json)
        testRapid.sendTestMessage(vedtaksperiodeForkastet.json)

        assertEquals(1, utgiver.meldinger.size)

        val sendtTilDVH = utgiver.meldinger[0]

        val expected = StatistikkEvent(
            aktorId = vedtaksperiodeForkastet.aktørId,
            behandlingId = søknadData.søknadId,
            tekniskTid = sendtTilDVH.tekniskTid,
            funksjonellTid = vedtaksperiodeForkastet.vedtaksperiodeForkastet,
            mottattDato = søknadData.hendelseOpprettet.toString(),
            registrertDato = søknadData.hendelseOpprettet.toString(),
            saksbehandlerIdent = ikkegodkjentBehovsLøsning.saksbehandlerIdent,
            automatiskbehandling = true,
            resultat = Resultat.AVVIST,
        )

        assertEquals(expected, sendtTilDVH)
    }

}



