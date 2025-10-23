package no.nav.soknad.innsending.endtoend

import no.nav.soknad.innsending.repository.ConfigRepository
import no.nav.soknad.innsending.service.config.ConfigDefinition
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("endtoend")
class EndtoendConfig(
	configRepository: ConfigRepository
) {

	init {
		// enable nologin for endtoend tests
		val nologinMainSwitch = configRepository.findByKey(ConfigDefinition.NOLOGIN_MAIN_SWITCH.key)
			?: throw IllegalStateException("Config ${ConfigDefinition.NOLOGIN_MAIN_SWITCH.key} not found in database")
		configRepository.save(nologinMainSwitch.copy(value = "on"))
	}

}
