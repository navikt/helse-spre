package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.spre.saksbehandlingsstatistikk.Avsender.SPLEIS
import no.nav.helse.spre.saksbehandlingsstatistikk.YtelseType.SYKEPENGER
import java.time.LocalDateTime
import java.util.*

data class StatistikkEvent(
    val aktorId: String,
    val behandlingId: UUID,
    val tekniskTid: LocalDateTime = LocalDateTime.now(),
    val funksjonellTid: LocalDateTime,
    val mottattDato: String,
    val registrertDato: String,
    val behandlingType: BehandlingType  = BehandlingType.SØKNAD,
    val behandlingStatus: BehandlingStatus = BehandlingStatus.AVSLUTTET,
    val ytelseType: YtelseType = SYKEPENGER,
    val utenlandstilsnitt: Utenlandstilsnitt = Utenlandstilsnitt.NEI,
    val totrinnsbehandling: Totrinnsbehandling = Totrinnsbehandling.NEI,
    val ansvarligEnhetKode: AnsvarligEnhetKode = AnsvarligEnhetKode.FIREFIREÅTTEÅTTE,
    val ansvarligEnhetType: AnsvarligEnhetType = AnsvarligEnhetType.NORG,
    val versjon: String = System.getenv()["GIT_SHA"].toString(),
    val avsender: Avsender = SPLEIS,
    val saksbehandlerIdent: String,
) {
    companion object {
        fun toStatistikkEvent(søknad: Søknad, vedtakFattetData: VedtakFattetData) = StatistikkEvent(
            aktorId = vedtakFattetData.aktørId,
            behandlingId = søknad.søknadDokumentId,
            mottattDato = søknad.rapportert.toString(),
            registrertDato = søknad.registrertDato.toString(),
            saksbehandlerIdent = søknad.saksbehandlerIdent!!,
            funksjonellTid = søknad.vedtakFattet!!
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
