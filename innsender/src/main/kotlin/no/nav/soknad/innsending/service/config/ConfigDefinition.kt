package no.nav.soknad.innsending.service.config

import no.nav.soknad.innsending.model.ConfigTypeDto
import no.nav.soknad.innsending.model.ConfigValueDto

enum class ConfigDefinition(
	val key: String,
	val label: String,
	val description: String,
	val type: ConfigTypeDto,
	val validators: List<ConfigValidator> = emptyList(),
) {
	NOLOGIN_MAIN_SWITCH(
		"nologin_main_switch",
		label = "Uinnlogget digital innsending",
		"Bryter for å skru uinnlogget digital innsending av/på",
		ConfigTypeDto.SWITCH,
		listOf(SwitchValidator(setOf("on", "off")))
	),
	NOLOGIN_MAX_FILE_UPLOADS_COUNT(
		"nologin_max_file_uploads_count",
		label = "Maks antall uinnloggede filopplastinger",
		"Maks antall filopplastinger på uinnloggede søknader som forventes i et gitt tidsintervall",
		ConfigTypeDto.INTEGER,
		listOf(MinIntegerValidator(1))
	),
	NOLOGIN_MAX_FILE_UPLOADS_WINDOW_MINUTES(
		"nologin_max_file_uploads_window_minutes",
		label = "Maks filopplastingsvindu i minutter",
		"Størrelse på vinduet hvor det kan være maks antall filopplastinger på uinnloggede søknader",
		ConfigTypeDto.INTEGER,
		listOf(MinIntegerValidator(1))
	),
	NOLOGIN_MAX_SUBMISSIONS_COUNT(
		"nologin_max_submissions_count",
		label = "Maks antall uinnloggede innsendinger",
		"Maks antall uinnloggede innsendinger som forventes i et gitt tidsintervall",
		ConfigTypeDto.INTEGER,
		listOf(MinIntegerValidator(1))
	),
	NOLOGIN_MAX_SUBMISSIONS_WINDOW_MINUTES(
		"nologin_max_submissions_window_minutes",
		label = "Maks innsendingsvindu i minutter",
		"Størrelse på vinduet hvor det kan være maks antall uinnloggede innsendinger",
		ConfigTypeDto.INTEGER,
		listOf(MinIntegerValidator(1))
	);

	companion object {
		fun fromKey(key: String): ConfigDefinition = entries.firstOrNull { it.key == key }
			?: throw IllegalArgumentException("Unknown config key: $key")
	}
}

interface ConfigValidator {
	fun isValid(value: String?): Boolean
}

class MinIntegerValidator(private val minValue: Int) : ConfigValidator {
	override fun isValid(value: String?): Boolean {
		return value?.toIntOrNull()?.let { it >= minValue } ?: false
	}
}

class SwitchValidator(private val allowedValues: Set<String>) : ConfigValidator {
	override fun isValid(value: String?): Boolean {
		return value != null && value in allowedValues
	}
}

fun ConfigDefinition.validate(value: String?): Boolean {
	return this.validators.all { it.isValid(value) }
}
