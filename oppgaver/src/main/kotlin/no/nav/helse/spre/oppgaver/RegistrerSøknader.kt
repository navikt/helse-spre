package no.nav.helse.spre.oppgaver

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import java.time.LocalDate
import java.util.UUID

internal typealias Periode = Pair<LocalDate, LocalDate>

private val JsonMessage.periode: Periode
    get() = get("fom").asLocalDate() to get("tom").asLocalDate()

class RegistrerSøknader(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    val søknadsPerioderDAO: SøknadsperioderDAO
) : River.PacketListener{

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("@id") }
            validate { it.requireKey("id") }
            validate { it.requireKey("fom", "tom") }
            validate { it.requireValue("@event_name", "sendt_søknad_nav") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelseId = UUID.fromString(packet["@id"].asText())
        val dokumentId = UUID.fromString(packet["id"].asText())

        oppgaveDAO.opprettOppgaveHvisNy(hendelseId, dokumentId, DokumentType.Søknad)
        søknadsPerioderDAO.lagre(hendelseId, packet.periode)
        log.info("Søknad oppdaget: {} og {}", keyValue("hendelseId", hendelseId), keyValue("dokumentId", dokumentId))
    }
}
