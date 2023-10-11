package no.nav.helse.spre.styringsinfo.domain

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.objectMapper
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
    val patchLevel: Int = 0
) {
    fun patch() = this.patch(0, ::fjernFnrFraJsonString, 1)
}

private fun fjernFnrFraJsonString(soknad: SendtSøknad) =
    soknad.copy(melding = fjernNoderFraJson(soknad.melding, listOf("fnr")))

private fun fjernNoderFraJson(json: String, noder: List<String>): String {
    val objectNode = objectMapper.readTree(json) as ObjectNode
    noder.map { objectNode.remove(it) }
    return objectMapper.writeValueAsString(objectNode)
}

private fun SendtSøknad.patch(
    patchLevelPreCondition: Int,
    patchFunction: (input: SendtSøknad) -> SendtSøknad,
    patchLevelPostPatch: Int
): SendtSøknad =
    if (this.patchLevel == patchLevelPreCondition)
        patchFunction(this).copy(patchLevel = patchLevelPostPatch)
    else
        this
