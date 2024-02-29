package no.nav.helse.spre.styringsinfo.teamsak.behandling

internal class Versjon private constructor(
    private val major: Int,
    private val minor: Int,
    private val patch: Int
): Comparable<Versjon> {
    private val majorUpdate get() = Versjon(major + 1, 0, 0)
    private val minorUpdate get() = Versjon(major, minor + 1, 0)
    private val patchUpdate get() = Versjon(major, minor, patch + 1)

    private val tall = "$major$minor$patch".toInt()
    override fun compareTo(other: Versjon) = this.tall.compareTo(other.tall)

    override fun equals(other: Any?) = other is Versjon && this.toString() == other.toString()
    override fun hashCode() = toString().hashCode()
    override fun toString() = "$major.$minor.$patch"


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

        private fun nesteVersjon(forrigeVersjon: Versjon, forrigeFelter: Set<String>, felter: Set<String>): Pair<Set<String>, Versjon> {
            check(forrigeFelter != felter) { "Trenger ikke lage en ny versjon. $forrigeVersjon dekker allerede feltene $felter" }
            if ((forrigeFelter - felter).isNotEmpty()) return felter to forrigeVersjon.majorUpdate
            return felter to forrigeVersjon.minorUpdate
        }
        internal fun interface Versjonsutleder {
            fun nyVersjon(forrigeFelter: Set<String>, forrigeVersjon: Versjon): Pair<Set<String>, Versjon>
        }
        internal fun Patch(@Suppress("UNUSED_PARAMETER") beskrivelse: String) = Versjonsutleder{ forrigeFelter, forrigeVersjon -> forrigeFelter to forrigeVersjon.patchUpdate }
        internal fun Minor(@Suppress("UNUSED_PARAMETER") beskrivelse: String) = Versjonsutleder{ forrigeFelter, forrigeVersjon -> forrigeFelter to forrigeVersjon.minorUpdate }
        internal fun Major(@Suppress("UNUSED_PARAMETER") beskrivelse: String) = Versjonsutleder{ forrigeFelter, forrigeVersjon -> forrigeFelter to forrigeVersjon.majorUpdate }
        internal fun LeggTil(vararg felter: String) = Versjonsutleder { forrigeFelter, forrigeVersjon -> nesteVersjon(forrigeVersjon, forrigeFelter, forrigeFelter + felter) }
        internal fun Fjern(vararg felter: String) = Versjonsutleder { forrigeFelter, forrigeVersjon -> nesteVersjon(forrigeVersjon, forrigeFelter, forrigeFelter - felter.toSet()) }
        internal fun LeggTilOgFjern(leggTil: Set<String>, fjern: Set<String>) = Versjonsutleder { forrigeFelter, forrigeVersjon -> nesteVersjon(forrigeVersjon, forrigeFelter, forrigeFelter + leggTil - fjern) }
        private object InitiellVersjon: Versjonsutleder {
            override fun nyVersjon(forrigeFelter: Set<String>, forrigeVersjon: Versjon) =
                setOf("aktørId", "mottattTid", "registrertTid", "behandlingstatus", "behandlingtype", "behandlingskilde", "behandlingsmetode", "relatertBehandlingId", "behandlingsresultat") to of("0.0.1")
        }

        private val versjoner = listOf(
            InitiellVersjon,
            Patch("Endret enum-verdier til CAPS LOCK (842144a) og korrigerte tidligere rader (bdebadb), derfor bare Patch update"),
            Patch("Presisjon på tidsstempler truncates til 6 desimaler i databasen (2aa8b95 & 1b17827)"),
            Patch("Presisjon på tidsstempler truncates til 6 desimaler i databasen take 2️⃣ (909324f)"),
            LeggTil("saksbehandlerEnhet", "beslutterEnhet"),
            LeggTil("periodetype")
        ).genererVersjoner

        internal val List<Versjonsutleder>.genererVersjoner: Map<Set<String>, Versjon> get() {
            val (initielleFelter, initiellVersjon) = firstOrNull()?.nyVersjon(emptySet(), of("0.0.0")) ?: return emptyMap()
            return drop(1).fold(listOf(initielleFelter to initiellVersjon)) { versjoner, versjonsutleder ->
                val (forrigeFelter, forrigeVersjon) = versjoner.last()
                versjoner + versjonsutleder.nyVersjon(forrigeFelter, forrigeVersjon)
            }.associate { (felter, versjon) -> felter to versjon }
        }
        internal fun of(felter: Set<String>) = versjoner[felter] ?: versjoner.maxBy { it.value }.let { (sisteFelter, sisteVersjon) ->
            val nyeFelter = felter - sisteFelter
            val fjernedeFelter = sisteFelter - felter
            throw IllegalStateException("""
                Finner ingen definert versjon for disse feltene. Differanse i forhold til siste versjon $sisteVersjon:
                    NyeFelter: ${nyeFelter.joinToString()}
                    FjernedeFelter: ${fjernedeFelter.joinToString()}
                    Legge til endringene dine i 'versjoner'-listen da vel :)
            """.trimIndent())
        }
    }
}