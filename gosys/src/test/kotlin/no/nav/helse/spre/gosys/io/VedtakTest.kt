package no.nav.helse.spre.gosys.io

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert.assertEquals
import org.skyscreamer.jsonassert.JSONCompareMode

class VedtakTest {

    @Test
    fun `serialisering og deserialisering av vedtakjson`() {
        val format = Json {
            ignoreUnknownKeys = true;
            prettyPrint = true;
        }

        mockUtbetalinger.forEach {
            val serialisert: IO.Vedtak = format.decodeFromString(it)
            val deserialisert: String = format.encodeToString(serialisert)

            assertEquals(deserialisert, it, JSONCompareMode.LENIENT)
        }
    }
}
