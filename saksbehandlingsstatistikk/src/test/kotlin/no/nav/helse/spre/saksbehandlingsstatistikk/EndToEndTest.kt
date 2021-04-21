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
import java.util.*

internal class EndToEndTest {
    private val testRapid = TestRapid()
    private val dataSource = DatabaseHelpers.dataSource
    private val kafkaProducer: KafkaProducer<String, String> = mockk(relaxed = true)
    private val dokumentDao = DokumentDao(dataSource)
    private val spreService = SpreService(kafkaProducer, dokumentDao)

    init {
        testRapid.setupRivers(dokumentDao, spreService)
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
            aktorId = "aktørens id",
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
            aktorId = "aktørens id",
            behandlingStatus = REGISTRERT,
            behandlingId = null
        )

        assertEquals(expected, sendtTilDVH)
    }

    @Test
    fun `Ignorerer vedtaksperiode_endret-events med dato fra før vi har en komplett dokumentdatabase`() {
        val søknadHendelseId = UUID.randomUUID()
        val søknad = Hendelse(UUID.randomUUID(), søknadHendelseId, Dokument.Søknad)

        testRapid.sendTestMessage(vedtaksperiodeEndretMessageUtdatert(listOf(søknad.hendelseId), "TIL_GODKJENNING"))

        verify(exactly = 0) { kafkaProducer.send(any()) }
    }
}

fun vedtaksperiodeEndretMessage(hendelser: List<UUID>, tilstand: String) =
    """{
            "@event_name": "vedtaksperiode_endret",
            "gjeldendeTilstand": "$tilstand",
            "hendelser": [${hendelser.joinToString { """"$it"""" }}],
            "@opprettet": "2021-03-19T18:23:27.76939",
            "vedtaksperiodeId": "${UUID.randomUUID()}",
            "aktørId": "aktørens id"
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
