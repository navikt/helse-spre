package no.nav.helse.spre.saksbehandlingsstatistikk

import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import java.util.UUID.randomUUID

internal class DbTest {
    private val testRapid = TestRapid()
    private val kafkaProducer: KafkaProducer<String, String> = mockk(relaxed = true)

    private val dataSource = DatabaseHelpers.dataSource
    private val søknadDao = SøknadDao(dataSource)

    private val spreService = SpreService(kafkaProducer, søknadDao)

    init {
        testRapid.setupRivers(spreService, søknadDao)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `lagrer saksbehandlingsløp for søknad`() {
        val søknadHendelseId = randomUUID()
        val vedtaksperiodeId = randomUUID()
        val saksbehandlerIdent = "AA10000"

        val søknad = Søknad(
            søknadHendelseId,
            randomUUID(),
            LocalDateTime.parse("2021-01-01T00:00:00"),
            LocalDateTime.parse("2021-01-01T01:00:00"),
            vedtaksperiodeId,
            saksbehandlerIdent
        )

        testRapid.sendTestMessage(sendtSøknadNavMessage(søknad, vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeEndretMessage(listOf(søknadHendelseId), vedtaksperiodeId))
        testRapid.sendTestMessage(vedtaksperiodeGodkjentMessage(saksbehandlerIdent, vedtaksperiodeId))
        testRapid.sendTestMessage(vedtakFattetMessage(listOf(søknadHendelseId), vedtaksperiodeId))

        assertEquals(søknad, søknadDao.finnSøknad(listOf(søknadHendelseId)))
        assertEquals(søknad, søknadDao.finnSøknad(vedtaksperiodeId))
    }

    private fun sendtSøknadNavMessage(søknad: Søknad, vedtaksperiodeId: UUID) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${søknad.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sendtNav": "2021-01-01T00:00:00",
            "rapportertDato": "2021-01-01T01:00:00",
            "vedtaksperiodeId": "$vedtaksperiodeId"
        }"""

    private fun vedtakFattetMessage(hendelseIder: List<UUID>, vedtaksperiodeId: UUID) =
        """{
            "@event_name": "vedtak_fattet",
            "@opprettet": "2021-04-01T12:00:00.000000",
            "aktørId": "aktørens id",
            "hendelser": [${hendelseIder.joinToString { """"$it"""" }}],
            "vedtaksperiodeId": "$vedtaksperiodeId"
        }"""

    fun vedtaksperiodeEndretMessage(hendelser: List<UUID>, vedtaksperiodeId: UUID) =
        """{
            "@event_name": "vedtaksperiode_endret",
            "gjeldendeTilstand": "AVVENTER_GODKJENNING",
            "hendelser": [${hendelser.joinToString { """"$it"""" }}],
            "@opprettet": "2021-03-09T18:23:27.76939",
            "aktørId": "aktørens id",
            "vedtaksperiodeId": "${vedtaksperiodeId}"
        }"""

    fun vedtaksperiodeGodkjentMessage(saksbehandlerIdent: String, vedtaksperiodeId: UUID) =
        """{
            "@event_name": "vedtaksperiode_godkjent",
            "@opprettet": "2021-03-09T18:23:27.76939",
            "saksbehandlerIdent": "${saksbehandlerIdent}",
            "vedtaksperiodeId": "${vedtaksperiodeId}"
        }"""
}
