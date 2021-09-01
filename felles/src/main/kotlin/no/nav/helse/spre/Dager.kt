package no.nav.helse.spre

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.streams.toList

fun utbetalingsdager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.UTBETALINGSDAG)
fun arbeidsdager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.ARBEIDSDAG)
fun fridager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.FRIDAG)
fun ukjentDager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.UKJENTDAG)
fun foreldetDager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.FORELDETDAG)
fun avvistDager(fom: LocalDate, tom: LocalDate = fom) = dagerFraTil(fom, tom, Dagtype.AVVISTDAG)

private fun dagerFraTil(fom: LocalDate, tom: LocalDate, type: Dagtype): List<Dag> {
    return fom.datesUntil(tom.plusDays(1)).map {
        Dag(it, type)
    }.toList()
}

class Dag(val dato: LocalDate, private val type: Dagtype) {
    override fun toString(): String {
        return """{
                   "dato": "$dato",
                   "type": "${if (dato.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) type.helgenavn else type.vanligNavn}"
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
    AVVISTDAG("AvvistDag");

    companion object {
        fun from(serialisertNavn: String): Dagtype {
            if (serialisertNavn in listOf("NavDag", "NavHelgeDag")) return UTBETALINGSDAG
            return enumValueOf<Dagtype>(serialisertNavn.toUpperCase())
        }
    }
}