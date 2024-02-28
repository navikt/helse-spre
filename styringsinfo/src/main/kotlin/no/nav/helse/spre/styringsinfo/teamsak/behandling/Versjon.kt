package no.nav.helse.spre.styringsinfo.teamsak.behandling
internal class Versjon private constructor(
    private val major: Int,
    private val minor: Int,
    private val patch: Int
): Comparable<Versjon> {
    private val majorUpdate get() = Versjon(major + 1, 0, 0)
    private val minorUpdate get() = Versjon(major, minor + 1, 0)

    private val tall = "$major$minor$patch".toInt()
    override fun compareTo(other: Versjon) = this.tall.compareTo(other.tall)

    override fun equals(other: Any?) = other is Versjon && this.toString() == other.toString()
    override fun hashCode() = toString().hashCode()
    override fun toString() = "$major.$minor.$patch"


    internal companion object {
        private val initiellVersjon = of("0.0.4")
        private val initielleFelter = setOf("aktørId", "mottattTid", "registrertTid", "behandlingstatus", "behandlingtype", "behandlingskilde", "behandlingsmetode", "relatertBehandlingId", "behandlingsresultat")

        private fun nesteVersjon(forrigeVersjon: Versjon, forrigeFelter: Set<String>, felter: Set<String>): Pair<Set<String>, Versjon> {
            check(forrigeFelter != felter) { "Trenger ikke lage en ny versjon. $forrigeVersjon dekker allerede feltene $felter" }
            if ((forrigeFelter - felter).isNotEmpty()) return felter to forrigeVersjon.majorUpdate
            return felter to forrigeVersjon.minorUpdate
        }

        private val versjoner = listOf(
            { forrigeFelter: Set<String>, forrigeVersjon: Versjon -> nesteVersjon(forrigeVersjon, forrigeFelter, forrigeFelter + "saksbehandlerEnhet" + "beslutterEnhet") },
            { forrigeFelter: Set<String>, forrigeVersjon: Versjon -> nesteVersjon(forrigeVersjon, forrigeFelter, forrigeFelter + "periodetype") }
        )

        private val genererteVersjoner = versjoner.fold(listOf(initielleFelter to initiellVersjon)) { versjoner, genererNesteVersjon ->
            val (forrigeFelter, forrigeVersjon) = versjoner.last()
            versjoner + genererNesteVersjon(forrigeFelter, forrigeVersjon)
        }.associate { (felter, versjon) -> felter to versjon}

        internal fun of(felter: Set<String>) = genererteVersjoner[felter] ?: genererteVersjoner.maxBy { it.value }.let { (sisteFelter, sisteVersjon) ->
            val nyeFelter = felter - sisteFelter
            val fjernedeFelter = sisteFelter - felter
            throw IllegalStateException("""
                (-(-(-(-_-)-)-)-) LES HELE MELDINGEN DIN LATSABB! (-(-(-(-_-)-)-)-)
                Finner ingen definert versjon for disse feltene. Differanse i forhold til siste versjon $sisteVersjon:
                    NyeFelter: ${nyeFelter.joinToString()}
                    FjernedeFelter: ${fjernedeFelter.joinToString()}
                    Legges dette til blir det versjon ${nesteVersjon(sisteVersjon, sisteFelter, felter).second}
                    Her behøves et nytt element i en liste!
                    Dette gjøres ved å legge til følgende innslag i `val versjoner`:
                         { forrigeFelter: Set<String>, forrigeVersjon: Versjon -> nesteVersjon(forrigeVersjon, forrigeFelter, forrigeFelter${nyeFelter.eksempelverdi('+')}${fjernedeFelter.eksempelverdi('-')} )}
            """.trimIndent())
        }
        private fun Set<String>.eksempelverdi(prefix: Char) = takeUnless { it.isEmpty() }?.let { "setOf(${it.joinToString { felt -> "\"$felt\"" }})" }?.let { " $prefix $it" } ?: ""

        private val String.ikkeNegativInt get() = toIntOrNull()?.takeIf { it >= 0 }
        internal fun of(versjon: String): Versjon {
            val split = versjon.split(".")
            check(split.size == 3) { "Ugyldig versjon $versjon" }
            val major = checkNotNull(split[0].ikkeNegativInt) { "Ugyldig major $versjon" }
            val minor = checkNotNull(split[1].ikkeNegativInt) { "Ugyldig minor $versjon" }
            val patch = checkNotNull(split[2].ikkeNegativInt) { "Ugyldig patch $versjon" }
            return Versjon(major, minor, patch)
        }
    }
}