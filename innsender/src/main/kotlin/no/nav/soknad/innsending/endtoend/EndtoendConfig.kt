package no.nav.soknad.innsending.endtoend

import no.nav.soknad.innsending.service.config.ConfigDefinition
import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.config.utils.dto.isEqualTo
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("endtoend | loadtests")
class EndtoendConfig(
	configService: ConfigService,
) {

	init {
		// enable nologin for endtoend and loadtests
		val config = configService.getConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH)
		if (!config.isEqualTo("on")) {
			configService.setConfig(ConfigDefinition.NOLOGIN_MAIN_SWITCH, "on", "SYSTEM")
		}
	}

}
