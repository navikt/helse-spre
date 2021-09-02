package no.nav.helse.spre.gosys.e2e

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.testhelpers.Dag.Companion.toJson
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtbetaltRiver
import no.nav.helse.spre.gosys.utbetaling.UtbetalingUtenUtbetalingRiver
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.IkkeUtbetalteDager
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetRiver
import no.nav.helse.spre.testhelpers.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

    companion object {
        fun LocalDate.formatted(): String = format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }

    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_utbetalt`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId
        )
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId)
        )
        assertJournalpost()
        assertVedtakPdf()
    }

    @Test
    fun `journalfører vedtak med vedtak_fattet og deretter utbetaling_uten_utbetaling`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(vedtaksperiodeId = vedtaksperiodeId, utbetalingId = utbetalingId)
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = fridager(1.januar, 31.januar)
        )
        assertJournalpost()
        assertVedtakPdf(
            expectedPdfPayload().copy(
                totaltTilUtbetaling = 0,
                linjer = emptyList(),
                ikkeUtbetalteDager = listOf(
                    IkkeUtbetalteDager(
                        fom = 1.januar,
                        tom = 31.januar,
                        grunn = "Ferie/Permisjon",
                        begrunnelser = emptyList()
                    )
                ),
                dagsats = null
            )
        )
    }

    @Test
    fun `journalfører vedtak med utbetaling_utbetalt og deretter vedtak_fattet`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendUtbetaling(utbetalingId = utbetalingId, vedtaksperiodeIder = listOf(vedtaksperiodeId))
        sendVedtakFattet(utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)

        assertEquals(1, capturedJoarkRequests.size)
        assertJournalpost()
        assertVedtakPdf()
    }

    @Test
    fun `journalfører ikke dobbelt vedtak`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val hendelseIdVedtak = UUID.randomUUID()
        val hendelseIdUtbetaling = UUID.randomUUID()
        sendVedtakFattet(hendelseId = hendelseIdVedtak, utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)
        sendUtbetaling(hendelseId = hendelseIdUtbetaling, utbetalingId = utbetalingId, vedtaksperiodeIder = listOf(vedtaksperiodeId))

        sendUtbetaling(hendelseId = hendelseIdUtbetaling, utbetalingId = utbetalingId, vedtaksperiodeIder = listOf(vedtaksperiodeId))
        sendVedtakFattet(hendelseId = hendelseIdVedtak, utbetalingId = utbetalingId, vedtaksperiodeId = vedtaksperiodeId)
        assertEquals(1, capturedJoarkRequests.size)
        assertJournalpost()
        assertVedtakPdf()
    }

    @Test
    fun `journalfører ikke uten å ha mottatt utbetaling`() {
        sendVedtakFattet()
        assertEquals(0, capturedJoarkRequests.size)
        assertEquals(0, capturedPdfRequests.size)
    }

    @Test
    fun `journalfører ikke uten å ha mottatt vedtak_fattet`() {
        sendUtbetaling()
        assertEquals(0, capturedJoarkRequests.size)
        assertEquals(0, capturedPdfRequests.size)
    }

    @Test
    fun `journalfører ikke vedtak uten utbetalingId`() {
        sendVedtakFattet(utbetalingId = null)
        sendUtbetaling()
        assertEquals(0, capturedJoarkRequests.size)
        assertEquals(0, capturedPdfRequests.size)
    }

    @Test
    fun `tar med arbeidsdager og kollapser over inneklemte fridager`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val sykdomstidslinje =
            utbetalingsdager(1.januar, 17.januar) +
            arbeidsdager(18.januar) +
            fridager(19.januar) +
            arbeidsdager(20.januar)
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = sykdomstidslinje
        )
        sendVedtakFattet(
            utbetalingId = utbetalingId,
            vedtaksperiodeId = vedtaksperiodeId,
            sykdomstidslinje = sykdomstidslinje
        )
        assertJournalpost(expectedJournalpost(1.januar, 20.januar))
        val payload = actualPdfPayload()
        assertEquals(
            listOf(
                IkkeUtbetalteDager(
                    fom = 18.januar,
                    tom = 20.januar,
                    grunn = "Arbeidsdag",
                    begrunnelser = emptyList()
                )
            ),
            payload.ikkeUtbetalteDager
        )
    }

    @Test
    fun `en avvist dag med begrunnelse revurdering`() {
        val utbetalingId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        val sykdomstidslinje =
            utbetalingsdager(1.januar, 16.januar) +
            avvistDager(17.januar, begrunnelser = listOf("EtterDødsdato"))
        sendVedtakFattet(
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingId = utbetalingId,
            sykdomstidslinje = sykdomstidslinje
        )
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = sykdomstidslinje,
            type = "REVURDERING"
        )
        assertJournalpost(
            expected = expectedJournalpost(
                journalpostTittel = "Vedtak om revurdering av sykepenger",
                dokumentTittel = "Sykepenger revurdert i ny løsning, 01.01.2018 - 17.01.2018"
            )
        )
        val actual = actualPdfPayload()
        assertEquals(
            listOf(
                IkkeUtbetalteDager(
                    fom = 17.januar,
                    tom = 17.januar,
                    grunn = "Avvist dag",
                    begrunnelser = listOf("Personen er død")
                )
            ), actual.ikkeUtbetalteDager
        )
    }

    @Test
    fun `arbeidsdager før skjæringstidspunkt`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        val sykdomstidslinje = utbetalingsdager(2.januar, 31.januar)
        sendVedtakFattet(
            utbetalingId = utbetalingId,
            vedtaksperiodeId = vedtaksperiodeId,
            sykdomstidslinje = sykdomstidslinje
        )
        sendUtbetaling(
            utbetalingId = utbetalingId,
            vedtaksperiodeIder = listOf(vedtaksperiodeId),
            sykdomstidslinje = arbeidsdager(1.januar) + sykdomstidslinje
        )

        assertJournalpost(expectedJournalpost(2.januar, 31.januar))
        val pdfPayload = actualPdfPayload()
        assertEquals(2.januar, pdfPayload.fom)
        assertEquals(31.januar, pdfPayload.tom)
        assertTrue(pdfPayload.ikkeUtbetalteDager.isEmpty())
    }

    @Test
    fun `vedtak og utbetaling som linkes med ulik fnr`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val utbetalingId = UUID.randomUUID()
        sendVedtakFattet(vedtaksperiodeId = vedtaksperiodeId, utbetalingId = utbetalingId, fødselsnummer = "123")
        assertThrows<IllegalStateException> {
            sendUtbetaling(fødselsnummer = "321", utbetalingId = utbetalingId, vedtaksperiodeIder = listOf(vedtaksperiodeId))
        }
    }

    private inline fun <reified T> HttpRequestData.parsePayload(): T = runBlocking {
        requireNotNull(objectMapper.readValue(this@parsePayload.body.toByteArray(), T::class.java))
    }

    fun assertJournalpost(expected: JournalpostPayload = expectedJournalpost()) {
        val joarkRequest = capturedJoarkRequests.single()
        val joarkPayload = joarkRequest.parsePayload<JournalpostPayload>()

        assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
        assertNotNull(joarkRequest.headers["Nav-Consumer-Token"])
        assertEquals("application/json", joarkRequest.body.contentType.toString())
        assertEquals(expected, joarkPayload)
    }

    fun assertVedtakPdf(expected: VedtakPdfPayload = expectedPdfPayload()) {
        val pdfPayload = capturedPdfRequests.single().parsePayload<VedtakPdfPayload>()
        assertEquals(expected, pdfPayload)
    }

    fun actualPdfPayload() = capturedPdfRequests.single().parsePayload<VedtakPdfPayload>()

    private fun expectedPdfPayload() =
        VedtakPdfPayload(
            fødselsnummer = "12345678910",
            fagsystemId = "fagsystemId",
            type = "utbetalt",
            fom = 1.januar,
            tom = 31.januar,
            organisasjonsnummer = "123456789",
            behandlingsdato = 31.januar,
            dagerIgjen = 31,
            automatiskBehandling = true,
            godkjentAv = "Automatisk behandlet",
            totaltTilUtbetaling = 32913,
            ikkeUtbetalteDager = listOf(),
            dagsats = 1431,
            sykepengegrunnlag = 565260.0,
            maksdato = LocalDate.of(2021, 7, 15),
            linjer = listOf(
                VedtakPdfPayload.Linje(
                    fom = 1.januar,
                    tom = 31.januar,
                    grad = 100,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                )
            )
        )

    private fun expectedJournalpost(
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        journalpostTittel: String = "Vedtak om sykepenger",
        dokumentTittel: String = "Sykepenger behandlet i ny løsning, ${fom.formatted()} - ${tom.formatted()}"
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
    private fun vedtakFattet(
        hendelseId: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        utbetalingId: UUID? = UUID.randomUUID(),
        fnr: String = "12345678910",
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
    ) = """{
    "@id": "$hendelseId",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "fødselsnummer": "$fnr",
    "utbetalingId": ${utbetalingId?.let { "\"$it\"" }},
    "@event_name": "vedtak_fattet",
    "@opprettet": "${tom.atStartOfDay()}",
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
    "skjæringstidspunkt": "$fom",
    "sykepengegrunnlag": 565260.0,
    "inntekt": 47105.0,
    "aktørId": "123",
    "organisasjonsnummer": "123456789",
    "system_read_count": 0
}"""

    @Language("json")
    private fun utbetaling(
        fødselsnummer: String = "12345678910",
        hendelseId: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = listOf(UUID.randomUUID()),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        type: String = "UTBETALING"
    ) = """{
    "@id": "$hendelseId",
    "fødselsnummer": "$fødselsnummer",
    "utbetalingId": "$utbetalingId",
    "@event_name": ${if (sykdomstidslinje.none {it.type == Dagtype.UTBETALINGSDAG}) "\"utbetaling_uten_utbetaling\"" else "\"utbetaling_utbetalt\""},
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
    "@opprettet": "${sykdomstidslinje.last().dato.atStartOfDay()}",
    "aktørId": "123",
    "organisasjonsnummer": "123456789"
}"""

    private fun revurderingUtbetalt(
        id: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = listOf(UUID.randomUUID())
    ) = utbetaling(
        hendelseId = id,
        utbetalingId = utbetalingId,
        vedtaksperiodeIder = vedtaksperiodeIder,
        type = "REVURDERING"
    )

    private fun sendUtbetaling(
        hendelseId: UUID = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = emptyList(),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        type: String = "UTBETALING"
    ) {
        require(sykdomstidslinje.isNotEmpty()) { "Sykdomstidslinjen kan ikke være tom!" }
        testRapid.sendTestMessage(
            utbetaling(
                hendelseId = hendelseId,
                fødselsnummer = fødselsnummer,
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = vedtaksperiodeIder,
                sykdomstidslinje = sykdomstidslinje,
                type = type
            )
        )
    }

    private fun sendVedtakFattet(
        hendelseId: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        utbetalingId: UUID? = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar)
    ) {
        require(sykdomstidslinje.isNotEmpty()) { "Sykdomstidslinjen kan ikke være tom!" }
        testRapid.sendTestMessage(
            vedtakFattet(
                hendelseId = hendelseId,
                fnr = fødselsnummer,
                utbetalingId = utbetalingId,
                vedtaksperiodeId = vedtaksperiodeId,
                fom = sykdomstidslinje.first().dato,
                tom = sykdomstidslinje.last().dato
            )
        )
    }


}
