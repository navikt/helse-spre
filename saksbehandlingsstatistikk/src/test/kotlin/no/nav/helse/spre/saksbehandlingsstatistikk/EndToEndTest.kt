package no.nav.helse.spre.saksbehandlingsstatistikk

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.verify
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.saksbehandlingsstatistikk.BehandlingStatus.REGISTRERT
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.streams.asSequence

internal class EndToEndTest {
    private val testRapid = TestRapid()
    private val dataSource = DatabaseHelpers.dataSource
    private val kafkaProducer: KafkaProducer<String, String> = mockk(relaxed = true)
    private val dokumentDao = DokumentDao(dataSource)
    private val spreService = SpreService(kafkaProducer, dokumentDao)

    init {
        NyttDokumentRiver(testRapid, dokumentDao)
        VedtaksperiodeEndretRiver(testRapid, spreService, dokumentDao)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "TRUNCATE TABLE hendelse"
            session.run(queryOf(query).asExecute)
        }
    }

    @Test
    fun `Spleis reagerer på søknad`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)

        testRapid.sendTestMessage(sendtSøknadNavMessage(sykmelding, søknad))
        testRapid.sendTestMessage(vedtaksperiodeEndretMessage(listOf(søknad.hendelseId, sykmelding.hendelseId), "AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP"))

        val capture = CapturingSlot<ProducerRecord<String, String>>()
        verify { kafkaProducer.send(capture(capture), any()) }
        val record = capture.captured

        val sendtTilDVH = objectMapper.readValue<StatistikkEvent>(record.value())
        val expected = StatistikkEvent(
            aktorId = "aktorId",
            behandlingStatus = REGISTRERT,
            behandlingId = søknad.dokumentId
        )

        assertEquals(expected, sendtTilDVH)
    }

    @Test
    fun `Kan sende til DVH når vi bare har fått sykmelding`() {
        val søknadHendelseId = UUID.randomUUID()
        val sykmelding = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Sykmelding)

        testRapid.sendTestMessage(sykmeldingMessage(sykmelding))
        testRapid.sendTestMessage(vedtaksperiodeEndretMessage(listOf(sykmelding.hendelseId), "MOTTATT_SYKMELDING_FERDIG_GAP"))

        val capture = CapturingSlot<ProducerRecord<String, String>>()
        verify { kafkaProducer.send(capture(capture), any()) }
        val record = capture.captured

        val sendtTilDVH = objectMapper.readValue<StatistikkEvent>(record.value())
        val expected = StatistikkEvent(
            aktorId = "aktorId",
            behandlingStatus = REGISTRERT,
            behandlingId = null
        )

        assertEquals(expected, sendtTilDVH)
    }

    @Test
    fun `Kan sende til DVH når vi bare har fått sykmelding2`() {
        testRapid.sendTestMessage(vedtaksperiodeEndretMessage(listOf(UUID.randomUUID()), "MOTTATT_SYKMELDING_FERDIG_GAP"))

        val capture = CapturingSlot<ProducerRecord<String, String>>()
        verify(exactly = 0) { kafkaProducer.send(capture(capture), any()) }
    }

    @Test
    fun `Ignorerer vedtaksperiode_endret-events med dato fra før vi har en komplett dokumentdatabase`() {
        val søknadHendelseId = UUID.randomUUID()
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)

        testRapid.sendTestMessage(vedtaksperiodeEndretMessageUtdatert(listOf(søknad.hendelseId), "TIL_GODKJENNING"))

        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Language("JSON")
    private fun utbetalingMessage(
        hendelseId: UUID,
        fom: LocalDate,
        tom: LocalDate,
        tidligereBrukteSykedager: Int,
        hendelser: List<Hendelse>,
        fagsystemId: String = "77ATRH3QENHB5K4XUY4LQ7HRTY"
    ) = """{
    "aktørId": "aktørId",
    "fødselsnummer": "FNR",
    "organisasjonsnummer": "ORGNUMMER",
    "hendelser": ${hendelser.map { "\"${it.hendelseId}\"" }},
    "utbetalt": [
        {
            "mottaker": "ORGNUMMER",
            "fagområde": "SPREF",
            "fagsystemId": "$fagsystemId",
            "førsteSykepengedag": "",
            "totalbeløp": 8586,
            "utbetalingslinjer": [
                {
                    "fom": "$fom",
                    "tom": "$tom",
                    "dagsats": 1431,
                    "beløp": 1431,
                    "grad": 100.0,
                    "sykedager": ${sykedager(fom, tom)}
                }
            ]
        },
        {
            "mottaker": "FNR",
            "fagområde": "SP",
            "fagsystemId": "353OZWEIBBAYZPKU6WYKTC54SE",
            "totalbeløp": 0,
            "utbetalingslinjer": []
        }
    ],
    "fom": "$fom",
    "tom": "$tom",
    "forbrukteSykedager": ${tidligereBrukteSykedager + sykedager(fom, tom)},
    "gjenståendeSykedager": ${248 - tidligereBrukteSykedager - sykedager(fom, tom)},
    "maksdato": "${maksdato(tidligereBrukteSykedager, fom, tom)}",
    "opprettet": "2020-05-04T11:26:30.23846",
    "system_read_count": 0,
    "@event_name": "utbetalt",
    "@id": "$hendelseId",
    "@opprettet": "2020-05-04T11:27:13.521000",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "cf28fbba-562e-4841-b366-be1456fdccee",
        "opprettet": "2020-05-04T11:26:47.088455"
    }
}
"""

    private fun sykedager(fom: LocalDate, tom: LocalDate) =
        fom.datesUntil(tom.plusDays(1)).asSequence()
            .filter { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.count()

    private fun maksdato(tidligereBrukteSykedager: Int, fom: LocalDate, tom: LocalDate) =
        (0..247 - sykedager(fom, tom) - tidligereBrukteSykedager).fold(tom) { tilOgMed, _ ->
            if (tilOgMed.dayOfWeek in listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) tilOgMed.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            else tilOgMed.plusDays(1)
        }
}

fun vedtaksperiodeEndretMessage(hendelser: List<UUID>, tilstand: String) =
    """{
            "@event_name": "vedtaksperiode_endret",
            "gjeldendeTilstand": "$tilstand",
            "hendelser": [${hendelser.joinToString { """"$it"""" }}],
            "@opprettet": "2021-03-19T18:23:27.76939",
            "vedtaksperiodeId": "${UUID.randomUUID()}"
        }"""

fun vedtaksperiodeEndretMessageUtdatert(hendelser: List<UUID>, tilstand: String) =
    """{
            "@event_name": "vedtaksperiode_endret",
            "gjeldendeTilstand": "$tilstand",
            "hendelser": [${hendelser.joinToString { """"$it"""" }}],
            "@opprettet": "2021-03-09T18:23:27.76939"
        }"""

fun sendtSøknadNavMessage(sykmelding: Hendelse, søknad: Hendelse) =
    """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${søknad.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""

fun sykmeldingMessage(sykmelding: Hendelse) =
    """{
            "@event_name": "ny_søknad",
            "@id": "${sykmelding.hendelseId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
        }"""

fun inntektsmeldingMessage(hendelse: Hendelse) =
    """{
            "@event_name": "inntektsmelding",
            "@id": "${hendelse.hendelseId}",
            "inntektsmeldingId": "${hendelse.dokumentId}"
        }"""
