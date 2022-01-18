package no.nav.soknad.innsending

import org.springframework.context.annotation.Configuration

@Configuration
data class ApplicationState(
	var alive: Boolean = true,
	var ready: Boolean = false
)
