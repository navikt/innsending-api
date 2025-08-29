package no.nav.soknad.innsending.rest.config

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.ConfigApi
import no.nav.soknad.innsending.model.ConfigValueDto
import no.nav.soknad.innsending.model.SetConfigRequest
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.util.Constants
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = Constants.AZURE)
class ConfigRestApi(
	private val configService: ConfigService,
	private val subjectHandler: SubjectHandlerInterface,
) : ConfigApi {

	@ProtectedWithClaims(
		issuer = Constants.AZURE,
		claimMap = ["scp=config-admin-access defaultaccess"],
	)
	override fun getConfig(key: String): ResponseEntity<ConfigValueDto> {
		val config = ConfigDefinition.fromKey(key)
		return ResponseEntity.ok(configService.getConfig(config))
	}

	@ProtectedWithClaims(
		issuer = Constants.AZURE,
		claimMap = ["scp=config-admin-access defaultaccess"],
	)
	override fun setConfig(key: String, setConfigRequest: SetConfigRequest): ResponseEntity<ConfigValueDto> {
		val userId = subjectHandler.getNavIdent()
		val config = ConfigDefinition.fromKey(key)
		return configService.setConfig(config, setConfigRequest.value, userId)
			.let { ResponseEntity.ok(it) }
	}

	@ProtectedWithClaims(
		issuer = Constants.AZURE,
		claimMap = ["scp=config-admin-access defaultaccess"],
	)
	override fun getAllConfig(): ResponseEntity<List<ConfigValueDto>> {
		return ResponseEntity.ok(configService.getConfig())
	}

}
