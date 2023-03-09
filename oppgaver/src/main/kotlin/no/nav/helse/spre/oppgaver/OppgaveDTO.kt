package no.nav.helse.spre.oppgaver

import java.time.LocalDateTime
import java.util.UUID

data class OppgaveDTO(
    val dokumentType: DokumentTypeDTO,
    val oppdateringstype: OppdateringstypeDTO,
    val dokumentId: UUID,
    val timeout: LocalDateTime? = null
) {
    internal companion object {
        fun nySøknadoppgave(dokumentId: UUID) = nyOppgave(dokumentId, DokumentTypeDTO.Søknad)
        fun nyInntektsmeldingoppgave(dokumentId: UUID) = nyOppgave(dokumentId, DokumentTypeDTO.Inntektsmelding)

        private fun nyOppgave(dokumentId: UUID, dokumentType: DokumentTypeDTO) = OppgaveDTO(
            dokumentType = dokumentType,
            oppdateringstype = OppdateringstypeDTO.Opprett,
            dokumentId = dokumentId,
            timeout = null,
        )
    }
}

fun DokumentType.toDTO(): DokumentTypeDTO = when (this) {
    DokumentType.Inntektsmelding -> DokumentTypeDTO.Inntektsmelding
    DokumentType.Søknad -> DokumentTypeDTO.Søknad
}

enum class OppdateringstypeDTO {
    Utsett, Opprett, OpprettSpeilRelatert, Ferdigbehandlet
}

enum class DokumentTypeDTO {
    Inntektsmelding, Søknad
}
