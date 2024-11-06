package no.nav.helse.spre.testhelpers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OppdragTest {

    private companion object {
        val objectMapper: ObjectMapper = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())
    }

    @Test
    fun oppdrag() {
        val oppdrag = objectMapper.readTree(
            Oppdrag(
            tidslinje = utbetalingsdager(6.januar, 13.januar)
        ).toJson())

        //ASSERT OPPDRAG
        assertEquals(1, oppdrag["linjer"].size())
        assertEquals("123456789", oppdrag["mottaker"].asText())
        assertEquals("SPREF", oppdrag["fagområde"].asText())
        assertEquals(7155, oppdrag["nettoBeløp"].asInt())
        assertEquals(5, oppdrag["stønadsdager"].asInt())
        assertEquals("fagsystemId", oppdrag["fagsystemId"].asText())
        assertEquals(13.januar.atStartOfDay(), oppdrag["tidsstempel"].asLocalDateTime())

        //ASSERT LINJER
        val linje = oppdrag["linjer"][0]
        assertEquals(6.januar, linje["fom"].asLocalDate())
        assertEquals(13.januar, linje["tom"].asLocalDate())
        assertEquals(5, linje["stønadsdager"].asInt())
        assertEquals(1431, linje["sats"].asInt())
        assertEquals(100.0, linje["grad"].asDouble())
    }

    @Test
    fun `oppdrag med to linjer`() {
        val oppdrag = objectMapper.readTree(
            Oppdrag(
            tidslinje = utbetalingsdager(6.januar, 13.januar)
                    + feriedager(14.januar, 15.januar)
                    + fridager(16.januar, 17.januar)
                    + permisjonsdager(18.januar, 19.januar)
                    + utbetalingsdager(20.januar, 30.januar)
        ).toJson())

        //ASSERT OPPDRAG
        assertEquals(2, oppdrag["linjer"].size())
        assertEquals("123456789", oppdrag["mottaker"].asText())
        assertEquals("SPREF", oppdrag["fagområde"].asText())
        assertEquals(17172, oppdrag["nettoBeløp"].asInt())
        assertEquals("fagsystemId", oppdrag["fagsystemId"].asText())
        assertEquals(12, oppdrag["stønadsdager"].asInt())
        assertEquals(30.januar.atStartOfDay(), oppdrag["tidsstempel"].asLocalDateTime())

        //ASSERT LINJER
        val linje1 = oppdrag["linjer"][0]
        assertEquals(6.januar, linje1["fom"].asLocalDate())
        assertEquals(13.januar, linje1["tom"].asLocalDate())
        assertEquals(5, linje1["stønadsdager"].asInt())
        assertEquals(1431, linje1["sats"].asInt())
        assertEquals(100.0, linje1["grad"].asDouble())

        //ASSERT LINJER
        val linje2 = oppdrag["linjer"][1]
        assertEquals(20.januar, linje2["fom"].asLocalDate())
        assertEquals(30.januar, linje2["tom"].asLocalDate())
        assertEquals(7, linje2["stønadsdager"].asInt())
        assertEquals(1431, linje2["sats"].asInt())
        assertEquals(100.0, linje2["grad"].asDouble())
    }
}
