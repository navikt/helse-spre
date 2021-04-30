package no.nav.helse.spre.saksbehandlingsstatistikk.util

import com.fasterxml.jackson.databind.JsonNode
import java.util.*

object JsonUtil {
    fun JsonNode.asUuid(): UUID = UUID.fromString(asText())
}