package no.nav.soknad.innsending.config

import no.nav.soknad.innsending.ProfileConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties("restconfig")
class RestConfig(private val profileConfig: ProfileConfig) {
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
	var filesInOneRequestToFilestorage by Delegates.notNull<Int>()
	lateinit var soknadsMottakerHost: String
	lateinit var soknadsMottakerEndpoint: String

}
