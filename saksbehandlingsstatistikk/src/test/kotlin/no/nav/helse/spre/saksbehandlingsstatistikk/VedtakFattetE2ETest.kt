package no.nav.helse.spre.saksbehandlingsstatistikk

import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.UUID.randomUUID

internal class VedtakFattetE2ETest {
    private val testRapid = TestRapid()
    private val kafkaProducer: KafkaProducer<String, String> = mockk(relaxed = true)

    private val dataSource = DatabaseHelpers.dataSource
    private val dokumentDao = DokumentDao(dataSource)
    private val søknadDao = KoblingDao(dataSource)
    private val spreService = SpreService(kafkaProducer, dokumentDao, søknadDao)

    init {
        testRapid.setupRivers(dokumentDao, spreService)
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

        val sykmelding = Hendelse(randomUUID(), søknadHendelseId, Dokument.Sykmelding)
        val søknad = Hendelse(randomUUID(), søknadHendelseId, Dokument.Søknad)

        testRapid.sendTestMessage(sendtSøknadMessage(sykmelding, søknad))
        testRapid.sendTestMessage(vedtakFattetMessage(listOf(søknadHendelseId), utbetalingId, vedtaksperiodeId))

        assertEquals(søknad.dokumentId, søknadDao.finnSøknadIdForUtbetalingId(utbetalingId))
        assertEquals(søknad.dokumentId, søknadDao.finnSøknadIdForVedtaksperiodeId(vedtaksperiodeId))
    }

    private fun sendtSøknadMessage(sykmelding: Hendelse, søknad: Hendelse) =
        """{
            "@event_name": "sendt_søknad_nav",
            "@id": "${sykmelding.hendelseId}",
            "id": "${søknad.dokumentId}",
            "sykmeldingId": "${sykmelding.dokumentId}"
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
