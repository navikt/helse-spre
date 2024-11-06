package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.test_support.CleanupStrategy
import com.github.navikt.tbd_libs.test_support.DatabaseContainers

val databaseContainer = DatabaseContainers.container("spre-oppgaver", CleanupStrategy.tables("oppgave_tilstand,timeout"))
