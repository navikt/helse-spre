import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.spre.*
import no.nav.helse.spre.Dag.Companion.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DagerTest {

    private companion object {
        val objectMapper: ObjectMapper = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())
    }

    @Test
    fun `en enkelt dag`() {
        val dager = utbetalingsdager(1.januar)
        val expected = objectMapper.readTree(dager.toJson())
        assertTrue(expected.isArray)
        assertEquals(1, expected.size())
        assertEquals(1.januar, expected[0]["dato"].asLocalDate())
        assertEquals(Dagtype.UTBETALINGSDAG, Dagtype.from(expected[0]["type"].asText()))
    }

    @Test
    fun utbetalingsdager() {
        val dager = utbetalingsdager(1.januar, 2.januar)
        val expected = objectMapper.readTree(dager.toJson())
        assertTrue(expected.isArray)
        assertEquals(2, expected.size())
        assertEquals(1.januar, expected[0]["dato"].asLocalDate())
        assertEquals(Dagtype.UTBETALINGSDAG, Dagtype.from(expected[0]["type"].asText()))
        assertEquals(2.januar, expected[1]["dato"].asLocalDate())
        assertEquals(Dagtype.UTBETALINGSDAG, Dagtype.from(expected[1]["type"].asText()))
    }

    @Test
    fun `utbetalingsdager med helg`() {
        val dager = utbetalingsdager(6.januar, 7.januar)
        val expected = objectMapper.readTree(dager.toJson())
        assertTrue(expected.isArray)
        assertEquals(2, expected.size())
        assertEquals(6.januar, expected[0]["dato"].asLocalDate())
        assertEquals("NavHelgeDag", expected[0]["type"].asText())
        assertEquals(Dagtype.UTBETALINGSDAG, Dagtype.from(expected[0]["type"].asText()))
        assertEquals(7.januar, expected[1]["dato"].asLocalDate())
        assertEquals("NavHelgeDag", expected[1]["type"].asText())
        assertEquals(Dagtype.UTBETALINGSDAG, Dagtype.from(expected[1]["type"].asText()))
    }

    @Test
    fun fridager() {
        val dager = fridager(1.januar, 2.januar)
        val expected = objectMapper.readTree(dager.toJson())
        assertTrue(expected.isArray)
        assertEquals(2, expected.size())
        assertEquals(1.januar, expected[0]["dato"].asLocalDate())
        assertEquals(Dagtype.FRIDAG, Dagtype.from(expected[0]["type"].asText()))
        assertEquals(2.januar, expected[1]["dato"].asLocalDate())
        assertEquals(Dagtype.FRIDAG, Dagtype.from(expected[1]["type"].asText()))
    }

    @Test
    fun arbeidsdager() {
        val dager = arbeidsdager(1.januar, 2.januar)
        val expected = objectMapper.readTree(dager.toJson())
        assertTrue(expected.isArray)
        assertEquals(2, expected.size())
        assertEquals(1.januar, expected[0]["dato"].asLocalDate())
        assertEquals(Dagtype.ARBEIDSDAG, Dagtype.from(expected[0]["type"].asText()))
        assertEquals(2.januar, expected[1]["dato"].asLocalDate())
        assertEquals(Dagtype.ARBEIDSDAG, Dagtype.from(expected[1]["type"].asText()))
    }

    @Test
    fun foreldetDager() {
        val dager = foreldetDager(1.januar, 2.januar)
        val expected = objectMapper.readTree(dager.toJson())
        assertTrue(expected.isArray)
        assertEquals(2, expected.size())
        assertEquals(1.januar, expected[0]["dato"].asLocalDate())
        assertEquals(Dagtype.FORELDETDAG, Dagtype.from(expected[0]["type"].asText()))
        assertEquals(2.januar, expected[1]["dato"].asLocalDate())
        assertEquals(Dagtype.FORELDETDAG, Dagtype.from(expected[1]["type"].asText()))
    }

    @Test
    fun avvistDager() {
        val dager = avvistDager(1.januar, 2.januar)
        val expected = objectMapper.readTree(dager.toJson())
        assertTrue(expected.isArray)
        assertEquals(2, expected.size())
        assertEquals(1.januar, expected[0]["dato"].asLocalDate())
        assertEquals(Dagtype.AVVISTDAG, Dagtype.from(expected[0]["type"].asText()))
        assertEquals(2.januar, expected[1]["dato"].asLocalDate())
        assertEquals(Dagtype.AVVISTDAG, Dagtype.from(expected[1]["type"].asText()))
    }

    @Test
    fun ukjentDager() {
        val dager = ukjentDager(1.januar, 2.januar)
        val expected = objectMapper.readTree(dager.toJson())
        assertTrue(expected.isArray)
        assertEquals(2, expected.size())
        assertEquals(1.januar, expected[0]["dato"].asLocalDate())
        assertEquals(Dagtype.UKJENTDAG, Dagtype.from(expected[0]["type"].asText()))
        assertEquals(2.januar, expected[1]["dato"].asLocalDate())
        assertEquals(Dagtype.UKJENTDAG, Dagtype.from(expected[1]["type"].asText()))
    }

    @Test
    fun `utbetalingsdager + fridager`() {
        val dager = utbetalingsdager(1.januar, 2.januar) + fridager(3.januar, 4.januar)
        val expected = objectMapper.readTree(dager.toJson())
        assertTrue(expected.isArray)
        assertEquals(4, expected.size())
        assertEquals(1.januar, expected[0]["dato"].asLocalDate())
        assertEquals(Dagtype.UTBETALINGSDAG, Dagtype.from(expected[0]["type"].asText()))
        assertEquals(2.januar, expected[1]["dato"].asLocalDate())
        assertEquals(Dagtype.UTBETALINGSDAG, Dagtype.from(expected[1]["type"].asText()))
        assertEquals(3.januar, expected[2]["dato"].asLocalDate())
        assertEquals(Dagtype.FRIDAG, Dagtype.from(expected[2]["type"].asText()))
        assertEquals(4.januar, expected[3]["dato"].asLocalDate())
        assertEquals(Dagtype.FRIDAG, Dagtype.from(expected[3]["type"].asText()))
    }
}