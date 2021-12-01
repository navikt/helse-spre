package no.nav.helse.spre.stonadsstatistikk

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.verify
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.spre.stonadsstatistikk.DatabaseHelpers.Companion.dataSource
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class EndToEndAnnulleringTest {
    private val testRapid = TestRapid()
    private val dokumentDao = mockk<DokumentDao>()
    private val utbetaltDao = mockk<UtbetaltDao>()
    private val annulleringDao = AnnulleringDao(dataSource)
    private val kafkaStønadProducer: KafkaProducer<String, String> = mockk(relaxed = true)
    private val utbetaltService = UtbetaltService(utbetaltDao, dokumentDao, annulleringDao, kafkaStønadProducer)

    init {
        AnnullertRiver(testRapid, utbetaltService)

        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "TRUNCATE TABLE utbetaling, oppdrag, vedtak, hendelse, vedtak_utbetalingsref, annullering"
            session.run(queryOf(query).asExecute)
        }
    }

    @Test
    fun `håndterer utbetaling_annullert event ved full refusjon`() {
        val fødselsnummer = "12345678910"
        val arbeidsgiverFagsystemId = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val annullertAvSaksbehandlerTidspunkt = LocalDateTime.of(2020, 1, 1, 1, 1)

        testRapid.sendTestMessage(utbetalingAnnullert(
            fødselsnummer = fødselsnummer,
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
            personFagsystemId = null,
            annullertAvSaksbehandlerTidspunkt = annullertAvSaksbehandlerTidspunkt
        ))

        val capture = CapturingSlot<ProducerRecord<String, String>>()
        verify { kafkaStønadProducer.send(capture(capture)) }

        val record = capture.captured
        val sendtTilStønad = objectMapper.readValue<Annullering>(record.value())
        val event = Annullering(fødselsnummer, arbeidsgiverFagsystemId, sendtTilStønad.annulleringstidspunkt)
        val lagretAnnullering = annulleringDao.hentAnnulleringer().first()

        assertEquals("ANNULLERING", String(record.headers().headers("type").first().value()))
        assertEquals(event, sendtTilStønad)
        assertEquals(event, lagretAnnullering)
    }

    @Test
    fun `håndterer utbetaling_annullert event ved ingen refusjon`() {
        val fødselsnummer = "12345678910"
        val personFagsystemId = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val annullertAvSaksbehandlerTidspunkt = LocalDateTime.of(2020, 1, 1, 1, 1)

        testRapid.sendTestMessage(utbetalingAnnullert(
            fødselsnummer = fødselsnummer,
            arbeidsgiverFagsystemId = null,
            personFagsystemId = personFagsystemId,
            annullertAvSaksbehandlerTidspunkt = annullertAvSaksbehandlerTidspunkt
        ))

        val capture = CapturingSlot<ProducerRecord<String, String>>()
        verify { kafkaStønadProducer.send(capture(capture)) }

        val record = capture.captured
        val sendtTilStønad = objectMapper.readValue<Annullering>(record.value())
        val event = Annullering(fødselsnummer, personFagsystemId, sendtTilStønad.annulleringstidspunkt)
        val lagretAnnullering = annulleringDao.hentAnnulleringer().first()

        assertEquals("ANNULLERING", String(record.headers().headers("type").first().value()))
        assertEquals(event, sendtTilStønad)
        assertEquals(event, lagretAnnullering)
    }

    @Test
    fun `håndterer utbetaling_annullert event ved delvis refusjon`() {
        val fødselsnummer = "12345678910"
        val arbeidsgiverFagsystemId = "ABCDEFGHIJKLMNOPQRSTUVWXYF"
        val personFagsystemId = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val annullertAvSaksbehandlerTidspunkt = LocalDateTime.of(2020, 1, 1, 1, 1)

        testRapid.sendTestMessage(utbetalingAnnullert(
            fødselsnummer = fødselsnummer,
            arbeidsgiverFagsystemId = arbeidsgiverFagsystemId,
            personFagsystemId = personFagsystemId,
            annullertAvSaksbehandlerTidspunkt = annullertAvSaksbehandlerTidspunkt
        ))

        val capture = mutableListOf<ProducerRecord<String, String>>()

        verify { kafkaStønadProducer.send(capture(capture)) }

        val arbeidsgiverRecord = capture.first { it.value().contains(arbeidsgiverFagsystemId) }
        val arbeidsgiverSendtTilStønad = objectMapper.readValue<Annullering>(arbeidsgiverRecord.value())
        val arbeidsgiverEvent = Annullering(fødselsnummer, arbeidsgiverFagsystemId, arbeidsgiverSendtTilStønad.annulleringstidspunkt)
        val arbeidsgiverLagretAnnullering = annulleringDao.hentAnnulleringer().first { it.fagsystemId == arbeidsgiverFagsystemId }

        val personRecord = capture.first { it.value().contains(personFagsystemId) }
        val personSendtTilStønad = objectMapper.readValue<Annullering>(personRecord.value())
        val personEvent = Annullering(fødselsnummer, personFagsystemId, personSendtTilStønad.annulleringstidspunkt)
        val personLagretAnnullering = annulleringDao.hentAnnulleringer().first { it.fagsystemId == personFagsystemId }

        assertEquals(2, capture.size)

        assertEquals("ANNULLERING", String(arbeidsgiverRecord.headers().headers("type").first().value()))
        assertEquals(arbeidsgiverEvent, arbeidsgiverSendtTilStønad)
        assertEquals(arbeidsgiverEvent, arbeidsgiverLagretAnnullering)

        assertEquals("ANNULLERING", String(personRecord.headers().headers("type").first().value()))
        assertEquals(personEvent, personSendtTilStønad)
        assertEquals(personEvent, personLagretAnnullering)
    }

    @Language("JSON")
    private fun utbetalingAnnullert(
        fødselsnummer: String,
        arbeidsgiverFagsystemId: String?,
        personFagsystemId: String?,
        annullertAvSaksbehandlerTidspunkt: LocalDateTime
    ) = """
        {
          "utbetalingId": "${UUID.randomUUID()}",
          "arbeidsgiverFagsystemId": ${if (arbeidsgiverFagsystemId != null) "\"$arbeidsgiverFagsystemId\"" else null},
          "personFagsystemId": ${if (personFagsystemId != null) "\"$personFagsystemId\"" else null},
          "tidspunkt": "$annullertAvSaksbehandlerTidspunkt",
          "epost": "saksbehandler@nav.no",
          "@event_name": "utbetaling_annullert",
          "@id": "5132bee3-646d-4992-95a2-5c94cacd0807",
          "@opprettet": "2020-12-15T14:45:00.000000",
          "aktørId": "1111110000000",
          "fødselsnummer": "$fødselsnummer",
          "organisasjonsnummer": "987654321"
        }
    """
}
