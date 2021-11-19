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

        val vedtaksperiodeEndret = vedtaksperiodeEndretData(
            hendelse = søknadData.hendelseId
        )

        val vedtaksperiodeGodkjent = vedtaksperiodeGodkjent(
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        )

        val vedtakFattet = vedtakFattet(
            hendelser = listOf(søknadData.hendelseId),
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        )

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
    fun `Arbeidsgiversøknad skal ikke føre til statistikkevent`() {
        val søknadData = søknadData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData(
            hendelse = søknadData.hendelseId
        )

        val vedtakFattet = vedtakFattet(
            hendelser = listOf(søknadData.hendelseId),
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        )

        testRapid.sendTestMessage(søknadData.json(eventType = "sendt_søknad_arbeidsgiver"))
        testRapid.sendTestMessage(vedtaksperiodeEndret.json("MOTTATT_SYKMELDING_FERDIG_GAP"))
        testRapid.sendTestMessage(vedtakFattet.json)

        assertEquals(0, utgiver.meldinger.size)

    }

    @Test
    fun `Innvilget av spleis`() {
        val søknadData = søknadData()

        val vedtakFattet = vedtakFattet(
            hendelser = listOf(søknadData.hendelseId),
            vedtaksperiodeId = UUID.randomUUID()
        )

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

        val vedtaksperiodeEndret = vedtaksperiodeEndretData(
            hendelse = søknadData.hendelseId
        )

        val vedtaksperiodeForkastet = vedtaksperiodeForkastet(
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        )

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
    fun `Forkastet mens perioden er til godkjenning`() {
        val søknadData1 = søknadData()
        val vedtaksperiodeEndret1 = vedtaksperiodeEndretData(søknadData1.hendelseId)

        val søknadData2 = søknadData()
        val vedtaksperiodeEndret2 = VedtaksperiodeEndretData(
            listOf(søknadData1.hendelseId, søknadData2.hendelseId),
            vedtaksperiodeEndret1.vedtaksperiodeId
        )

        val vedtaksperiodeAvvist = vedtaksperiodeAvvist(vedtaksperiodeId = vedtaksperiodeEndret2.vedtaksperiodeId)

        val vedtaksperiodeForkastet = vedtaksperiodeForkastet(vedtaksperiodeEndret2.vedtaksperiodeId)

        testRapid.sendTestMessage(søknadData1.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret1.json())
        testRapid.sendTestMessage(søknadData2.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret2.json())
        testRapid.sendTestMessage(vedtaksperiodeAvvist.json)
        testRapid.sendTestMessage(vedtaksperiodeForkastet.json)

        assertEquals(2, utgiver.meldinger.size)

        val sendtTilDVH1 = utgiver.meldinger[0]

        val expected1 = StatistikkEvent(
            aktorId = vedtaksperiodeForkastet.aktørId,
            behandlingId = søknadData1.søknadId,
            tekniskTid = sendtTilDVH1.tekniskTid,
            funksjonellTid = vedtaksperiodeForkastet.vedtaksperiodeForkastet,
            mottattDato = søknadData1.hendelseOpprettet.toString(),
            registrertDato = søknadData1.hendelseOpprettet.toString(),
            saksbehandlerIdent = vedtaksperiodeAvvist.saksbehandlerIdent,
            automatiskbehandling = true,
            resultat = Resultat.AVVIST,
        )

        assertEquals(expected1, sendtTilDVH1)

        val sendtTilDVH2 = utgiver.meldinger[1]

        val expected2 = expected1.copy(
            behandlingId = søknadData2.søknadId,
            tekniskTid = sendtTilDVH2.tekniskTid,
            mottattDato = søknadData2.hendelseOpprettet.toString(),
            registrertDato = søknadData2.hendelseOpprettet.toString()
        )

        assertEquals(expected2, sendtTilDVH2)
    }

    @Test
    fun `Sender event for hver søknad ved godkjenning`() {
        val opprinneligSøknadData = søknadData()
        val korrigerer = opprinneligSøknadData.søknadId
        val korrigertSøknadData = søknadData(korrigerer)
        val vedtaksperiodeEndret = VedtaksperiodeEndretData(
            listOf(opprinneligSøknadData.hendelseId, korrigertSøknadData.hendelseId),
            UUID.randomUUID()
        )

        val vedtaksperiodeGodkjent = vedtaksperiodeGodkjent(vedtaksperiodeEndret.vedtaksperiodeId)
            .automatiskBehandling(false)

        val vedtakFattet = vedtakFattet(
            listOf(opprinneligSøknadData.hendelseId, korrigertSøknadData.hendelseId),
            vedtaksperiodeEndret.vedtaksperiodeId
        )

        testRapid.sendTestMessage(opprinneligSøknadData.json())
        testRapid.sendTestMessage(korrigertSøknadData.json())
        testRapid.sendTestMessage(vedtaksperiodeEndret.json())
        testRapid.sendTestMessage(vedtaksperiodeGodkjent.json)
        testRapid.sendTestMessage(vedtakFattet.json)

        assertEquals(2, utgiver.meldinger.size)

        val sendtTilDVH1 = utgiver.meldinger[0]
        val sendtTilDVH2 = utgiver.meldinger[1]

        val expected1 = StatistikkEvent(
            aktorId = vedtakFattet.aktørId,
            behandlingId = opprinneligSøknadData.søknadId,
            tekniskTid = sendtTilDVH1.tekniskTid,
            funksjonellTid = vedtakFattet.avsluttetISpleis,
            mottattDato = opprinneligSøknadData.hendelseOpprettet.toString(),
            registrertDato = opprinneligSøknadData.hendelseOpprettet.toString(),
            saksbehandlerIdent = vedtaksperiodeGodkjent.saksbehandlerIdent,
            automatiskbehandling = false,
            resultat = Resultat.INNVILGET,
            behandlingType = BehandlingType.SØKNAD,
        )

        assertEquals(expected1, sendtTilDVH1)

        val expected2 = expected1.copy(
            behandlingId = korrigertSøknadData.søknadId,
            tekniskTid = sendtTilDVH2.tekniskTid,
            mottattDato = korrigertSøknadData.hendelseOpprettet.toString(),
            registrertDato = korrigertSøknadData.hendelseOpprettet.toString(),
            relatertBehandlingId = korrigerer,
            behandlingType = BehandlingType.REVURDERING,
        )

        assertEquals(expected2, sendtTilDVH2)
    }

    @Test
    fun `Avvist av spesialist eller menneske`() {
        val søknadData = søknadData()

        val vedtaksperiodeEndret = vedtaksperiodeEndretData(
            hendelse = søknadData.hendelseId
        )

        val vedtaksperiodeAvvist = vedtaksperiodeAvvist(
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        )

        val vedtaksperiodeForkastet = vedtaksperiodeForkastet(
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        )

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

        val vedtaksperiodeEndret = vedtaksperiodeEndretData(
            hendelse = søknadData.hendelseId
        )

        val ikkegodkjentBehovsLøsning = ikkeGodkjentGodkjenningBehovsLøsning(
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        )

        val vedtaksperiodeForkastet = vedtaksperiodeForkastet(
            vedtaksperiodeId = vedtaksperiodeEndret.vedtaksperiodeId
        )

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



