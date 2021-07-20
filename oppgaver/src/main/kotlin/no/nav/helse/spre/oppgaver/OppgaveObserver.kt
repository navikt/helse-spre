package no.nav.helse.spre.oppgaver

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDateTime
import java.util.*

class OppgaveObserver(
    private val oppgaveDAO: OppgaveDAO,
    private val oppgaveProducers: List<OppgaveProducer>,
    private val rapidsConnection: RapidsConnection
) : Oppgave.Observer {

    override fun lagre(oppgave: Oppgave) {
        oppgaveDAO.oppdaterTilstand(oppgave)
    }

    override fun publiser(oppgave: Oppgave) {
        oppgaveProducers.forEach { it.kafkaproducer().send(
            ProducerRecord(
                it.topic(), OppgaveDTO(
                    dokumentType = oppgave.dokumentType.toDTO(),
                    oppdateringstype = oppgave.tilstand.toDTO(),
                    dokumentId = oppgave.dokumentId,
                    timeout = oppgave.tilstand.timeout(),
                )
            )
        ) }

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
        Oppgave.Tilstand.KortInntektsmeldingFerdigbehandlet,
        Oppgave.Tilstand.SpleisLest -> OppdateringstypeDTO.Utsett
        Oppgave.Tilstand.DokumentOppdaget -> error("skal ikke legge melding på topic om at dokument er oppdaget")
    }

    private fun Oppgave.Tilstand.timeout(): LocalDateTime? = when (this) {
        Oppgave.Tilstand.SpleisLest -> LocalDateTime.now().plusDays(110)
        else -> null
    }

    private fun Oppgave.Tilstand.toEventName(): String = when (this) {
        Oppgave.Tilstand.SpleisFerdigbehandlet -> "oppgavestyring_ferdigbehandlet"
        Oppgave.Tilstand.LagOppgave -> "oppgavestyring_opprett"
        Oppgave.Tilstand.KortInntektsmeldingFerdigbehandlet,
        Oppgave.Tilstand.SpleisLest -> "oppgavestyring_utsatt"
        Oppgave.Tilstand.KortSøknadFerdigbehandlet -> "oppgavestyring_kort_periode"
        Oppgave.Tilstand.DokumentOppdaget -> error("skal ikke legge melding på topic om at dokument er oppdaget")
    }
}
