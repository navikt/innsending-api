package no.nav.soknad.innsending.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("gcp.buckets")
class CloudStorageConfig {
	lateinit var fillagerBucketNavn: String
	var host: String? = null
}
