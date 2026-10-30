package no.nav.soknad.innsending.rest.config

import no.nav.security.token.support.core.api.Unprotected
import no.nav.soknad.innsending.api.ConfigApi
import no.nav.soknad.innsending.model.ConfigValueDto
import no.nav.soknad.innsending.model.SetConfigRequest
import no.nav.soknad.innsending.security.KreverAzure
import no.nav.soknad.innsending.security.KreverAzureAdmin
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@KreverAzure
// Azure-token valideres av Texas og tilgangen av @PreAuthorize (#461, steg 3i)
@Unprotected
class ConfigRestApi(
	private val configService: ConfigService,
	private val subjectHandler: SubjectHandlerInterface,
) : ConfigApi {

	@KreverAzureAdmin
	override fun getConfig(key: String): ResponseEntity<ConfigValueDto> {
		val config = ConfigDefinition.fromKey(key)
		return ResponseEntity.ok(configService.getConfig(config))
	}

	@KreverAzureAdmin
	override fun setConfig(key: String, setConfigRequest: SetConfigRequest): ResponseEntity<ConfigValueDto> {
		val userId = subjectHandler.getNavIdent()
		val config = ConfigDefinition.fromKey(key)
		return configService.setConfig(config, setConfigRequest.value, userId)
			.let { ResponseEntity.ok(it) }
	}

	@KreverAzureAdmin
	override fun getAllConfig(): ResponseEntity<List<ConfigValueDto>> {
		return ResponseEntity.ok(configService.getConfig())
	}

}
