package no.nav.soknad.innsending.config

import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties("restconfig")
class RestConfig {
	lateinit var version: String
	lateinit var username: String
	lateinit var password: String
	lateinit var sharedUsername: String
	lateinit var sharedPassword: String
	var maxFileSize by Delegates.notNull<Int>()
	lateinit var sanityHost: String
	lateinit var sanityEndpoint: String
	lateinit var filestorageHost: String
	lateinit var filestorageEndpoint: String
	lateinit var filestorageParameters: String
	lateinit var soknadsMottakerHost: String
	lateinit var soknadsMottakerEndpoint: String

}
