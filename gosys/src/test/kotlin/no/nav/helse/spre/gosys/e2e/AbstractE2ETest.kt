package no.nav.helse.spre.gosys.e2e

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.gosys.*
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest.Utbetalingstype.UTBETALING
import no.nav.helse.spre.gosys.e2e.VedtakOgUtbetalingE2ETest.Companion.formatted
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.IkkeUtbetalteDager
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload.Linje
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2
import no.nav.helse.spre.testhelpers.*
import no.nav.helse.spre.testhelpers.Dag.Companion.toJson
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertNotNull

@KtorExperimentalAPI
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class AbstractE2ETest {

    protected val testRapid = TestRapid()
    protected val dataSource = setupDataSourceMedFlyway()
    protected var capturedJoarkRequests = mutableListOf<HttpRequestData>()
    protected var capturedPdfRequests = mutableListOf<HttpRequestData>()

    private val mockClient = httpclient()
    protected val pdfClient = PdfClient(mockClient)
    private val stsMock: StsRestClient = mockk {
        coEvery { token() }.returns("6B70C162-8AAB-4B56-944D-7F092423FE4B")
    }
    protected val joarkClient = JoarkClient("https://url.no", stsMock, mockClient)
    protected val duplikatsjekkDao = DuplikatsjekkDao(dataSource)
    protected val vedtakMediator = VedtakMediator(pdfClient, joarkClient)

    @BeforeEach
    internal fun abstractSetup() {
        testRapid.reset()
        capturedJoarkRequests.clear()
        capturedPdfRequests.clear()
    }

    private fun httpclient(): HttpClient {
        return HttpClient(MockEngine) {
            install(JsonFeature) {
                serializer = JacksonSerializer(objectMapper)
            }
            engine {
                addHandler { request ->
                    when (request.url.fullPath) {

                        "/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true" -> handlerForJoark(request)

                        "/api/v1/genpdf/spre-gosys/vedtak" -> handlerForPdfKall(request)

                        "/api/v1/genpdf/spre-gosys/annullering" -> handlerForPdfKall(request)

                        "/api/v1/genpdf/spre-gosys/vedtak-v2" -> handlerForPdfKall(request)

                        else -> error("Unhandled ${request.url.fullPath}")
                    }
                }
            }
        }
    }

    open fun MockRequestHandleScope.handlerForJoark(request: HttpRequestData): HttpResponseData {
        capturedJoarkRequests.add(request)
        return respond("Hello, world")
    }

    open fun MockRequestHandleScope.handlerForPdfKall(request: HttpRequestData): HttpResponseData {
        capturedPdfRequests.add(request)
        return respond("Test".toByteArray())
    }

    private inline fun <reified T> HttpRequestData.parsePayload(): T = runBlocking {
        requireNotNull(objectMapper.readValue(this@parsePayload.body.toByteArray(), T::class.java))
    }

    protected fun assertJournalpost(expected: JournalpostPayload = expectedJournalpost()) {
        val joarkRequest = capturedJoarkRequests.single()
        val joarkPayload = joarkRequest.parsePayload<JournalpostPayload>()

        Assertions.assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
        assertNotNull(joarkRequest.headers["Nav-Consumer-Token"])
        Assertions.assertEquals("application/json", joarkRequest.body.contentType.toString())
        Assertions.assertEquals(expected, joarkPayload)
    }

    protected fun assertVedtakPdf(expected: VedtakPdfPayload = expectedPdfPayload()) {
        val pdfPayload = capturedPdfRequests.single().parsePayload<VedtakPdfPayload>()
        Assertions.assertEquals(expected, pdfPayload)
    }

    protected fun assertVedtakPdf(expected: VedtakPdfPayloadV2 = expectedPdfPayloadV2()) {
        val pdfPayload = capturedPdfRequests.single().parsePayload<VedtakPdfPayloadV2>()
        Assertions.assertEquals(expected, pdfPayload)
    }

    protected fun actualPdfPayload() = capturedPdfRequests.single().parsePayload<VedtakPdfPayload>()

    protected fun expectedPdfPayload(
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        utbetalingstype: Utbetalingstype = UTBETALING,
        totaltTilUtbetaling: Int = 32913,
        behandlingsdato: LocalDate = tom,
        linjer: List<Linje> = listOf(
            Linje(
                fom = fom,
                tom = tom,
                grad = 100,
                beløp = 1431,
                mottaker = "123 456 789"
            )
        ),
        arbeidsgiverOppdrag: VedtakPdfPayload.Oppdrag = VedtakPdfPayload.Oppdrag(linjer = linjer),
        personOppdrag: VedtakPdfPayload.Oppdrag = VedtakPdfPayload.Oppdrag(),
        ikkeUtbetalteDager: List<IkkeUtbetalteDager> = emptyList()
    ) =
        VedtakPdfPayload(
            fødselsnummer = "12345678910",
            fagsystemId = "fagsystemId",
            type = utbetalingstype.lesbarTittel,
            fom = fom,
            tom = tom,
            organisasjonsnummer = "123456789",
            behandlingsdato = behandlingsdato,
            dagerIgjen = 31,
            automatiskBehandling = true,
            godkjentAv = "Automatisk behandlet",
            totaltTilUtbetaling = totaltTilUtbetaling,
            ikkeUtbetalteDager = ikkeUtbetalteDager,
            dagsats = 1431,
            sykepengegrunnlag = 565260.0,
            grunnlagForSykepengegrunnlag = mapOf("123456789" to 265260.0, "987654321" to 300000.21),
            maksdato = LocalDate.of(2021, 7, 15),
            linjer = linjer,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag,
            personOppdrag = personOppdrag
        )

    protected fun expectedPdfPayloadV2(
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        utbetalingstype: Utbetalingstype = UTBETALING,
        totaltTilUtbetaling: Int = 32913,
        behandlingsdato: LocalDate = tom,
        linjer: List<VedtakPdfPayloadV2.Linje> = listOf(
            VedtakPdfPayloadV2.Linje(
                fom = fom,
                tom = tom,
                grad = 100,
                dagsats = 1431,
                mottaker = "123 456 789",
                totalbeløp = totaltTilUtbetaling
            )
        ),
        arbeidsgiverOppdrag: VedtakPdfPayloadV2.Oppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdArbeidsgiver"),
        personOppdrag: VedtakPdfPayloadV2.Oppdrag = VedtakPdfPayloadV2.Oppdrag("fagsystemIdPerson"),
        ikkeUtbetalteDager: List<VedtakPdfPayloadV2.IkkeUtbetalteDager> = emptyList()
    ) =
        VedtakPdfPayloadV2(
            fødselsnummer = "12345678910",
            type = utbetalingstype.lesbarTittel,
            fom = fom,
            tom = tom,
            organisasjonsnummer = "123456789",
            behandlingsdato = behandlingsdato,
            dagerIgjen = 31,
            automatiskBehandling = true,
            godkjentAv = "Automatisk behandlet",
            sumNettoBeløp = totaltTilUtbetaling,
            ikkeUtbetalteDager = ikkeUtbetalteDager,
            sykepengegrunnlag = 565260.0,
            grunnlagForSykepengegrunnlag = mapOf("123456789" to 265260.0, "987654321" to 300000.21),
            maksdato = LocalDate.of(2021, 7, 15),
            linjer = linjer,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag,
            personOppdrag = personOppdrag
        )

    protected fun expectedJournalpost(
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        utbetalingstype: Utbetalingstype = UTBETALING,
        journalpostTittel: String = utbetalingstype.journaltittel,
        dokumentTittel: String = "${utbetalingstype.dokumenttittel}, ${fom.formatted()} - ${tom.formatted()}"
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
    "grunnlagForSykepengegrunnlagPerArbeidsgiver": {
      "123456789": 265260.0,
      "987654321": 300000.21
    },
    "inntekt": 47105.0,
    "aktørId": "123",
    "organisasjonsnummer": "123456789",
    "system_read_count": 0
}"""

    @Language("json")
    private fun utbetalingBruker(
        fødselsnummer: String = "12345678910",
        hendelseId: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = listOf(UUID.randomUUID()),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        type: String = "UTBETALING",
        opprettet: LocalDateTime = sykdomstidslinje.last().dato.atStartOfDay(),
        personOppdrag: Oppdrag = Oppdrag(sykdomstidslinje, fagområde = "SP", mottaker = fødselsnummer, fagsystemId = "fagsystemIdPerson"),
        arbeidsgiverOppdrag: Oppdrag = Oppdrag(emptyList(), fagområde = "SPREF", fagsystemId = "fagsystemIdArbeidsgiver")
    ) = """{
    "@id": "$hendelseId",
    "fødselsnummer": "$fødselsnummer",
    "utbetalingId": "$utbetalingId",
    "@event_name": ${if (sykdomstidslinje.none { it.type == Dagtype.UTBETALINGSDAG }) "\"utbetaling_uten_utbetaling\"" else "\"utbetaling_utbetalt\""},
    "fom": "${sykdomstidslinje.first().dato}",
    "tom": "${sykdomstidslinje.last().dato}",
    "maksdato": "2021-07-15",
    "forbrukteSykedager": "217",
    "gjenståendeSykedager": "31",
    "ident": "Automatisk behandlet",
    "epost": "tbd@nav.no",
    "type": "$type",
    "tidspunkt": "$opprettet",
    "automatiskBehandling": "true",
    "vedtaksperiodeIder": [
        ${vedtaksperiodeIder.joinToString { "\"$it\"" }}
    ],
    "arbeidsgiverOppdrag": ${arbeidsgiverOppdrag.toJson()},
    "personOppdrag": ${personOppdrag.toJson()},
    "utbetalingsdager": ${sykdomstidslinje.toJson()},
    "@opprettet": "$opprettet",
    "aktørId": "123",
    "organisasjonsnummer": "123456789"
}"""

    @Language("json")
    private fun utbetalingArbeidsgiver(
        fødselsnummer: String = "12345678910",
        hendelseId: UUID = UUID.randomUUID(),
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = listOf(UUID.randomUUID()),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        type: String = "UTBETALING",
        opprettet: LocalDateTime = sykdomstidslinje.last().dato.atStartOfDay(),
        arbeidsgiverOppdrag: Oppdrag = Oppdrag(sykdomstidslinje, fagområde = "SPREF"),
        brukeroppdrag: Oppdrag = Oppdrag(emptyList(), fagområde = "SP")
    ) = """{
    "@id": "$hendelseId",
    "fødselsnummer": "$fødselsnummer",
    "utbetalingId": "$utbetalingId",
    "@event_name": ${if (sykdomstidslinje.none { it.type == Dagtype.UTBETALINGSDAG }) "\"utbetaling_uten_utbetaling\"" else "\"utbetaling_utbetalt\""},
    "fom": "${sykdomstidslinje.first().dato}",
    "tom": "${sykdomstidslinje.last().dato}",
    "maksdato": "2021-07-15",
    "forbrukteSykedager": "217",
    "gjenståendeSykedager": "31",
    "ident": "Automatisk behandlet",
    "epost": "tbd@nav.no",
    "type": "$type",
    "tidspunkt": "$opprettet",
    "automatiskBehandling": "true",
    "vedtaksperiodeIder": [
        ${vedtaksperiodeIder.joinToString { "\"$it\"" }}
    ],
    "arbeidsgiverOppdrag": ${arbeidsgiverOppdrag.toJson()},
    "personOppdrag": ${brukeroppdrag.toJson()},
    "utbetalingsdager": ${sykdomstidslinje.toJson()},
    "@opprettet": "$opprettet",
    "aktørId": "123",
    "organisasjonsnummer": "123456789"
}"""

    private fun revurderingUtbetalt(
        id: UUID = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = listOf(UUID.randomUUID()),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        opprettet: LocalDateTime = sykdomstidslinje.last().dato.atStartOfDay()
    ) = utbetalingArbeidsgiver(
        hendelseId = id,
        fødselsnummer = fødselsnummer,
        utbetalingId = utbetalingId,
        vedtaksperiodeIder = vedtaksperiodeIder,
        sykdomstidslinje = sykdomstidslinje,
        type = "REVURDERING",
        opprettet = opprettet
    )

    protected fun sendRevurdering(
        hendelseId: UUID = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = emptyList(),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        opprettet: LocalDateTime = sykdomstidslinje.last().dato.atStartOfDay()
    ) {
        require(sykdomstidslinje.isNotEmpty()) { "Sykdomstidslinjen kan ikke være tom!" }
        testRapid.sendTestMessage(
            revurderingUtbetalt(
                id = hendelseId,
                fødselsnummer = fødselsnummer,
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = vedtaksperiodeIder,
                sykdomstidslinje = sykdomstidslinje,
                opprettet = opprettet
            )
        )
    }

    protected fun sendUtbetaling(
        hendelseId: UUID = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = emptyList(),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        type: String = "UTBETALING",
    ) {
        require(sykdomstidslinje.isNotEmpty()) { "Sykdomstidslinjen kan ikke være tom!" }
        testRapid.sendTestMessage(
            utbetalingArbeidsgiver(
                hendelseId = hendelseId,
                fødselsnummer = fødselsnummer,
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = vedtaksperiodeIder,
                sykdomstidslinje = sykdomstidslinje,
                type = type
            )
        )
    }

    protected fun sendBrukerutbetaling(
        hendelseId: UUID = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = emptyList(),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        type: String = "UTBETALING",
    ) {
        require(sykdomstidslinje.isNotEmpty()) { "Sykdomstidslinjen kan ikke være tom!" }
        testRapid.sendTestMessage(
            utbetalingBruker(
                hendelseId = hendelseId,
                fødselsnummer = fødselsnummer,
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = vedtaksperiodeIder,
                sykdomstidslinje = sykdomstidslinje,
                type = type
            )
        )
    }

    protected fun sendUtbetalingDelvisRefusjon(
        hendelseId: UUID = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        orgnummer: String = "123456789",
        utbetalingId: UUID = UUID.randomUUID(),
        vedtaksperiodeIder: List<UUID> = emptyList(),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        type: String = "UTBETALING",
    ) {
        require(sykdomstidslinje.isNotEmpty()) { "Sykdomstidslinjen kan ikke være tom!" }
        testRapid.sendTestMessage(
            utbetalingBruker(
                hendelseId = hendelseId,
                fødselsnummer = fødselsnummer,
                utbetalingId = utbetalingId,
                vedtaksperiodeIder = vedtaksperiodeIder,
                sykdomstidslinje = sykdomstidslinje,
                personOppdrag = Oppdrag(sykdomstidslinje, dagsats = 700, mottaker = fødselsnummer, fagområde = "SP", fagsystemId = "fagsystemIdPerson"),
                arbeidsgiverOppdrag = Oppdrag(sykdomstidslinje, dagsats = 741, mottaker = orgnummer, fagområde = "SPREF", fagsystemId = "fagsystemIdArbeidsgiver"),
                type = type
            )
        )
    }

    protected fun sendVedtakFattet(
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

    enum class Utbetalingstype(
        internal val journaltittel: String,
        internal val dokumenttittel: String,
        internal val lesbarTittel: String
    ) {
        UTBETALING("Vedtak om sykepenger", "Sykepenger behandlet i ny løsning", "utbetalt"),
        REVURDERING("Vedtak om revurdering av sykepenger", "Sykepenger revurdert i ny løsning", "revurdering av")
    }
}
