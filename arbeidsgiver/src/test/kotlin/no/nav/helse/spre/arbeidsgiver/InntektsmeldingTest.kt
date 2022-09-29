package no.nav.helse.spre.arbeidsgiver

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class InntektsmeldingTest {

    private val testRapid = TestRapid()
    private val mockproducer: KafkaProducer<String, InntektsmeldingDTO> = mockk(relaxed = true)
    private val fnr = "12345678910"
    private val orgnr = "987654321"
    private val opprettet = LocalDateTime.now()

    init {
        BeOmInntektsmeldinger(testRapid, mockproducer)
        TrengerIkkeInntektsmelding(testRapid, mockproducer)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearMocks(mockproducer)
    }

    @Test
    fun `event genererer request for inntektsmelding`() {
        testRapid.sendTestMessage(eventSomJson())
        testRapid.sendTestMessage(eventSomJson())

        val payload = InntektsmeldingDTO(
            type = Meldingstype.TRENGER_INNTEKTSMELDING,
            organisasjonsnummer = orgnr,
            fødselsnummer = fnr,
            fom = LocalDate.now(),
            tom = LocalDate.now(),
            opprettet = opprettet
        )
        verify(exactly = 2) { mockproducer.send(
            ProducerRecord("aapen-helse-spre-arbeidsgiver", null, fnr, payload, headere(payload)))
        }
        assertEquals(2, testRapid.inspektør.size)
    }

    @Test
    fun `event genererer melding om at vi ikke trenger inntektsmelding`() {
        val type = "trenger_ikke_inntektsmelding"
        testRapid.sendTestMessage(eventSomJson(type))
        testRapid.sendTestMessage(eventSomJson(type))

        val payload = InntektsmeldingDTO(
            type = Meldingstype.TRENGER_IKKE_INNTEKTSMELDING,
            organisasjonsnummer = orgnr,
            fødselsnummer = fnr,
            fom = LocalDate.now(),
            tom = LocalDate.now(),
            opprettet = opprettet
        )
        verify(exactly = 2) { mockproducer.send(
            ProducerRecord("aapen-helse-spre-arbeidsgiver", null, fnr, payload, headere(payload)))
        }
    }

    @Test
    fun `event uten event_name blir ignorert`() {
        testRapid.sendTestMessage(JsonMessage.newMessage(mapOf(
            "vedtaksperiodeId" to UUID.randomUUID(),
            "fødselsnummer" to fnr,
            "organisasjonsnummer" to orgnr,
            "@opprettet" to opprettet,
            "fom" to LocalDate.now(),
            "tom" to LocalDate.now()
        )).toJson())
        verify(exactly = 0) { mockproducer.send(any()) }
    }

    private fun headere(payload: InntektsmeldingDTO) = listOf(RecordHeader("type", payload.meldingstype))

    private fun eventSomJson(type: String = "trenger_inntektsmelding"): String {
        return JsonMessage.newMessage(
            mapOf(
                "@event_name" to type,
                "vedtaksperiodeId" to UUID.randomUUID(),
                "fødselsnummer" to fnr,
                "organisasjonsnummer" to orgnr,
                "@opprettet" to opprettet,
                "fom" to LocalDate.now(),
                "tom" to LocalDate.now()
            )
        ).toJson()
    }
}
