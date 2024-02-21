package no.nav.helse.nom

import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.styringsinfo.sikkerLogg
import java.time.LocalDate

/**
 * gjør oss i stand til å Spoute test-meldinger som ender opp med å logge
 * hva NOM gir oss i response
 */
internal class MidlertidigNOMRiver (
    rapidsConnection: RapidsConnection,
    private val nomClient: Nom
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "test_nom")
                it.requireKey("@id", "testIdent")
            }
        }.register(this)
    }
    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val ident = packet["testIdent"].asText()
        try {
            val enhet = nomClient.hentEnhet(ident, LocalDate.now(), packet["@id"].asText())
            sikkerLogg.info("Fant enhet $enhet for ident $ident")
        } catch (e:Exception) {
            sikkerLogg.info("Fikk feil ${e.javaClass.simpleName} : ${e.message} for ident $ident")
        }
    }

}