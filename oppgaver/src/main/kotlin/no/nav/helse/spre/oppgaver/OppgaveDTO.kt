package no.nav.helse.spre.oppgaver

import no.nav.helse.spre.oppgaver.DokumentTypeDTO.*
import no.nav.helse.spre.oppgaver.OppdateringstypeDTO.*
import java.time.LocalDateTime
import java.util.UUID

data class OppgaveDTO(
    val dokumentType: DokumentTypeDTO,
    val oppdateringstype: OppdateringstypeDTO,
    val dokumentId: UUID,
    val timeout: LocalDateTime? = null
) {
    internal companion object {
        fun nySøknadoppgave(dokumentId: UUID) = nyOppgave(dokumentId, Søknad)
        fun nySøknadoppgaveSpeil(dokumentId: UUID) = nyOppgave(dokumentId, Søknad, OpprettSpeilRelatert)
        fun nyInntektsmeldingoppgave(dokumentId: UUID) = nyOppgave(dokumentId, Inntektsmelding)
        fun nyInntektsmeldingoppgaveSpeil(dokumentId: UUID) = nyOppgave(dokumentId, Inntektsmelding, OpprettSpeilRelatert)
        fun ferdigbehandletSøknad(dokumentId: UUID) = ferdigbehandlet(dokumentId, Søknad)
        fun ferdigbehandletInntektsmelding(dokumentId: UUID) = ferdigbehandlet(dokumentId, Inntektsmelding)

        private fun ferdigbehandlet(dokumentId: UUID, dokumentType: DokumentTypeDTO) = OppgaveDTO(
            dokumentType = dokumentType,
            dokumentId = dokumentId,
            oppdateringstype = Ferdigbehandlet,
            timeout = null
        )

        private fun nyOppgave(dokumentId: UUID, dokumentType: DokumentTypeDTO, oppdateringstype: OppdateringstypeDTO = Opprett) = OppgaveDTO(
            dokumentType = dokumentType,
            dokumentId = dokumentId,
            oppdateringstype = oppdateringstype,
            timeout = null,
        )
    }
}

fun DokumentType.toDTO(): DokumentTypeDTO = when (this) {
    DokumentType.Inntektsmelding -> Inntektsmelding
    DokumentType.Søknad -> Søknad
}

enum class OppdateringstypeDTO {
    Utsett, Opprett, OpprettSpeilRelatert, Ferdigbehandlet
}

enum class DokumentTypeDTO {
    Inntektsmelding, Søknad
}
