package no.nav.soknad.innsending

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = false
)
