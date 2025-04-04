package no.nav.soknad.innsending.config

import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties("restconfig")
class RestConfig {
	lateinit var clientId: String
	lateinit var clientSecret: String
	var maxFileSize by Delegates.notNull<Int>()
	var maxFileSizeSum by Delegates.notNull<Int>()
	var maxNumberOfPages by Delegates.notNull<Int>()
	lateinit var sanityHost: String
	lateinit var sanityEndpoint: String
	var filesInOneRequestToFilestorage by Delegates.notNull<Int>()
	lateinit var soknadsMottakerHost: String
	lateinit var sendInnUrl: String
	lateinit var sendinn: SendInnConfig
	lateinit var fyllut: FyllutConfig
	lateinit var fyllUtUrl: String
	lateinit var pdlScope: String
	lateinit var pdlUrl: String
	lateinit var safselvbetjeningUrl: String
	lateinit var safUrl: String
	lateinit var azureUrl: String
	lateinit var arenaUrl: String
	lateinit var kodeverkUrl: String
	lateinit var kontoregisterUrl: String

	class SendInnConfig {
		lateinit var urls: Map<String, String>
	}

	class FyllutConfig {
		lateinit var urls: Map<String, String>
	}
}
