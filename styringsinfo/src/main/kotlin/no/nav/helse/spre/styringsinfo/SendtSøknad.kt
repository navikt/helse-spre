package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class SendtSøknad(
    val sendt: LocalDateTime,
    val korrigerer: UUID?,
    val fom: LocalDate,
    val tom: LocalDate,
    val hendelseId: UUID,
    val melding: String,
    val patchLevel: Int? = null
)

private val verdierSomSkalBort = listOf("fnr")

fun fjernFnrFraJsonString(soknad: SendtSøknad): SendtSøknad {
    val objectNode = objectMapper.readTree(soknad.melding) as ObjectNode
    verdierSomSkalBort.map { objectNode.remove(it) }
    val jsonUtenFnr = objectMapper.writeValueAsString(objectNode)
    return soknad.copy(melding = jsonUtenFnr)
}

fun SendtSøknad.patch(
    patchLevelPreCondition: Int?,
    patchFunction: (input: SendtSøknad) -> SendtSøknad,
    patchLevelPostPatch: Int
): SendtSøknad {
    if (this.patchLevel != patchLevelPreCondition) {
        return this
    }
    return patchFunction(this).copy(patchLevel = patchLevelPostPatch)
}
