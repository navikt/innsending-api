package no.nav.soknad.innsending.location

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.model.EnvQualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("prod")
class UrlHandlerImpl(
	private val urlConfig: RestConfig
) : UrlHandler {
	override fun getSendInnUrl(@Suppress("UNUSED_PARAMETER") envQualifier: EnvQualifier?): String =
		urlConfig.sendinn.urls["default"].orEmpty()

	override fun getFyllutUrl(@Suppress("UNUSED_PARAMETER") envQualifier: EnvQualifier?): String =
		urlConfig.fyllUtUrl
}
