package no.nav.helse.spre.styringsinfo.datafortelling.domain

import no.nav.helse.spre.styringsinfo.datafortelling.dataminimering.JsonUtil.fjernRotNoderFraJson
import no.nav.helse.spre.styringsinfo.datafortelling.dataminimering.JsonUtil.traverserOgFjernNoder
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
    val patchLevel: Int = SendtSøknadPatch.UPATCHET.ordinal
) {
    fun patch() = this
        .applyPatch(SendtSøknadPatch.FNR, ::fjernFnrFraJsonString)
        .applyPatch(SendtSøknadPatch.ARBEIDSGIVER, ::fjernArbeidsgiverFraJsonString)
        .applyPatch(SendtSøknadPatch.SPØRSMÅL, ::fjernSpørsmålstekstFraJsonString)

    private fun fjernFnrFraJsonString(soknad: SendtSøknad) =
        soknad.copy(melding = fjernRotNoderFraJson(soknad.melding, listOf("fnr")))

    private fun fjernArbeidsgiverFraJsonString(soknad: SendtSøknad) =
        soknad.copy(melding = fjernRotNoderFraJson(soknad.melding, listOf("arbeidsgiver")))

    private fun fjernSpørsmålstekstFraJsonString(soknad: SendtSøknad) =
        soknad.copy(melding = traverserOgFjernNoder(soknad.melding, "sporsmalstekst"))

    private fun applyPatch(
        sendtSøknadPatch: SendtSøknadPatch,
        patchFunction: (input: SendtSøknad) -> SendtSøknad
    ) =
        if (this.patchLevel < sendtSøknadPatch.ordinal) {
            patchFunction(this).copy(patchLevel = sendtSøknadPatch.ordinal)
        } else {
            this
        }
}

enum class SendtSøknadPatch {
    UPATCHET, FNR, ARBEIDSGIVER, SPØRSMÅL
}
