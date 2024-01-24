package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class GenerasjonOpprettetRiverTest {

    @Test
    fun `tester parsing av JsonMessage for opprettelse av domeneobjekt`() {
        val generasjonId = UUID.randomUUID()
        val innsendt = LocalDateTime.now()
        val message = JsonMessage.newMessage("generasjon_opprettet", mutableMapOf(
                "aktørId" to "123",
                "vedtaksperiodeId" to UUID.randomUUID(),
                "generasjonId" to generasjonId,
                "type" to "Førstegangsbehandling",
                "kilde" to mapOf(
                        "meldingsreferanseId" to UUID.randomUUID(),
                        "innsendt" to innsendt,
                        "registrert" to LocalDateTime.now(),
                        "avsender" to "SYKMELDT"
                ),
                "@id" to UUID.randomUUID()
        )).also {
            it.requireKey("@id",
                    "aktørId",
                    "vedtaksperiodeId",
                    "generasjonId",
                    "type",
                    "kilde.meldingsreferanseId",
                    "kilde.avsender")
            it.require("kilde.innsendt", JsonNode::asLocalDateTime)
            it.require("kilde.registrert", JsonNode::asLocalDateTime)
        }

        val generasjonOpprettet = message.toGenerasjonOpprettet()
        assertEquals("123", generasjonOpprettet.aktørId)
        assertEquals("SYKMELDT", generasjonOpprettet.kilde.avsender)
        assertEquals(generasjonId, generasjonOpprettet.generasjonId)
        assertEquals(innsendt, generasjonOpprettet.kilde.innsendt)
    }
}