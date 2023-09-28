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
) {
    fun patch() = this.patch(null, ::fjernFnrFraJsonString, 1)
}

private fun fjernFnrFraJsonString(soknad: SendtSøknad): SendtSøknad {
    val jsonUtenFnr = fjernNoderFraJson(soknad.melding, listOf("fnr"))
    return soknad.copy(melding = jsonUtenFnr)
}

private fun fjernNoderFraJson(json: String, noder: List<String>): String {
    val objectNode = objectMapper.readTree(json) as ObjectNode
    noder.map { objectNode.remove(it) }
    return objectMapper.writeValueAsString(objectNode)
}

private fun SendtSøknad.patch(
    patchLevelPreCondition: Int?,
    patchFunction: (input: SendtSøknad) -> SendtSøknad,
    patchLevelPostPatch: Int
): SendtSøknad {
    if (this.patchLevel != patchLevelPreCondition) {
        return this
    }
    return patchFunction(this).copy(patchLevel = patchLevelPostPatch)
}
