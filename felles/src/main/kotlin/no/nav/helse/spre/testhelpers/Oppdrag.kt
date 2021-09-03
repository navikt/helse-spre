package no.nav.helse.spre.testhelpers

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.streams.toList

/* Typet objekt for å lage en json-representasjon av et oppdrag, til bruk i test av
* eventet utbetaling_utbetalt (og potensielt andre eventer)
* Lager et oppdrag basert på en tidslinje
* */

class Oppdrag(
    private val tidslinje: List<Dag>,
    private val dagsats: Int = 1431,
    private val grad: Double = 100.0,
    private val lønn: Int = 2193,
    private val mottaker: String = "123456789",
    private val fagområde: String = "SPREF",
    private val fagsystemId: String = "fagsystemId",
    private val tidsstempel: LocalDateTime = tidslinje.last().dato.atStartOfDay()
) {

    fun toJson(): String {
        val linjer = Linjer(tidslinje, grad, lønn, dagsats)
        val stønadsdager = tidslinje.count {
            it.type == Dagtype.UTBETALINGSDAG && it.dato.dayOfWeek !in listOf(
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
            )
        }
        return """ { 
             "linjer": ${linjer.toJson()},
             "stønadsdager": $stønadsdager,
             "fagområde": "$fagområde",
             "nettoBeløp": ${stønadsdager * dagsats},
             "mottaker": "$mottaker",
             "fagsystemId": "$fagsystemId",
             "tidsstempel": "$tidsstempel"
            }    
        """.trimIndent()

    }


    // I første omgang kun linjer uten opphold
    class Linjer(
        tidslinje: List<Dag>,
        private val grad: Double,
        private val lønn: Int,
        private val dagsats: Int
    ) {

        private val påbegynt = mutableListOf<Dag>()
        private val linjer = mutableListOf<Pair<Dag, Dag>>()

        init {
            tidslinje.forEach { dag ->
                if (dag.type == Dagtype.UTBETALINGSDAG) {
                    påbegynt.add(dag)
                } else {
                    if (påbegynt.isNotEmpty()) {
                        linjer.add(påbegynt.first() to påbegynt.last())
                        påbegynt.clear()
                    }
                }
            }
            if (påbegynt.isNotEmpty()) {
                linjer.add(påbegynt.first() to påbegynt.last())
                påbegynt.clear()
            }
        }

        fun toJson(): String {
            return """
                ${
                linjer.map { (fom, tom) ->
                    val stønadsdager = fom.dato.datesUntil(tom.dato.plusDays(1))
                        .toList().count { it.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }

                    """{
                            "fom": "${fom.dato}",
                            "tom": "${tom.dato}",
                             "dagsats": $dagsats,
                             "lønn": $lønn,
                             "grad": $grad,
                             "totalbeløp": ${stønadsdager * dagsats},
                             "stønadsdager": $stønadsdager
                            }"""
                }
            }
            """.trimIndent()
        }
    }
}