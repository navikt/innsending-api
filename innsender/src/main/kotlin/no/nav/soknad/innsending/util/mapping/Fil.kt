package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.arkivering.soknadsmottaker.model.Variant
import no.nav.soknad.arkivering.soknadsmottaker.model.Varianter
import no.nav.soknad.innsending.model.FilDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.repository.domain.models.FilDbData
import no.nav.soknad.innsending.model.Mimetype
import java.time.LocalDateTime

fun mapTilFilDb(filDto: FilDto) = FilDbData(
	filDto.id,
	filDto.vedleggsid,
	filDto.filnavn ?: "",
	mapTilDbMimetype(filDto.mimetype) ?: "application/pdf",
	if (filDto.data == null) null else filDto.data?.size,
	filDto.data,
	mapTilLocalDateTime(filDto.opprettetdato) ?: LocalDateTime.now(),
	antallsider = filDto.antallsider
)

fun lagFilDto(filDbData: FilDbData, medFil: Boolean = true) = FilDto(
	filDbData.vedleggsid, filDbData.id,
	filDbData.filnavn, mapTilMimetype(filDbData.mimetype), filDbData.storrelse, filDbData.antallsider,
	if (medFil) filDbData.data else null, mapTilOffsetDateTime(filDbData.opprettetdato)
)

fun filExtention(dokumentDto: VedleggDto): String =
	when (dokumentDto.mimetype) {
		Mimetype.imageSlashPng -> "png"
		Mimetype.imageSlashJpeg -> "jpeg"
		Mimetype.applicationSlashJson -> "json"
		Mimetype.applicationSlashXml -> "xml"
		Mimetype.applicationSlashPdf -> if (dokumentDto.erPdfa) "pdfa" else "pdf"
		else -> ""
	}

fun translate(dokumentDto: VedleggDto): Varianter {
	return Varianter(
		id =	dokumentDto.uuid!!,
		mediaType = dokumentDto.mimetype?.value ?: "application/pdf",
		filnavn = (dokumentDto.vedleggsnr ?: "N6") + "." + filExtention(dokumentDto),
		filtype = filExtention(dokumentDto)
	)
}

fun translate(dokumentDto: VedleggDto, new: Boolean): Variant {
	return Variant(
		mediaType = dokumentDto.mimetype?.value ?: "application/pdf",
		filnavn = dokumentDto.label,
		filtype = filExtention(dokumentDto),
		uuid = dokumentDto.uuid!!,
		variantFormat = translateVariantFormat(dokumentDto.mimetype),
	)
}

fun translateVariantFormat(mimeType: Mimetype?): String  =
	when (mimeType) {
		Mimetype.applicationSlashJson -> "ORIGINAL"
		Mimetype.applicationSlashXml -> "ORIGINAL"
		else -> "ARKIV"
	}
