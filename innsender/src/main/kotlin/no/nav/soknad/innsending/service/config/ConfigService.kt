package no.nav.soknad.innsending.service.config

import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.ConfigValueDto
import no.nav.soknad.innsending.repository.ConfigRepository
import no.nav.soknad.innsending.service.config.utils.toDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ConfigService(
	private val configRepository: ConfigRepository,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun getConfig(): List<ConfigValueDto> {
		return configRepository.findAllByOrderByKey().map { it.toDto() }
	}

	fun getConfig(config: ConfigDefinition): ConfigValueDto {
		return configRepository.findByKey(config.key)?.toDto()
			?: throw ResourceNotFoundException("Config with key ${config.key} not found")
	}

	fun setConfig(config: ConfigDefinition, value: String?, userId: String): ConfigValueDto {
		val existing = configRepository.findByKey(config.key)
			?: throw ResourceNotFoundException("Config with key ${config.key} not found")
		val valid = config.validate(value)
		if (!valid) {
			throw IllegalArgumentException("Invalid value for config ${config.key}: $value")
		}
		logger.info("Updating config with key ${config.key} (user $userId), new value: $value")
		val updated = existing.copy(
			value = value,
			modifiedBy = userId,
			modifiedAt = LocalDateTime.now(),
		)
		return configRepository.save(updated).toDto()
	}

}
