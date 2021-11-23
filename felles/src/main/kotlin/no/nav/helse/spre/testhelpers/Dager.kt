package no.nav.helse.spre.testhelpers

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.streams.toList

fun utbetalingsdager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.UTBETALINGSDAG)
fun arbeidsdager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.ARBEIDSDAG)
fun fridager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.FRIDAG)
fun feriedager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.FERIEDAG)
fun permisjonsdager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.PERMISJONSDAG)
fun ukjentDager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.UKJENTDAG)
fun foreldetDager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.FORELDETDAG)
fun avvistDager(fom: LocalDate, tom: LocalDate = fom, begrunnelser: List<String>) = dagerFraTil(fom, tom, Dagtype.AVVISTDAG, begrunnelser)

private fun dagerFraTil(fom: LocalDate, tom: LocalDate, type: Dagtype, begrunnelser: List<String>? = null): List<Dag> {
    return fom.datesUntil(tom.plusDays(1)).map {
        Dag(it, type, begrunnelser)
    }.toList()
}

class Dag(val dato: LocalDate, val type: Dagtype, private val begrunnelser: List<String>?) {
    override fun toString(): String {
        return """{
                   "dato": "$dato",
                   "type": "${if (dato.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) type.helgenavn else type.vanligNavn}"
                   ${if(begrunnelser != null) {",\"begrunnelser\": ${begrunnelser.map { "\"$it\"" }}"} else {""}}
               }"""
    }

    companion object {
        fun List<Dag>.toJson(): String {
            return """
                   [
                    ${this.joinToString()}
                   ] 
                """
        }
    }
}

enum class Dagtype(val vanligNavn: String, val helgenavn: String = vanligNavn) {
    UTBETALINGSDAG("NavDag", "NavHelgeDag"),
    ARBEIDSDAG("Arbeidsdag"),
    FRIDAG("Fridag"),
    FORELDETDAG("ForeldetDag"),
    UKJENTDAG("UkjentDag"),
    AVVISTDAG("AvvistDag"),
    FERIEDAG("Feriedag"),
    PERMISJONSDAG("Permisjonsdag");

    companion object {
        fun from(serialisertNavn: String): Dagtype {
            if (serialisertNavn in listOf("NavDag", "NavHelgeDag")) return UTBETALINGSDAG
            return enumValueOf<Dagtype>(serialisertNavn.toUpperCase())
        }
    }
}
