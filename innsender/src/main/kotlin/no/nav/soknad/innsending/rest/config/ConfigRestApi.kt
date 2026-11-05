package no.nav.soknad.innsending.rest.config

import no.nav.soknad.innsending.api.ConfigApi
import no.nav.soknad.innsending.model.ConfigValueDto
import no.nav.soknad.innsending.model.SetConfigRequest
import no.nav.soknad.innsending.security.RequiresAzure
import no.nav.soknad.innsending.security.RequiresAzureAdmin
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@RequiresAzure
class ConfigRestApi(
	private val configService: ConfigService,
	private val subjectHandler: SubjectHandlerInterface,
) : ConfigApi {

	@RequiresAzureAdmin
	override fun getConfig(key: String): ResponseEntity<ConfigValueDto> {
		val config = ConfigDefinition.fromKey(key)
		return ResponseEntity.ok(configService.getConfig(config))
	}

	@RequiresAzureAdmin
	override fun setConfig(key: String, setConfigRequest: SetConfigRequest): ResponseEntity<ConfigValueDto> {
		val userId = subjectHandler.getNavIdent()
		val config = ConfigDefinition.fromKey(key)
		return configService.setConfig(config, setConfigRequest.value, userId)
			.let { ResponseEntity.ok(it) }
	}

	@RequiresAzureAdmin
	override fun getAllConfig(): ResponseEntity<List<ConfigValueDto>> {
		return ResponseEntity.ok(configService.getConfig())
	}

}
