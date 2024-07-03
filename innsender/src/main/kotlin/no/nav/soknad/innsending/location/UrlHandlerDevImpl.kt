package no.nav.soknad.innsending.location

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.model.EnvQualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!prod")
class UrlHandlerDevImpl(
	private val urlConfig: RestConfig
) : UrlHandler {
	override fun getSendInnUrl(envQualifier: EnvQualifier?): String {
		val urls = urlConfig.sendinn.urls
		val defaultUrl = urls["default"].orEmpty()
		return when {
			envQualifier != null -> urls[envQualifier.value] ?: defaultUrl
			else -> defaultUrl
		}
	}
}
