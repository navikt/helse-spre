package no.nav.helse.spre.gosys
import com.github.navikt.tbd_libs.test_support.CleanupStrategy
import com.github.navikt.tbd_libs.test_support.DatabaseContainers

val databaseContainer = DatabaseContainers.container("spre-gosys", CleanupStrategy.tables("duplikatsjekk,vedtak_fattet,utbetaling,annullering"))

