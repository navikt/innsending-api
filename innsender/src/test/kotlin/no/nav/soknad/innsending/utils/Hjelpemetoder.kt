package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.SoknadServiceTest
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.BEARER
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.OffsetDateTime
import java.util.*

fun lagDokumentSoknad(
	brukerId: String,
	skjemanr: String,
	spraak: String,
	tittel: String,
	tema: String,
	id: Long? = null,
	innsendingsid: String? = null,
	soknadsStatus: SoknadsStatusDto? = SoknadsStatusDto.opprettet,
	vedleggsListe: List<VedleggDto>? = null,
	ettersendingsId: String? = null,
	opprettetDato: OffsetDateTime? = OffsetDateTime.now()
): DokumentSoknadDto {
	val vedleggDtoPdf = lagVedleggDto(skjemanr, tittel, "application/pdf", getBytesFromFile("/litenPdf.pdf"))
	val vedleggDtoJson = lagVedleggDto(skjemanr, tittel, "application/json", getBytesFromFile("/sanity.json"))

	val vedleggDtoList = vedleggsListe ?: listOf(vedleggDtoPdf, vedleggDtoJson)
	return DokumentSoknadDto(
		brukerId, skjemanr, tittel, tema, soknadsStatus!!, opprettetDato ?: OffsetDateTime.now(),
		vedleggDtoList, id, innsendingsid ?: UUID.randomUUID().toString(), ettersendingsId, spraak,
		OffsetDateTime.now(), null
	)
}


fun lagVedlegg(
	id: Long? = null,
	vedleggsnr: String,
	tittel: String,
	opplastingsStatus: OpplastingsStatusDto = OpplastingsStatusDto.ikkeValgt,
	erHoveddokument: Boolean = false,
	vedleggsNavn: String? = null,
	label: String? = null
): VedleggDto =
	lagVedleggDto(
		vedleggsnr, tittel,
		if (opplastingsStatus == OpplastingsStatusDto.lastetOpp)
			(if (vedleggsNavn != null && vedleggsNavn.contains(".pdf")) "application/pdf" else "application/json") else null,
		if (opplastingsStatus == OpplastingsStatusDto.lastetOpp && vedleggsNavn != null) getBytesFromFile(vedleggsNavn) else null,
		id, erHoveddokument, erVariant = false, erPakrevd = false, label = label
	)


fun lagVedleggDto(
	skjemanr: String, tittel: String, mimeType: String?, fil: ByteArray?, id: Long? = null,
	erHoveddokument: Boolean? = true, erVariant: Boolean? = false, erPakrevd: Boolean? = true, label: String? = null
): VedleggDto {
	return VedleggDto(
		tittel, label ?: tittel, erHoveddokument!!, erVariant!!,
		"application/pdf".equals(mimeType, true), erPakrevd!!,
		if (fil != null) OpplastingsStatusDto.lastetOpp else OpplastingsStatusDto.ikkeValgt, OffsetDateTime.now(), id,
		skjemanr, "Beskrivelse", UUID.randomUUID().toString(), Mimetype.applicationSlashPdf, fil,
		if (erHoveddokument) "https://cdn.sanity.io/files/gx9wf39f/soknadsveiviser-p/1b736c8e28abcb80f654166318f130e5ed2a0aad.pdf" else null
	)

}

fun writeBytesToFile(data: ByteArray, filePath: String) {
	File(filePath).writeBytes(data)
}

fun createHeaders(token: String): HttpHeaders {
	val headers = HttpHeaders()
	headers.contentType = MediaType.APPLICATION_JSON
	headers.add(HttpHeaders.AUTHORIZATION, "$BEARER$token")
	headers.add(Constants.CORRELATION_ID, UUID.randomUUID().toString())
	return headers
}

fun createHeaders(token: String, contentType: MediaType): HttpHeaders {
	val headers = HttpHeaders()
	headers.contentType = contentType
	headers.add(HttpHeaders.AUTHORIZATION, "$BEARER$token")
	headers.add(Constants.CORRELATION_ID, UUID.randomUUID().toString())
	return headers
}

fun getBytesFromFile(path: String): ByteArray {
	val resourceAsStream = SoknadServiceTest::class.java.getResourceAsStream(path)
	val outputStream = ByteArrayOutputStream()
	resourceAsStream.use { input ->
		outputStream.use { output ->
			input!!.copyTo(output)
		}
	}
	return outputStream.toByteArray()
}

// Brukes for å skrive fil til disk for manuell sjekk av innhold.
fun writeBytesToFile(navn: String, suffix: String, innhold: ByteArray?) {
	val dest = kotlin.io.path.createTempFile(navn, suffix)

//		if (innhold != null)	dest.toFile().writeBytes(innhold)
}

fun lagFilDtoMedFil(vedleggDto: VedleggDto): FilDto {
	val fil = getBytesFromFile("/litenPdf.pdf")
	return FilDto(
		vedleggDto.id!!, null, "OpplastetFil.pdf",
		Mimetype.applicationSlashPdf, fil.size, fil, OffsetDateTime.now()
	)
}
