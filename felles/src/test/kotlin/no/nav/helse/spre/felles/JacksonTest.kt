package no.nav.helse.spre.felles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

internal class JacksonTest {
    private data class NorskeFelter(
        val årsak: String,
        val ønske: String,
        val ærend: String,
    )

    @Test
    fun `properties som starter med æ, ø og å blir med ved serialisering og deserialisering`() {
        val objectMapper = spreJacksonMapperBuilder().build()
        val original = NorskeFelter(årsak = "a", ønske = "b", ærend = "c")

        val json = objectMapper.writeValueAsString(original)

        assertEquals(setOf("årsak", "ønske", "ærend"), objectMapper.readTree(json).propertyNames().toSet())
        assertEquals(original, objectMapper.readValue<NorskeFelter>(json))
    }
}
