package no.nav.helse.spre.styringsinfo.domain

import no.nav.helse.spre.styringsinfo.fjernNoderFraJson
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

private fun SendtSøknad.patch(
    patchLevelPreCondition: Int,
    patchFunction: (input: SendtSøknad) -> SendtSøknad,
    patchLevelPostPatch: Int
): SendtSøknad =
    if (this.patchLevel == patchLevelPreCondition)
        patchFunction(this).copy(patchLevel = patchLevelPostPatch)
    else
        this
