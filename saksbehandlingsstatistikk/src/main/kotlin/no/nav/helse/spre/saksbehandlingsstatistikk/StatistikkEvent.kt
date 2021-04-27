package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.util.*
import no.nav.helse.spre.saksbehandlingsstatistikk.Avsender.SPLEIS
import no.nav.helse.spre.saksbehandlingsstatistikk.YtelseType.SYKEPENGER

data class StatistikkEvent(
    val aktorId: String,
    val behandlingId: UUID?, // SøknadDokumentId

    val tekniskTid: LocalDateTime = LocalDateTime.now(),
    val funksjonellTid: LocalDateTime, // Tidspunkt for avslag eller fatting av vedtak eller tilsvarende
    val mottattDato: String?,
    val registrertDato: String?, // Tidspunktet Spleis ble klar over søknaden

    val behandlingType: BehandlingType?,
    val behandlingStatus: BehandlingStatus,

    val ytelseType: YtelseType = SYKEPENGER,
    val utenlandstilsnitt: Utenlandstilsnitt = Utenlandstilsnitt.NEI,
    val totrinnsbehandling: Totrinnsbehandling = Totrinnsbehandling.NEI,
    val ansvarligEnhetKode: String = "4488",
    val ansvarligEnhetType: String = "NORG",

    val versjon: String = System.getenv()["GIT_SHA"].toString(),
    val avsender: Avsender = SPLEIS,
    val saksbehandlerIdent: String?, // NAV-ident
)

enum class Avsender {
    SPLEIS
}

enum class Totrinnsbehandling {
    NEI
}

enum class Utenlandstilsnitt {
    NEI
}

enum class BehandlingStatus {
    REGISTRERT,
    AVSLUTTET
}

enum class BehandlingType {
    SØKNAD,
    REVURDERING
}

enum class YtelseType {
    SYKEPENGER
}
