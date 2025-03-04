package no.nav.soknad.innsending.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "publisherconfig")
data class PublisherConfig (
	val cluster: String,
	val team: String,
	val application: String,
)
