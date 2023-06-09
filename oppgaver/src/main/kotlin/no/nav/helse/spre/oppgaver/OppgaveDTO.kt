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
        fun utsettSøknad(dokumentId: UUID, timeout: LocalDateTime) = utsett(dokumentId, Søknad, timeout)
        fun utsettInntektsmelding(dokumentId: UUID, timeout: LocalDateTime) = utsett(dokumentId, Inntektsmelding, timeout)

        private fun utsett(dokumentId: UUID, dokumentType: DokumentTypeDTO, timeout: LocalDateTime) = OppgaveDTO(
            dokumentType = dokumentType,
            dokumentId = dokumentId,
            oppdateringstype = Utsett,
            timeout = timeout
        )

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

enum class OppdateringstypeDTO {
    Utsett, Opprett, OpprettSpeilRelatert, Ferdigbehandlet
}

enum class DokumentTypeDTO {
    Inntektsmelding, Søknad
}
