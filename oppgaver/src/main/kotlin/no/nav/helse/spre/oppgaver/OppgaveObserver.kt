package no.nav.helse.spre.oppgaver

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.spre.oppgaver.DokumentType.Søknad
import java.time.LocalDateTime
import java.util.*

class OppgaveObserver(
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
    private val rapidsConnection: RapidsConnection
) : Oppgave.Observer {

    override fun lagre(oppgave: Oppgave) {
        oppgaveDAO.oppdaterTilstand(oppgave)
    }

    override fun publiser(oppgave: Oppgave) {
        val dto = OppgaveDTO(
            dokumentType = oppgave.dokumentType.toDTO(),
            oppdateringstype = oppgave.tilstand.toDTO(),
            dokumentId = oppgave.dokumentId,
            timeout = oppgave.timeout(),
        )

        publisist.publiser(oppgave.dokumentId.toString(), dto)

        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    "@event_name" to oppgave.tilstand.toEventName(),
                    "@id" to UUID.randomUUID(),
                    "dokumentId" to oppgave.dokumentId,
                    "hendelseId" to oppgave.hendelseId
                )
            ).toJson()
        )

        log.info(
            "Publisert oppgave på ${oppgave.dokumentType.name} i tilstand: ${oppgave.tilstand} med ider: {}, {}",
            StructuredArguments.keyValue("hendelseId", oppgave.hendelseId),
            StructuredArguments.keyValue("dokumentId", oppgave.dokumentId)
        )
    }

    private fun Oppgave.Tilstand.toDTO(): OppdateringstypeDTO = when (this) {
        Oppgave.Tilstand.KortSøknadFerdigbehandlet,
        Oppgave.Tilstand.SpleisFerdigbehandlet -> OppdateringstypeDTO.Ferdigbehandlet
        Oppgave.Tilstand.LagOppgave -> OppdateringstypeDTO.Opprett
        Oppgave.Tilstand.LagOppgaveForSpeilsaksbehandlere -> OppdateringstypeDTO.OpprettSpeilRelatert
        Oppgave.Tilstand.KortInntektsmeldingFerdigbehandlet,
        Oppgave.Tilstand.SpleisLest -> OppdateringstypeDTO.Utsett
        Oppgave.Tilstand.DokumentOppdaget -> error("skal ikke legge melding på topic om at dokument er oppdaget")
    }

    private fun Oppgave.timeout(): LocalDateTime? = when (tilstand) {
        Oppgave.Tilstand.KortInntektsmeldingFerdigbehandlet,
        Oppgave.Tilstand.SpleisLest -> LocalDateTime.now().plusDays(finnTimeout())
        else -> null
    }

    private fun Oppgave.finnTimeout() =
        when {
            dokumentType == Søknad -> 110
            oppgaveDAO.harUtbetalingTilSøker(dokumentId) -> 1
            else -> 40
        }.toLong()


    private fun Oppgave.Tilstand.toEventName(): String = when (this) {
        Oppgave.Tilstand.SpleisFerdigbehandlet -> "oppgavestyring_ferdigbehandlet"
        Oppgave.Tilstand.LagOppgave -> "oppgavestyring_opprett"
        Oppgave.Tilstand.LagOppgaveForSpeilsaksbehandlere -> "oppgavestyring_opprett_speilrelatert"
        Oppgave.Tilstand.KortInntektsmeldingFerdigbehandlet,
        Oppgave.Tilstand.SpleisLest -> "oppgavestyring_utsatt"
        Oppgave.Tilstand.KortSøknadFerdigbehandlet -> "oppgavestyring_kort_periode"
        Oppgave.Tilstand.DokumentOppdaget -> error("skal ikke legge melding på topic om at dokument er oppdaget")
    }
}
