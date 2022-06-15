package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.SoknadServiceTest
import no.nav.soknad.innsending.util.Constants
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.util.*


fun lagDokumentSoknad(brukerId: String, skjemanr: String, spraak: String, tittel: String, tema: String, id: Long? = null
											, innsendingsid: String? = null, soknadsStatus: SoknadsStatusDto? = SoknadsStatusDto.opprettet
											, vedleggsListe: List<VedleggDto>? = null, ettersendingsId: String? = null
): DokumentSoknadDto {
	val vedleggDtoPdf = lagVedleggDto(skjemanr, tittel, "application/pdf", getBytesFromFile("/litenPdf.pdf"))
	val vedleggDtoJson = lagVedleggDto(skjemanr, tittel, "application/json", getBytesFromFile("/sanity.json"))

	val vedleggDtoList = listOf(vedleggDtoPdf, vedleggDtoJson)
	return DokumentSoknadDto( brukerId, skjemanr, tittel, tema, soknadsStatus!!, OffsetDateTime.now(),
		vedleggDtoList, id, innsendingsid ?: UUID.randomUUID().toString(), ettersendingsId, spraak,
		OffsetDateTime.now(), null )
}

fun oppdaterDokumentSoknad(dokumentSoknadDto: DokumentSoknadDto): DokumentSoknadDto {
	val vedleggDto = lastOppDokumentTilVedlegg(dokumentSoknadDto.vedleggsListe[0])
	val vedleggDtoListe = if (dokumentSoknadDto.vedleggsListe.size>1) listOf(dokumentSoknadDto.vedleggsListe[1]) else listOf()
	return DokumentSoknadDto(dokumentSoknadDto.brukerId, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tittel,
		dokumentSoknadDto.tema, SoknadsStatusDto.opprettet, dokumentSoknadDto.opprettetDato, listOf(vedleggDto) + vedleggDtoListe,
		dokumentSoknadDto.id, dokumentSoknadDto.innsendingsId, dokumentSoknadDto.ettersendingsId,
		dokumentSoknadDto.spraak, OffsetDateTime.now(), null )
}

fun lagVedlegg(id: Long? = null, vedleggsnr: String, tittel: String
							 , opplastingsStatus: OpplastingsStatusDto = OpplastingsStatusDto.ikkeValgt
							 , erHoveddokument: Boolean = false, vedleggsNavn: String? = null, label: String? = null): VedleggDto =
	lagVedleggDto(vedleggsnr, tittel,
		if (opplastingsStatus.equals(OpplastingsStatusDto.lastetOpp) )
			(if (vedleggsNavn != null && vedleggsNavn.contains(".pdf")) "application/pdf" else "application/json") else null,
		if (opplastingsStatus.equals(OpplastingsStatusDto.lastetOpp) && vedleggsNavn != null) getBytesFromFile(vedleggsNavn) else null,
		id, erHoveddokument, false,  false, label )


fun lagVedleggDto(skjemanr: String, tittel: String, mimeType: String?, fil: ByteArray?, id: Long? = null,
													erHoveddokument: Boolean? = true, erVariant: Boolean? = false, erPakrevd: Boolean? = true, label: String? = null ): VedleggDto {
	return  VedleggDto( tittel, label ?: tittel, erHoveddokument!!, erVariant!!,
		if ("application/pdf".equals(mimeType, true)) true else false, erPakrevd!!,
		if (fil != null) OpplastingsStatusDto.lastetOpp else OpplastingsStatusDto.ikkeValgt,  OffsetDateTime.now(), id,
		skjemanr,"Beskrivelse", UUID.randomUUID().toString(), Mimetype.applicationSlashPdf, fil,
		if (erHoveddokument) "https://cdn.sanity.io/files/gx9wf39f/soknadsveiviser-p/1b736c8e28abcb80f654166318f130e5ed2a0aad.pdf" else null)

}

fun lastOppDokumentTilVedlegg(vedleggDto: VedleggDto) =
	lagVedleggDto(vedleggDto.vedleggsnr ?: "N6", vedleggDto.tittel, "application/pdf",
		getBytesFromFile("/litenPdf.pdf"), vedleggDto.id)

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

fun createHeaders(token: String): HttpHeaders {
	val headers = HttpHeaders()
	headers.contentType = MediaType.APPLICATION_JSON
	headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token)
	headers.add(Constants.CORRELATION_ID, UUID.randomUUID().toString())
	return headers
}
