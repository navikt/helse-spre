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

internal class VedtakFattetE2ETest {
    private val testRapid = TestRapid()
    private val kafkaProducer: KafkaProducer<String, String> = mockk(relaxed = true)

    private val dataSource = DatabaseHelpers.dataSource
    private val koblingDao = KoblingDao(dataSource)
    private val søknadDao = SøknadDao(dataSource)

    private val spreService = SpreService(kafkaProducer, koblingDao, søknadDao)

    init {
        testRapid.setupRivers(spreService, søknadDao, koblingDao)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `lagrer ider til basen`() {
        val utbetalingId = randomUUID()
        val søknadHendelseId = randomUUID()
        val vedtaksperiodeId = randomUUID()

        val søknad = Søknad(søknadHendelseId, randomUUID(), LocalDateTime.now(), LocalDateTime.now(), null)

        testRapid.sendTestMessage(sendtSøknadNavMessage(søknad))
        testRapid.sendTestMessage(vedtakFattetMessage(listOf(søknadHendelseId), utbetalingId, vedtaksperiodeId))

        assertEquals(søknad.dokumentId, koblingDao.finnSøknadIdForUtbetalingId(utbetalingId))
        assertEquals(søknad.dokumentId, koblingDao.finnSøknadIdForVedtaksperiodeId(vedtaksperiodeId))
    }

    private fun sendtSøknadNavMessage(søknad: Søknad) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${søknad.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sendtNav": "2021-01-01T00:00:00",
            "rapportertDato": "2021-01-01T00:00:00"
        }"""

    private fun vedtakFattetMessage(hendelseIder: List<UUID>, utbetalingId: UUID, vedtaksperiodeId: UUID) =
        """{
            "@event_name": "vedtak_fattet",
            "@opprettet": "2021-04-01T12:00:00.000000",
            "aktørId": "aktørens id",
            "hendelser": [${hendelseIder.joinToString { """"$it"""" }}],
            "utbetalingId": "$utbetalingId",
            "vedtaksperiodeId": "$vedtaksperiodeId"
        }"""
}
