package no.nav.soknad.innsending.service.config.utils.dto

import no.nav.soknad.innsending.model.ConfigValueDto

fun ConfigValueDto?.isEqualTo(value: String?): Boolean {
	return this?.value == value
}

fun ConfigValueDto.getLongValue(): Long {
	return this.value?.toLong() ?: throw IllegalStateException("Config value (${this.key}) is not a valid Long: $value")
}

fun ConfigValueDto?.verifyValue(
	value: String?,
	exceptionType: (() -> Throwable)? = null
) {
	if (this == null) {
		throw exceptionType?.invoke() ?: IllegalStateException("Config not found")
	}
	if (!this.isEqualTo(value)) {
		throw exceptionType?.invoke() ?: IllegalStateException("[${this.key}] Config value does not match expected value")
	}
}
