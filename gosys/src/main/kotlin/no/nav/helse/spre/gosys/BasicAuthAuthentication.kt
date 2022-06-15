package no.nav.helse.spre.gosys

import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic

internal fun Application.basicAuthentication(
    adminSecret: String
) {
    authentication {
        basic(name = "admin") {
            this.validate {
                if (it.password == adminSecret) {
                    UserIdPrincipal("admin")
                } else null
            }
        }
    }
}
