package no.nav.helse.spre.gosys.e2e

import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.result_object.ok
import com.github.navikt.tbd_libs.speed.PersonResponse
import com.github.navikt.tbd_libs.speed.SpeedClient
import com.github.navikt.tbd_libs.test_support.TestDataSource
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.EregClient
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.annullering.PlanlagtAnnulleringDao
import no.nav.helse.spre.gosys.databaseContainer
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest.Utbetalingstype.UTBETALING
import no.nav.helse.spre.gosys.e2e.VedtakOgUtbetalingE2ETest.Companion.formatted
import no.nav.helse.spre.gosys.eregResponse
import no.nav.helse.spre.gosys.feriepenger.FeriepengerMediator
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.settOppRivers
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtak.PensjonsgivendeInntekt
import no.nav.helse.spre.gosys.vedtak.SNVedtakPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype.ANNET
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype.OMREGNET_ÅRSINNTEKT
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingtype.RAPPORTERT_ÅRSINNTEKT
import no.nav.helse.spre.gosys.vedtakFattet.Skjønnsfastsettingårsak
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import no.nav.helse.spre.testhelpers.Dag
import no.nav.helse.spre.testhelpers.Dag.Companion.toJson
import no.nav.helse.spre.testhelpers.Dagtype
import no.nav.helse.spre.testhelpers.Oppdrag
import no.nav.helse.spre.testhelpers.februar
import no.nav.helse.spre.testhelpers.januar
import no.nav.helse.spre.testhelpers.utbetalingsdager
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach

internal abstract class AbstractE2ETest {

    protected val testRapid = TestRapid()
    protected lateinit var dataSource: TestDataSource
    protected var capturedJoarkRequests = mutableListOf<HttpRequestData>()
    protected var capturedPdfRequests = mutableListOf<HttpRequestData>()

    private val mockClient = httpclient()
    protected val pdfClient = PdfClient(mockClient, "http://url.no")
    private val azureMock: AzureTokenProvider = mockk {
        every { bearerToken("scope") }.returns(AzureToken("token", LocalDateTime.MAX).ok())
        every { bearerToken("JOARK_SCOPE") }.returns(
            AzureToken(
                "6B70C162-8AAB-4B56-944D-7F092423FE4B",
                LocalDateTime.MAX
            ).ok()
        )
    }
    protected val joarkClient = JoarkClient("https://url.no", azureMock, "JOARK_SCOPE", mockClient)
    protected val eregClient = EregClient("https://url.no", mockClient)
    protected val speedClient = mockk<SpeedClient> {
        every { hentPersoninfo(any(), any()) } returns PersonResponse(
            fødselsdato = LocalDate.now(),
            dødsdato = null,
            fornavn = "MOLEFONKEN",
            mellomnavn = null,
            etternavn = "ERT",
            adressebeskyttelse = PersonResponse.Adressebeskyttelse.UGRADERT,
            kjønn = PersonResponse.Kjønn.UKJENT
        ).ok()
    }

    protected lateinit var duplikatsjekkDao: DuplikatsjekkDao
    protected lateinit var vedtakFattetDao: VedtakFattetDao
    protected lateinit var utbetalingDao: UtbetalingDao
    protected lateinit var planlagtAnnulleringDao: PlanlagtAnnulleringDao
    protected val feriepengerMediator = FeriepengerMediator(pdfClient, joarkClient)

    @BeforeEach
    internal fun abstractSetup() {
        dataSource = databaseContainer.nyTilkobling()

        duplikatsjekkDao = DuplikatsjekkDao(dataSource.ds)
        vedtakFattetDao = VedtakFattetDao(dataSource.ds)
        utbetalingDao = UtbetalingDao(dataSource.ds)
        planlagtAnnulleringDao = PlanlagtAnnulleringDao(dataSource.ds)

        testRapid.settOppRivers(
            duplikatsjekkDao,
            feriepengerMediator,
            vedtakFattetDao,
            utbetalingDao,
            planlagtAnnulleringDao,
            pdfClient,
            joarkClient,
            eregClient,
            speedClient
        )
        capturedJoarkRequests.clear()
        capturedPdfRequests.clear()
    }

