package no.nav.helse.forsikringsoppgaver

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

fun JsonNode.asUuid() = UUID.fromString(asText())
