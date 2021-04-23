package no.nav.helse.spre.saksbehandlingsstatistikk

import java.util.*

data class StatistikkEvent(
    val aktorId: String,
    val behandlingId: UUID?,
//    val funksjonellTid: LocalDateTime, //Tidspunkt for avslag eller fatting av vedtak eller tilsvarende
//    val tekniskTid: LocalDateTime, //?
//    val mottattDato: LocalDate, //Tidspunktet søknaden ankom NAV
//    val registrertDato: LocalDate, //Tidspunktet Spleis ble klar over søknaden
//    val ytelseType: YtelseType, //?, trenger vi å sende denne hvis den er hardkodet?
//    val sakId: UUID, //?
//    val saksnummer: Int, //?
    val behandlingType: BehandlingType?,
    val behandlingTypeBeskrivelse: String?,
    val behandlingStatus: BehandlingStatus,
//    val resterendeDager: Int,
)

enum class BehandlingStatus {
    REGISTRERT
}

enum class BehandlingType(val beskrivelse: String) {
    SØKNAD("Behandling av søknad om sykepenger"),
    REVURDERING("Ny behandling av søknad som følge av nye opplysninger")
}

enum class YtelseType {

}
