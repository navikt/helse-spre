package no.nav.helse.spre.gosys.e2e

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.*
import no.nav.helse.spre.Dag.Companion.toJson
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtbetaltRiver
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtenUtbetalingRiver
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetRiver
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertNotNull

@KtorExperimentalAPI
internal class VedtakOgUtbetalingE2ETest : AbstractE2ETest() {

    val vedtakFattetDao = VedtakFattetDao(dataSource)
    val utbetalingDao = UtbetalingDao(dataSource)

    init {
        VedtakFattetRiver(testRapid, vedtakFattetDao, utbetalingDao, vedtakMediator)
        UtbetalingUtbetaltRiver(testRapid, utbetalingDao, vedtakFattetDao, vedtakMediator)
        UtbetalingUtenUtbetalingRiver(testRapid, utbetalingDao, vedtakFattetDao, vedtakMediator)
    }

    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_utbetalt`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetaling(
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingId = utbetalingId
            )
        )
        testRapid.sendTestMessage(
            utbetalingUtbetalt(
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = listOf(vedtaksperiodeId)
            )
        )
        assertJournalpost()
        assertVedtakPdf()
    }

    fun assertJournalpost(expected: JournalpostPayload = expectedJournalpost()) {
        val joarkRequest = capturedJoarkRequests.single()
        val joarkPayload = joarkRequest.parsePayload<JournalpostPayload>()

        assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
        assertNotNull(joarkRequest.headers["Nav-Consumer-Token"]) //TODO kanskje utbetalingId?
        assertEquals("application/json", joarkRequest.body.contentType.toString())
        assertEquals(expected, joarkPayload)
    }

    fun assertVedtakPdf(expected: VedtakPdfPayload = expectedPdfPayload()) {
        val pdfPayload = capturedPdfRequests.single().parsePayload<VedtakPdfPayload>()
        assertEquals(expected, pdfPayload)
    }

    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_uten_utbetaling`() {
        val id = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(hendelseId = id, utbetalingId = utbetalingId))
        testRapid.sendTestMessage(utbetalingUtenUtbetaling(id = id, utbetalingId = utbetalingId))
        assertJournalPostOgVedtakPdf(
            id, expectedPdfPayload =
            VedtakPdfPayload(
                fødselsnummer = "12345678910",
                fagsystemId = "123",
                type = "utbetalt",
                fom = LocalDate.of(2021, 5, 6),
                tom = LocalDate.of(2021, 5, 16),
                organisasjonsnummer = "123456789",
                behandlingsdato = LocalDate.of(2021, 5, 25),
                dagerIgjen = 31,
                automatiskBehandling = true,
                godkjentAv = "Automatisk behandlet",
                totaltTilUtbetaling = 0,
                ikkeUtbetalteDager = listOf(
                    VedtakPdfPayload.IkkeUtbetalteDager(
                        fom = LocalDate.of(2021, 5, 6),
                        tom = LocalDate.of(2021, 5, 16),
                        grunn = "Ferie/Permisjon",
                        begrunnelser = emptyList()
                    )
                ),
                dagsats = null,
                sykepengegrunnlag = 565260.0,
                maksdato = LocalDate.of(2021, 7, 15),
                linjer = emptyList()
            )
        )
    }

    @Test
    fun `journalfører vedtak med utbetaling_utbetalt og deretter vedtak_fattet`() {
        val id = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(utbetalingUtbetalt(hendelseId = id, utbetalingId = utbetalingId))
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(hendelseId = id, utbetalingId = utbetalingId))
        assertJournalPostOgVedtakPdf(id)
    }

    @Test
    fun `journalfører ikke dobbelt vedtak`() {
        val id = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(hendelseId = id, utbetalingId = utbetalingId))
        testRapid.sendTestMessage(utbetalingUtbetalt(hendelseId = id, utbetalingId = utbetalingId))
        testRapid.sendTestMessage(utbetalingUtbetalt(hendelseId = id, utbetalingId = utbetalingId))
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(hendelseId = id, utbetalingId = utbetalingId))
        assertEquals(1, capturedJoarkRequests.size)
        assertJournalPostOgVedtakPdf(id)
    }

    @Test
    fun `journalfører ikke uten å ha mottatt utbetaling`() {
        val id = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(hendelseId = id, utbetalingId = utbetalingId))
        assertEquals(0, capturedJoarkRequests.size)
        assertEquals(0, capturedPdfRequests.size)
    }

    @Test
    fun `journalfører ikke uten å ha mottatt vedtak_fattet`() {
        val id = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(utbetalingUtbetalt(hendelseId = id, utbetalingId = utbetalingId))
        assertEquals(0, capturedJoarkRequests.size)
        assertEquals(0, capturedPdfRequests.size)
    }

    @Test
    fun `journalfører ikke vedtak uten utbetalingId`() {
        val id = UUID.randomUUID()
        testRapid.sendTestMessage(vedtakFattetUtenUtbetalingId(id = id))
        testRapid.sendTestMessage(utbetalingUtbetalt(hendelseId = id))
        assertEquals(0, capturedJoarkRequests.size)
        assertEquals(0, capturedPdfRequests.size)
    }


    @Test
    fun `tar med arbeidsdager og kollapser over inneklemte fridager`() {
        val id = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(utbetalingUtbetaltInneklemteFridager(id = id, utbetalingId = utbetalingId))
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(hendelseId = id, utbetalingId = utbetalingId))
        assertJournalPostOgVedtakPdf(
            id,
            expectedPdfPayload = vedtakPdfPayloadInneklemteFridager()
        )
    }

    @Test
    fun `en avvist dag med begrunnelse revurdering`() {
        val id = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetaling(
                hendelseId = id,
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingId = utbetalingId
            )
        )
        testRapid.sendTestMessage(
            utbetalingUtbetalt(
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = listOf(vedtaksperiodeId),
                sykdomstidslinje = utbetalingsdager(6.mai(2021), 13.mai(2021)) + avvistDager(
                    16.mai(2021),
                    16.mai(2021),
                    listOf("EtterDødsdato")
                ),
                type = "REVURDERING"
            )
        )
        assertJournalPostOgVedtakPdf(
            hendelseId = utbetalingId,
            expectedPdfPayload = vedtakPdfPayloadMedEnAvvistDag(),
            expectedJournalpost(
                journalpostTittel = "Vedtak om revurdering av sykepenger",
                dokumentTittel = "Sykepenger revurdert i ny løsning, 06.05.2021 - 16.05.2021"
            )
        )
    }

    @Test
    fun `arbeidsdager før skjæringstidspunkt`() {
        val id = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(hendelseId = id, utbetalingId = utbetalingId))
        testRapid.sendTestMessage(
            utbetalingUtbetaltEnAvvistDagFørSkjæringstidspunkt(
                id = id,
                utbetalingId = utbetalingId
            )
        )
        assertJournalPostOgVedtakPdf(id, vedtakPdfPayloadArbeidsdagFørSkjæringstidspunkt(), expectedJournalpost())
    }

    @Test
    fun `vedtak og utbetaling som linkes med ulik fnr`() {
        val id = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        testRapid.sendTestMessage(vedtakFattetMedUtbetaling(hendelseId = id, utbetalingId = utbetalingId, fnr = "123"))
        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(utbetalingUtenUtbetaling(id = id, utbetalingId = utbetalingId, fnr = "321 "))
        }
    }

    @Test
    fun testyMc() {
        val v1 = UUID.randomUUID()
        val v2 = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val vedtaksperiodeIder = listOf(v1, v2)
        testRapid.sendTestMessage(
            revurderingUtbetalt(
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = vedtaksperiodeIder
            )
        )
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetaling(
                vedtaksperiodeId = v1,
                utbetalingId = utbetalingId,
                fom = 1.januar,
                tom = 31.januar
            )
        )
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetaling(
                vedtaksperiodeId = v2,
                utbetalingId = utbetalingId,
                fom = 1.februar,
                tom = 28.februar
            )
        )

        assertEquals(1, capturedJoarkRequests.size)
        assertEquals(1, capturedPdfRequests.size)

        val payload = capturedPdfRequests.first().parsePayload<VedtakPdfPayload>()
        assertEquals(1.januar, payload.fom)
        assertEquals(28.februar, payload.tom)
    }

    @Test
    fun testyMc2() {
        val v1 = UUID.randomUUID()
        val v2 = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val vedtaksperiodeIder = listOf(v1, v2)
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetaling(
                vedtaksperiodeId = v1,
                utbetalingId = utbetalingId,
                fom = 1.januar,
                tom = 31.januar
            )
        )
        testRapid.sendTestMessage(
            utbetalingUtbetalt(
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = vedtaksperiodeIder
            )
        )
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetaling(
                vedtaksperiodeId = v2,
                utbetalingId = utbetalingId,
                fom = 1.februar,
                tom = 28.februar
            )
        )

        assertEquals(1, capturedJoarkRequests.size)
        assertEquals(1, capturedPdfRequests.size)

        val payload = capturedPdfRequests.first().parsePayload<VedtakPdfPayload>()
        assertEquals(1.januar, payload.fom)
        assertEquals(28.februar, payload.tom)
    }

    @Test
    fun testyMc3() {
        val v1 = UUID.randomUUID()
        val v2 = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val vedtaksperiodeIder = listOf(v1, v2)
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetaling(
                vedtaksperiodeId = v1,
                utbetalingId = utbetalingId,
                fom = 1.januar,
                tom = 31.januar
            )
        )
        testRapid.sendTestMessage(
            vedtakFattetMedUtbetaling(
                vedtaksperiodeId = v2,
                utbetalingId = utbetalingId,
                fom = 1.februar,
                tom = 28.februar
            )
        )
        testRapid.sendTestMessage(
            utbetalingUtbetalt(
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = vedtaksperiodeIder
            )
        )

        assertEquals(1, capturedJoarkRequests.size)
        assertEquals(1, capturedPdfRequests.size)

        val payload = capturedPdfRequests.first().parsePayload<VedtakPdfPayload>()
        assertEquals(1.januar, payload.fom)
        assertEquals(28.februar, payload.tom)
    }

    private inline fun <reified T> HttpRequestData.parsePayload(): T = runBlocking {
        requireNotNull(objectMapper.readValue(this@parsePayload.body.toByteArray(), T::class.java))
    }

    private fun assertJournalPostOgVedtakPdf(
        hendelseId: UUID,
        expectedPdfPayload: VedtakPdfPayload = expectedPdfPayload(),
        journalpostPayload: JournalpostPayload = expectedJournalpost()
    ) = runBlocking {
        val joarkRequest = capturedJoarkRequests.single()
        val joarkPayload = joarkRequest.parsePayload<JournalpostPayload>()

        assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
        assertEquals(hendelseId.toString(), joarkRequest.headers["Nav-Consumer-Token"])
        assertEquals("application/json", joarkRequest.body.contentType.toString())
        assertEquals(journalpostPayload, joarkPayload)

        val pdfPayload = capturedPdfRequests.single().parsePayload<VedtakPdfPayload>()
        assertEquals(expectedPdfPayload, pdfPayload)
    }

    private fun expectedPdfPayload() =
        VedtakPdfPayload(
            fødselsnummer = "12345678910",
            fagsystemId = "fagsystemId",
            type = "utbetalt",
            fom = LocalDate.of(2021, 5, 6),
            tom = LocalDate.of(2021, 5, 16),
            organisasjonsnummer = "123456789",
            behandlingsdato = LocalDate.of(2021, 5, 25),
            dagerIgjen = 31,
            automatiskBehandling = true,
            godkjentAv = "Automatisk behandlet",
            totaltTilUtbetaling = 10017,
            ikkeUtbetalteDager = listOf(),
            dagsats = 1431,
            sykepengegrunnlag = 565260.0,
            maksdato = LocalDate.of(2021, 7, 15),
            linjer = listOf(
                VedtakPdfPayload.Linje(
                    fom = LocalDate.of(2021, 5, 6),
                    tom = LocalDate.of(2021, 5, 16),
                    grad = 100,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                )
            )
        )

    private fun vedtakPdfPayloadArbeidsdagFørSkjæringstidspunkt() =
        VedtakPdfPayload(
            fødselsnummer = "12345678910",
            fagsystemId = "123",
            type = "utbetalt",
            fom = LocalDate.of(2021, 5, 6),
            tom = LocalDate.of(2021, 5, 16),
            organisasjonsnummer = "123456789",
            behandlingsdato = LocalDate.of(2021, 5, 25),
            dagerIgjen = 31,
            automatiskBehandling = true,
            godkjentAv = "Automatisk behandlet",
            totaltTilUtbetaling = 38360,
            ikkeUtbetalteDager = listOf(
                VedtakPdfPayload.IkkeUtbetalteDager(
                    fom = LocalDate.of(2021, 5, 14),
                    tom = LocalDate.of(2021, 5, 14),
                    grunn = "Ferie/Permisjon",
                    begrunnelser = listOf()
                )
            ),
            dagsats = 1431,
            sykepengegrunnlag = 565260.0,
            maksdato = LocalDate.of(2021, 7, 15),
            linjer = listOf(
                VedtakPdfPayload.Linje(
                    fom = LocalDate.of(2021, 5, 6),
                    tom = LocalDate.of(2021, 5, 16),
                    grad = 100,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                )
            )
        )

    private fun vedtakPdfPayloadMedEnAvvistDag() =
        VedtakPdfPayload(
            fødselsnummer = "12345678910",
            fagsystemId = "123",
            type = "revurdering av",
            fom = LocalDate.of(2021, 5, 6),
            tom = LocalDate.of(2021, 5, 16),
            organisasjonsnummer = "123456789",
            behandlingsdato = LocalDate.of(2021, 5, 25),
            dagerIgjen = 31,
            automatiskBehandling = true,
            godkjentAv = "Automatisk behandlet",
            totaltTilUtbetaling = 38360,
            ikkeUtbetalteDager = listOf(
                VedtakPdfPayload.IkkeUtbetalteDager(
                    fom = LocalDate.of(2021, 5, 14),
                    tom = LocalDate.of(2021, 5, 14),
                    grunn = "Avvist dag",
                    begrunnelser = listOf("Personen er død")
                )
            ),
            dagsats = 1431,
            sykepengegrunnlag = 565260.0,
            maksdato = LocalDate.of(2021, 7, 15),
            linjer = listOf(
                VedtakPdfPayload.Linje(
                    fom = LocalDate.of(2021, 5, 6),
                    tom = LocalDate.of(2021, 5, 13),
                    grad = 100,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                )
            )
        )

    private fun vedtakPdfPayloadInneklemteFridager() =
        VedtakPdfPayload(
            fødselsnummer = "12345678910",
            fagsystemId = "123",
            type = "utbetalt",
            fom = LocalDate.of(2021, 5, 6),
            tom = LocalDate.of(2021, 5, 16),
            organisasjonsnummer = "123456789",
            behandlingsdato = LocalDate.of(2021, 5, 25),
            dagerIgjen = 31,
            automatiskBehandling = true,
            godkjentAv = "Automatisk behandlet",
            totaltTilUtbetaling = 38360,
            ikkeUtbetalteDager = listOf(
                VedtakPdfPayload.IkkeUtbetalteDager(
                    fom = LocalDate.of(2021, 5, 10),
                    tom = LocalDate.of(2021, 5, 14),
                    grunn = "Arbeidsdag",
                    begrunnelser = emptyList()
                )
            ),
            dagsats = 1431,
            sykepengegrunnlag = 565260.0,
            maksdato = LocalDate.of(2021, 7, 15),
            linjer = listOf(
                VedtakPdfPayload.Linje(
                    fom = LocalDate.of(2021, 5, 6),
                    tom = LocalDate.of(2021, 5, 9),
                    grad = 100,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                )
            )
        )


    private fun expectedJournalpost(
        journalpostTittel: String = "Vedtak om sykepenger",
        dokumentTittel: String = "Sykepenger behandlet i ny løsning, 06.05.2021 - 16.05.2021"
    ): JournalpostPayload {
        return JournalpostPayload(
            tittel = journalpostTittel,
            journalpostType = "NOTAT",
            tema = "SYK",
            behandlingstema = "ab0061",
            journalfoerendeEnhet = "9999",
            bruker = JournalpostPayload.Bruker(
                id = "12345678910",
                idType = "FNR"
            ),
            sak = JournalpostPayload.Sak(
                sakstype = "GENERELL_SAK"
            ),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = dokumentTittel,
                    dokumentvarianter = listOf(
                        JournalpostPayload.Dokument.DokumentVariant(
                            filtype = "PDFA",
                            fysiskDokument = Base64.getEncoder().encodeToString("Test".toByteArray()),
                            variantformat = "ARKIV"
                        )
                    )
                )
            )
        )
    }

    @Language("json")
    private fun vedtakFattetMedUtbetaling(
        hendelseId: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        utbetalingId: UUID? = UUID.randomUUID(),
        fnr: String = "12345678910",
        fom: LocalDate = 6.mai(2021),
        tom: LocalDate = 16.mai(2021),
    ) = """{
  "@id": "$hendelseId",
  "vedtaksperiodeId": "$vedtaksperiodeId",
  "fødselsnummer": "$fnr",
  "utbetalingId": ${utbetalingId?.let { "\"$it\"" } ?: null},
  "@event_name": "vedtak_fattet",
  "@opprettet": "2021-05-25T13:12:24.922420993",
  "fom": "$fom",
  "tom": "$tom",
  "@forårsaket_av": {
    "behov": [
      "Utbetaling"
    ],
    "event_name": "behov",
    "id": "6c7d5e27-c9cf-4e74-8662-a977f3f6a587",
    "opprettet": "2021-05-25T13:12:22.535549467"
  },
  "hendelser": [
    "65ca68fa-0f12-40f3-ac34-141fa77c4270",
    "6977170d-5a99-4e7f-8d5f-93bda94a9ba3",
    "15aa9c84-a9cc-4787-b82a-d5447aa3fab1"
  ],
  "skjæringstidspunkt": "2021-05-06",
  "sykepengegrunnlag": 565260.0,
  "inntekt": 47105.0,
  "aktørId": "123",
  "organisasjonsnummer": "123456789",
  "system_read_count": 0
}


    """

    @Language("json")
    private fun vedtakFattetUtenUtbetalingId(
        id: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID()
    ) = """{
  "@id": "$id",
  "vedtaksperiodeId": "$vedtaksperiodeId",
  "fødselsnummer": "12345678910",
  "@event_name": "vedtak_fattet",
  "@opprettet": "2021-05-25T13:12:24.922420993",
  "fom": "2021-05-06",
  "tom": "2021-05-16",
  "@forårsaket_av": {
    "behov": [
      "Utbetaling"
    ],
    "event_name": "behov",
    "id": "6c7d5e27-c9cf-4e74-8662-a977f3f6a587",
    "opprettet": "2021-05-25T13:12:22.535549467"
  },
  "hendelser": [
    "65ca68fa-0f12-40f3-ac34-141fa77c4270",
    "6977170d-5a99-4e7f-8d5f-93bda94a9ba3",
    "15aa9c84-a9cc-4787-b82a-d5447aa3fab1"
  ],
  "skjæringstidspunkt": "2021-01-07",
  "sykepengegrunnlag": 565260.0,
  "inntekt": 47105.0,
  "aktørId": "123",
  "organisasjonsnummer": "123456789",
  "system_read_count": 0
}
    """

    private fun revurderingUtbetalt(
        id: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = listOf(UUID.randomUUID())
    ) = utbetalingUtbetalt(
        hendelseId = id,
        utbetalingId = utbetalingId,
        vedtaksperiodeIder = vedtaksperiodeIder,
        type = "REVURDERING"
    )

    @Language("json")
    private fun utbetalingUtbetalt(
        hendelseId: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = listOf(UUID.randomUUID()),
        sykdomstidslinje: List<Dag> = utbetalingsdager(fom = 6.mai(2021), tom = 16.mai(2021)),
        type: String = "UTBETALING"
    ) = """{
  "@id": "$hendelseId",
  "fødselsnummer": "12345678910",
  "utbetalingId": "$utbetalingId",
  "@event_name": "utbetaling_utbetalt",
  "fom": "${sykdomstidslinje.first().dato}",
  "tom": "${sykdomstidslinje.last().dato}",
  "maksdato": "2021-07-15",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "$type",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "vedtaksperiodeIder": [
    ${vedtaksperiodeIder.joinToString { "\"$it\"" }}
  ],
  "arbeidsgiverOppdrag": ${Oppdrag(sykdomstidslinje).toJson()},
  "utbetalingsdager": ${sykdomstidslinje.toJson()},
  "@opprettet": "2021-05-25T13:12:24.922420993",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """

    @Language("json")
    private fun utbetalingUtbetaltEnAvvistDag(
        id: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID()
    ) = """{
  "@id": "$id",
  "fødselsnummer": "12345678910",
  "utbetalingId": "$utbetalingId",
  "@event_name": "utbetaling_utbetalt",
  "fom": "2021-05-06",
  "tom": "2021-05-13",
  "maksdato": "2021-07-15",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "REVURDERING",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "vedtaksperiodeIder": ["$vedtaksperiodeId"],
  "arbeidsgiverOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SPREF",
    "linjer": [
      {
        "fom": "2021-05-06",
        "tom": "2021-05-13",
        "dagsats": 1431,
        "lønn": 2193,
        "grad": 100.0,
        "stønadsdager": 35,
        "totalbeløp": 38360,
        "endringskode": "UEND",
        "delytelseId": 1,
        "klassekode": "SPREFAG-IOP"
      }
    ],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": "38360",
    "stønadsdager": 35,
    "fom": "2021-05-06",
    "tom": "2021-05-13"
  },
  "utbetalingsdager": [
        {
          "dato": "2021-05-06",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-07",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-08",
          "type": "NavHelgeDag"
        },
        {
          "dato": "2021-05-09",
          "type": "NavHelgeDag"
        },
        {
          "dato": "2021-05-10",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-11",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-12",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-13",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-14",
          "type": "AvvistDag",
          "begrunnelser": ["EtterDødsdato"]
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """

    @Language("json")
    private fun utbetalingUtbetaltInneklemteFridager(
        id: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID()
    ) = """{
  "@id": "$id",
  "fødselsnummer": "12345678910",
  "utbetalingId": "$utbetalingId",
  "@event_name": "utbetaling_utbetalt",
  "fom": "2021-05-06",
  "tom": "2021-05-09",
  "maksdato": "2021-07-15",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "UTBETALING",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "arbeidsgiverOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SPREF",
    "linjer": [
      {
        "fom": "2021-05-06",
        "tom": "2021-05-09",
        "dagsats": 1431,
        "lønn": 2193,
        "grad": 100.0,
        "stønadsdager": 35,
        "totalbeløp": 38360,
        "endringskode": "UEND",
        "delytelseId": 1,
        "klassekode": "SPREFAG-IOP"
      }
    ],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": "38360",
    "stønadsdager": 35,
    "fom": "2021-05-06",
    "tom": "2021-05-09"
  },
  "utbetalingsdager": [
        {
          "dato": "2021-05-06",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-07",
          "type": "NavDag"
        },
        {
          "dato": "2021-05-08",
          "type": "NavHelgeDag"
        },
        {
          "dato": "2021-05-09",
          "type": "NavHelgeDag"
        },
        {
          "dato": "2021-05-10",
          "type": "Arbeidsdag"
        },
        {
          "dato": "2021-05-11",
          "type": "Arbeidsdag"
        },
        {
          "dato": "2021-05-12",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-13",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-14",
          "type": "Arbeidsdag"
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """

    @Language("json")
    private fun utbetalingUtenUtbetaling(
        id: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID(),
        fnr: String = "12345678910"
    ) = """{
  "@id": "$id",
  "fødselsnummer": "$fnr",
  "utbetalingId": "$utbetalingId",
  "@event_name": "utbetaling_utbetalt",
  "fom": "2021-05-06",
  "tom": "2021-05-16",
  "maksdato": "2021-07-15",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "UTBETALING",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "arbeidsgiverOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SPREF",
    "linjer": [],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": "0",
    "stønadsdager": 0,
    "fom": "2021-05-06",
    "tom": "2021-05-16"
  },
  "utbetalingsdager": [
        {
          "dato": "2021-05-06",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-07",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-08",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-09",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-10",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-11",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-12",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-13",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-14",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-15",
          "type": "Fridag"
        },
        {
          "dato": "2021-05-16",
          "type": "Fridag"
        }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """

    @Language("json")
    private fun utbetalingUtbetaltEnAvvistDagFørSkjæringstidspunkt(
        id: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID()
    ) = """{
  "@id": "$id",
  "fødselsnummer": "12345678910",
  "utbetalingId": "$utbetalingId",
  "@event_name": "utbetaling_utbetalt",
  "fom": "2021-05-06",
  "tom": "2021-05-16",
  "maksdato": "2021-07-15",
  "forbrukteSykedager": "217",
  "gjenståendeSykedager": "31",
  "ident": "Automatisk behandlet",
  "epost": "tbd@nav.no",
  "type": "UTBETALING",
  "tidspunkt": "${LocalDateTime.now()}",
  "automatiskBehandling": "true",
  "arbeidsgiverOppdrag": {
    "mottaker": "123456789",
    "fagområde": "SPREF",
    "linjer": [
      {
        "fom": "2021-05-06",
        "tom": "2021-05-16",
        "dagsats": 1431,
        "lønn": 2193,
        "grad": 100.0,
        "stønadsdager": 35,
        "totalbeløp": 38360,
        "endringskode": "UEND",
        "delytelseId": 1,
        "klassekode": "SPREFAG-IOP"
      }
    ],
    "fagsystemId": "123",
    "endringskode": "ENDR",
    "tidsstempel": "${LocalDateTime.now()}",
    "nettoBeløp": "38360",
    "stønadsdager": 35,
    "fom": "2021-05-06",
    "tom": "2021-05-16"
  },
  "utbetalingsdager": [
    {
      "dato": "2017-05-05",
      "type": "Arbeidsdag"
    },
    {
      "dato": "2021-05-05",
      "type": "Arbeidsdag"
    },
    {
      "dato": "2021-05-06",
      "type": "NavDag"
    },
    {
      "dato": "2021-05-07",
      "type": "NavDag"
    },
    {
      "dato": "2021-05-08",
      "type": "NavHelgeDag"
    },
    {
      "dato": "2021-05-09",
      "type": "NavHelgeDag"
    },
    {
      "dato": "2021-05-10",
      "type": "NavDag"
    },
    {
      "dato": "2021-05-11",
      "type": "NavDag"
    },
    {
      "dato": "2021-05-12",
      "type": "NavDag"
    },
    {
      "dato": "2021-05-13",
      "type": "NavDag"
    },
    {
      "dato": "2021-05-14",
      "type": "Fridag"
    },
    {
      "dato": "2021-05-15",
      "type": "NavHelgeDag"
    },
    {
      "dato": "2021-05-16",
      "type": "NavHelgeDag"
    }
  ],
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "123",
  "organisasjonsnummer": "123456789"
}
    """
}
