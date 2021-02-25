@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class, UUIDSerializer::class)
@file:Suppress("EXPERIMENTAL_API_USAGE")

package no.nav.helse.spre.gosys.io

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class IO {
    @Serializable
    data class Vedtak (
        val aktørId: String,
        val fødselsnummer: String,
        val organisasjonsnummer: String,
        val gjenståendeSykedager: Int,
        val utbetalt: List<Utbetaling>,
        val ikkeUtbetalteDager: List<IkkeUtbetaltDag>,
        val fom: LocalDate,
        val tom: LocalDate,
        val godkjentAv: String,
        val automatiskBehandling: Boolean,
        val sykepengegrunnlag: Double,
        val maksdato: LocalDate,
        val `@id`: UUID,
        val `@opprettet`: LocalDateTime,
    )

    @Serializable
    data class Utbetaling(
        val mottaker: String,
        val fagområde: Fagområde,
        val fagsystemId: String,
        val totalbeløp: Int,
        val utbetalingslinjer: List<Utbetalingslinje>
    )

    @Serializable
    enum class Fagområde {
        SPREF, SP
    }

    @Serializable
    data class Utbetalingslinje(
        val dagsats: Int,
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Double,
        val beløp: Int,
        val sykedager: Int
    )

    @Serializable
    data class IkkeUtbetaltDag(
        val dato: LocalDate,
        val type: String
    )
}