    @AfterEach
    fun after() {
        databaseContainer.droppTilkobling(dataSource)
        testRapid.reset()
    }

    private fun httpclient(): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper))
            }
            engine {
                addHandler { request ->
                    when (request.url.fullPath) {

                        "/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true" -> handlerForJoark(request)

                        "/api/v1/genpdf/spre-gosys/vedtak",
                        "/api/v1/genpdf/spre-gosys/ferdig_annullering",
                        "/api/v1/genpdf/spre-gosys/vedtak_selvstendig",
                        "/api/v1/genpdf/spre-gosys/annullering" -> handlerForPdfKall(request)

                        "/v1/organisasjon/123456789" -> handlerForEregKall(request)

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

    open fun MockRequestHandleScope.handlerForEregKall(request: HttpRequestData): HttpResponseData {
        return respond(eregResponse().toByteArray())
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

    protected fun assertSNVedtakPdf(expected: SNVedtakPdfPayload) {
        val pdfPayload = capturedPdfRequests.single().parsePayload<SNVedtakPdfPayload>()
        Assertions.assertEquals(expected, pdfPayload)
    }

    protected fun expectedPdfPayload(
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        utbetalingstype: Utbetalingstype = UTBETALING,
        totaltTilUtbetaling: Int = 32913,
        behandlingsdato: LocalDate = tom,
        linjer: List<VedtakPdfPayload.Linje> = listOf(
            VedtakPdfPayload.Linje(
                fom = fom,
                tom = tom,
                grad = 100,
                dagsats = 1431,
                mottaker = "Arbeidsgiver",
                totalbeløp = totaltTilUtbetaling,
                erOpphørt = false,
            )
        ),
        arbeidsgiverOppdrag: VedtakPdfPayload.Oppdrag? = null,
        personOppdrag: VedtakPdfPayload.Oppdrag? = null,
        ikkeUtbetalteDager: List<VedtakPdfPayload.IkkeUtbetalteDager> = emptyList(),
        maksdato: LocalDate = LocalDate.of(2021, 7, 15),
        godkjentAv: String = "Automatisk behandlet",
        dagerIgjen: Int = 31,
        skjæringstidspunkt: LocalDate = fom,
        avviksprosent: Double = 20.0,
        skjønnsfastsettingtype: Skjønnsfastsettingtype = OMREGNET_ÅRSINNTEKT,
        skjønnsfastsettingårsak: Skjønnsfastsettingårsak = Skjønnsfastsettingårsak.ANDRE_AVSNITT,
        arbeidsgivere: List<VedtakPdfPayload.ArbeidsgiverData> = listOf(
            VedtakPdfPayload.ArbeidsgiverData(
                organisasjonsnummer = "123456789",
                omregnetÅrsinntekt = 565260.0,
                innrapportertÅrsinntekt = 500000.0,
                skjønnsfastsatt = 565260.0
            )
        ),
        begrunnelser: Map<String, String>? = mapOf("innvilgelse" to ""),
        vedtakFattetTidspunkt: LocalDateTime = AbstractE2ETest.vedtakFattetTidspunkt,
    ) =
        VedtakPdfPayload(
            fødselsnummer = "12345678910",
            type = utbetalingstype.lesbarTittel,
            fom = fom,
            tom = tom,
            linjer = linjer,
            personOppdrag = personOppdrag,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag,
            organisasjonsnummer = "123456789",
            behandlingsdato = behandlingsdato,
            dagerIgjen = dagerIgjen,
            automatiskBehandling = godkjentAv == "Automatisk behandlet",
            godkjentAv = godkjentAv,
            sumNettoBeløp = totaltTilUtbetaling,
            ikkeUtbetalteDager = ikkeUtbetalteDager,
            maksdato = maksdato,
            sykepengegrunnlag = 565260.0,
            sumTotalBeløp = linjer.sumOf { it.totalbeløp },
            organisasjonsnavn = "PENGELØS SPAREBANK",
            navn = "Molefonken Ert",
            skjæringstidspunkt = skjæringstidspunkt,
            avviksprosent = avviksprosent,
            skjønnsfastsettingtype = when (skjønnsfastsettingtype) {
                OMREGNET_ÅRSINNTEKT -> "Omregnet årsinntekt"
                RAPPORTERT_ÅRSINNTEKT -> "Rapportert årsinntekt"
                ANNET -> "Annet"
            },
            skjønnsfastsettingårsak = when (skjønnsfastsettingårsak) {
                Skjønnsfastsettingårsak.ANDRE_AVSNITT -> "Skjønnsfastsettelse ved mer enn 25 % avvik (§ 8-30 andre avsnitt)"
                Skjønnsfastsettingårsak.TREDJE_AVSNITT -> "Skjønnsfastsettelse ved mangelfull eller uriktig rapportering (§ 8-30 tredje avsnitt)"
            },
            arbeidsgivere = arbeidsgivere,
            begrunnelser = begrunnelser,
            vedtakFattetTidspunkt = vedtakFattetTidspunkt,
        )

    protected fun expectedSNPdfPayload(
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        utbetalingstype: Utbetalingstype = UTBETALING,
        totaltTilUtbetaling: Int = 32913,
        behandlingsdato: LocalDate = tom,
        linjer: List<VedtakPdfPayload.Linje>,
        arbeidsgiverOppdrag: VedtakPdfPayload.Oppdrag? = null,
        personOppdrag: VedtakPdfPayload.Oppdrag? = null,
        ikkeUtbetalteDager: List<VedtakPdfPayload.IkkeUtbetalteDager> = emptyList(),
        maksdato: LocalDate = LocalDate.of(2021, 7, 15),
        godkjentAv: String = "Automatisk behandlet",
        dagerIgjen: Int = 31,
        skjæringstidspunkt: LocalDate = fom,
        beregningsgrunnlag: BigDecimal = BigDecimal("565260.0"),
        begrunnelser: Map<String, String>? = mapOf("innvilgelse" to ""),
        vedtakFattetTidspunkt: LocalDateTime = AbstractE2ETest.vedtakFattetTidspunkt,
        pensjonsgivendeInntekter: List<PensjonsgivendeInntekt> = listOf(
            PensjonsgivendeInntekt(
                årstall = 2024,
                beløp = BigDecimal("600000")
            ),
            PensjonsgivendeInntekt(
                årstall = 2023,
                beløp = BigDecimal("600000")
            ),
            PensjonsgivendeInntekt(
                årstall = 2022,
                beløp = BigDecimal("600000")
            )
        )
    ) =
        SNVedtakPdfPayload(
            fødselsnummer = "12345678910",
            type = utbetalingstype.lesbarTittel,
            fom = fom,
            tom = tom,
            linjer = linjer,
            personOppdrag = personOppdrag,
            arbeidsgiverOppdrag = arbeidsgiverOppdrag,
            behandlingsdato = behandlingsdato,
            dagerIgjen = dagerIgjen,
            automatiskBehandling = godkjentAv == "Automatisk behandlet",
            godkjentAv = godkjentAv,
            sumNettoBeløp = totaltTilUtbetaling,
            sumTotalBeløp = linjer.sumOf { it.totalbeløp },
            ikkeUtbetalteDager = ikkeUtbetalteDager,
            maksdato = maksdato,
            sykepengegrunnlag = BigDecimal("565260.0"),
            navn = "Molefonken Ert",
            skjæringstidspunkt = skjæringstidspunkt,
            begrunnelser = begrunnelser,
            beregningsgrunnlag = beregningsgrunnlag,
            vedtakFattetTidspunkt = vedtakFattetTidspunkt,
            pensjonsgivendeInntekter = pensjonsgivendeInntekter,
        )

    protected fun expectedJournalpost(
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        utbetalingstype: Utbetalingstype = UTBETALING,
        journalpostTittel: String = utbetalingstype.journaltittel,
        dokumentTittel: String = "${utbetalingstype.dokumenttittel}, ${fom.formatted()} - ${tom.formatted()}",
        eksternReferanseId: UUID = UUID.randomUUID(),
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
            ),
            eksternReferanseId = eksternReferanseId.toString(),
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
        vedtakFattetTidspunkt: LocalDateTime,
    ) = """{
    "@id": "$hendelseId",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "fødselsnummer": "$fnr",
    "utbetalingId": ${utbetalingId?.let { "\"$it\"" }},
    "@event_name": "vedtak_fattet",
    "@opprettet": "${tom.atStartOfDay()}",
    "fom": "$fom",
    "tom": "$tom",
    "vedtakFattetTidspunkt": "$vedtakFattetTidspunkt",
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
    "system_read_count": 0,
    "begrunnelser": [
        {
            "type": "Innvilgelse",
            "begrunnelse": "",
            "perioder": [
                {
                    "fom": "2023-11-13",
                    "tom": "2023-12-01"
                }
            ]
        }
    ],
    "sykepengegrunnlagsfakta": {
      "omregnetÅrsinntekt": 565260.0,
      "fastsatt": "EtterSkjønn",
      "innrapportertÅrsinntekt": 500000.0,
      "avviksprosent": 20.0,
      "seksG": 711720.0,
      "tags": ["6GBegrenset"],
      "skjønnsfastsettingtype": "OMREGNET_ÅRSINNTEKT",
      "skjønnsfastsettingårsak": "ANDRE_AVSNITT",
      "skjønnsfastsatt": 565260.0,
      "arbeidsgivere": [
        {
          "arbeidsgiver": "123456789",
          "omregnetÅrsinntekt": 565260.0,
          "innrapportertÅrsinntekt": 500000.0,
          "skjønnsfastsatt": 565260.0
        }
      ]
    }
}"""

    @Language("json")
    private fun vedtakFattetSN(
        hendelseId: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        utbetalingId: UUID? = UUID.randomUUID(),
        fnr: String = "12345678910",
        fom: LocalDate = 1.januar,
        tom: LocalDate = 31.januar,
        vedtakFattetTidspunkt: LocalDateTime,
    ) = """{
    "@id": "$hendelseId",
    "vedtaksperiodeId": "$vedtaksperiodeId",
    "fødselsnummer": "$fnr",
    "utbetalingId": ${utbetalingId?.let { "\"$it\"" }},
    "@event_name": "vedtak_fattet",
    "@opprettet": "${tom.atStartOfDay()}",
    "fom": "$fom",
    "tom": "$tom",
    "vedtakFattetTidspunkt": "$vedtakFattetTidspunkt",
    "yrkesaktivitetstype": "SELVSTENDIG",
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
    "organisasjonsnummer": "SELVSTENDIG",
    "system_read_count": 0,
    "begrunnelser": [
        {
            "type": "Innvilgelse",
            "begrunnelse": "",
            "perioder": [
                {
                    "fom": "2023-11-13",
                    "tom": "2023-12-01"
                }
            ]
        }
    ],
    "sykepengegrunnlagsfakta": {
      "fastsatt": "EtterHovedregel",
      "seksG": 711720.0,
      "tags": ["6GBegrenset"],
      "selvstendig": {
        "beregningsgrunnlag": 565260.0,
        "pensjonsgivendeInntekter": [
          {
            "årstall": 2024,
            "beløp": 600000
          },
          {
            "årstall": 2023,
            "beløp": 600000
          },
          {
            "årstall": 2022,
            "beløp": 600000
          }
        ]
      }
    }
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
        personOppdrag: Oppdrag = Oppdrag(
            sykdomstidslinje,
            fagområde = "SP",
            mottaker = fødselsnummer,
            fagsystemId = "fagsystemIdPerson"
        ),
        arbeidsgiverOppdrag: Oppdrag = Oppdrag(
            emptyList(),
            fagområde = "SPREF",
            fagsystemId = "fagsystemIdArbeidsgiver"
        )
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
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        type: String = "UTBETALING",
        opprettet: LocalDateTime = sykdomstidslinje.last().dato.atStartOfDay(),
        arbeidsgiverOppdrag: Oppdrag = Oppdrag(
            sykdomstidslinje,
            fagområde = "SPREF",
            fagsystemId = "fagsystemIdArbeidsgiver"
        ),
        brukeroppdrag: Oppdrag = Oppdrag(emptyList(), fagområde = "SP", fagsystemId = "fagsystemIdPerson")
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
    "arbeidsgiverOppdrag": ${arbeidsgiverOppdrag.toJson()},
    "personOppdrag": ${brukeroppdrag.toJson()},
    "utbetalingsdager": ${sykdomstidslinje.toJson()},
    "@opprettet": "$opprettet",
    "aktørId": "123",
    "organisasjonsnummer": "123456789"
}"""

    protected fun sendUtbetaling(
        hendelseId: UUID = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        utbetalingId: UUID = UUID.randomUUID(),
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        type: String = "UTBETALING",
    ) {
        require(sykdomstidslinje.isNotEmpty()) { "Sykdomstidslinjen kan ikke være tom!" }
        testRapid.sendTestMessage(
            utbetalingArbeidsgiver(
                fødselsnummer = fødselsnummer,
                hendelseId = hendelseId,
                utbetalingId = utbetalingId,
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
                personOppdrag = Oppdrag(
                    sykdomstidslinje,
                    sats = 700,
                    mottaker = fødselsnummer,
                    fagområde = "SP",
                    fagsystemId = "fagsystemIdPerson"
                ),
                arbeidsgiverOppdrag = Oppdrag(
                    sykdomstidslinje,
                    sats = 741,
                    mottaker = orgnummer,
                    fagområde = "SPREF",
                    fagsystemId = "fagsystemIdArbeidsgiver"
                ),
                type = type
            )
        )
    }

    protected fun sendVedtakFattet(
        hendelseId: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        utbetalingId: UUID? = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        vedtakFattetTidspunkt: LocalDateTime = AbstractE2ETest.vedtakFattetTidspunkt,
    ) {
        require(sykdomstidslinje.isNotEmpty()) { "Sykdomstidslinjen kan ikke være tom!" }
        testRapid.sendTestMessage(
            vedtakFattet(
                hendelseId = hendelseId,
                fnr = fødselsnummer,
                utbetalingId = utbetalingId,
                vedtaksperiodeId = vedtaksperiodeId,
                fom = sykdomstidslinje.first().dato,
                tom = sykdomstidslinje.last().dato,
                vedtakFattetTidspunkt = vedtakFattetTidspunkt
            )
        )
    }

    protected fun sendSNVedtakFattet(
        hendelseId: UUID = UUID.randomUUID(),
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        utbetalingId: UUID? = UUID.randomUUID(),
        fødselsnummer: String = "12345678910",
        sykdomstidslinje: List<Dag> = utbetalingsdager(1.januar, 31.januar),
        vedtakFattetTidspunkt: LocalDateTime = AbstractE2ETest.vedtakFattetTidspunkt,
    ) {
        require(sykdomstidslinje.isNotEmpty()) { "Sykdomstidslinjen kan ikke være tom!" }
        testRapid.sendTestMessage(
            vedtakFattetSN(
                hendelseId = hendelseId,
                fnr = fødselsnummer,
                utbetalingId = utbetalingId,
                vedtaksperiodeId = vedtaksperiodeId,
                fom = sykdomstidslinje.first().dato,
                tom = sykdomstidslinje.last().dato,
                vedtakFattetTidspunkt = vedtakFattetTidspunkt
            )
        )
    }

    protected fun harLagretTilDuplikattabellen(hendelseId: UUID): Boolean =
        antallRaderIDuplikattabellen(hendelseId) == 1

    protected fun harIkkeLagretTilDuplikattabellen(hendelseId: UUID): Boolean =
        antallRaderIDuplikattabellen(hendelseId) == 0

    private fun antallRaderIDuplikattabellen(hendelseId: UUID) = sessionOf(dataSource.ds).use { session ->
        @Language("PostgreSQL") val query = "SELECT COUNT(1) FROM duplikatsjekk WHERE id=?"
        session.run(queryOf(query, hendelseId).map { it.int(1) }.asSingle)
    }

    enum class Utbetalingstype(
        internal val journaltittel: String,
        internal val dokumenttittel: String,
        internal val lesbarTittel: String
    ) {
        UTBETALING("Vedtak om sykepenger", "Sykepenger behandlet", "utbetaling av"),
        REVURDERING("Vedtak om revurdering av sykepenger", "Sykepenger revurdert", "revurdering av")
    }

    companion object {
        val vedtakFattetTidspunkt = 1.februar.atStartOfDay()
    }
}
