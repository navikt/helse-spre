package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.spre.saksbehandlingsstatistikk.Avsender.SPLEIS
import no.nav.helse.spre.saksbehandlingsstatistikk.YtelseType.SYKEPENGER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

data class StatistikkEvent(
    val aktorId: String,
    val behandlingId: UUID,
    val funksjonellTid: LocalDateTime,
    val mottattDato: String,
    val registrertDato: String,
    val saksbehandlerIdent: String,
    val tekniskTid: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    val automatiskbehandling: Boolean? = null,
    val resultat: Resultat,
) {
    val avsender: Avsender = SPLEIS
    val ansvarligEnhetType: AnsvarligEnhetType = AnsvarligEnhetType.NORG
    val ansvarligEnhetKode: String = AnsvarligEnhetKode.FIREFIREÅTTEÅTTE.toString()
    val totrinnsbehandling: Totrinnsbehandling = Totrinnsbehandling.NEI
    val utenlandstilsnitt: Utenlandstilsnitt = Utenlandstilsnitt.NEI
    val ytelseType: YtelseType = SYKEPENGER
    val behandlingStatus: BehandlingStatus = BehandlingStatus.AVSLUTTET
    val behandlingType: BehandlingType = BehandlingType.SØKNAD
    val versjon: String = global.versjon

    companion object {
        fun statistikkEvent(
            søknad: Søknad,
            aktørId: String,
        ) = StatistikkEvent(
            aktorId = aktørId,
            behandlingId = søknad.søknadDokumentId,
            mottattDato = søknad.rapportert.toString(),
            registrertDato = søknad.registrertDato.toString(),
            saksbehandlerIdent = søknad.saksbehandlerIdent ?: "ukjent".also {
                log.info(
                    "manglet saksbehandlerIdent for søknadDokumentId {}",
                    søknad.søknadDokumentId
                )
            },
            funksjonellTid = søknad.vedtakFattet!!,
            automatiskbehandling = søknad.automatiskBehandling,
            resultat = Resultat.valueOf(søknad.resultat!!),
        )
    }
}

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

enum class AnsvarligEnhetKode(private val kode: Int) {
    FIREFIREÅTTEÅTTE(4488);

    override fun toString() = kode.toString()
}

enum class AnsvarligEnhetType {
    NORG
}

enum class Resultat {
    INNVILGET,
    AVVIST
}

