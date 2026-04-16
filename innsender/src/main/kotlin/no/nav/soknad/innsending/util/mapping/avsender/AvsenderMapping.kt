package no.nav.soknad.innsending.util.mapping.avsender

import no.nav.soknad.arkivering.soknadsmottaker.model.AvsenderDto as ArkiveringAvsenderDto
import no.nav.soknad.innsending.model.AvsenderDto

fun AvsenderDto.toArkiveringAvsenderDto(): ArkiveringAvsenderDto {
	val idType = translateAvsenderIdType(this)
	return ArkiveringAvsenderDto(
		id = this.id,
		idType = idType,
		navn = this.navn,
	)
}

private fun translateAvsenderIdType(avsenderDto: AvsenderDto): ArkiveringAvsenderDto.IdType? {
	val explicitIdType = avsenderDto.idType
	if (explicitIdType != null) {
		return ArkiveringAvsenderDto.IdType.valueOf(explicitIdType.value)
	}

	return if (avsenderDto.id.isNullOrEmpty()) null else ArkiveringAvsenderDto.IdType.FNR
}
