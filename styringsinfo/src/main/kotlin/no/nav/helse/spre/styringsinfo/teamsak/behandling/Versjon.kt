package no.nav.helse.spre.styringsinfo.teamsak.behandling

internal class Versjon private constructor(
    private val major: Int,
    private val minor: Int,
    private val patch: Int
) {
    private val majorUpdate get() = Versjon(major + 1, 0, 0)
    private val minorUpdate get() = Versjon(major, minor + 1, 0)

    override fun equals(other: Any?) = other is Versjon && this.toString() == other.toString()
    override fun hashCode() = toString().hashCode()
    override fun toString() = "$major.$minor.$patch"

    private val mineFelter get() = versjoner[this] ?: throw IllegalStateException("Mangler definerte felter for versjon $this")

    internal fun valider(felter: Set<String>) {
        if (felter == mineFelter) return
        val nyeFelter = felter - mineFelter
        if (nyeFelter.isNotEmpty()) throw IllegalStateException("Ettersom feltene $nyeFelter er lagt til burde versjon bumpes fra $this til $minorUpdate")
        val fjernedeFelter = mineFelter - felter
        if (fjernedeFelter.isNotEmpty()) throw IllegalStateException("Ettersom feltene $fjernedeFelter er fjernet burde versjon bumpes fra $this til $majorUpdate")
    }

    internal companion object {
        private val String.ikkeNegativInt get() = toIntOrNull()?.takeIf { it >= 0 }
        internal fun of(versjon: String): Versjon {
            val split = versjon.split(".")
            check(split.size == 3) { "Ugyldig versjon $versjon" }
            val major = checkNotNull(split[0].ikkeNegativInt) { "Ugyldig major $versjon" }
            val minor = checkNotNull(split[1].ikkeNegativInt) { "Ugyldig minor $versjon" }
            val patch = checkNotNull(split[2].ikkeNegativInt) { "Ugyldig patch $versjon" }
            return Versjon(major, minor, patch)
        }

        private val versjoner = mapOf(
            of("0.0.1") to setOf("aktørId", "mottattTid", "registrertTid", "behandlingstatus", "behandlingtype", "behandlingskilde", "behandlingsmetode", "relatertBehandlingId", "behandlingsresultat")
        )
    }
}