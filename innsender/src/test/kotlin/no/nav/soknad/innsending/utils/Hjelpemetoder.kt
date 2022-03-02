package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.service.SoknadServiceTest
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.*


fun lagDokumentSoknad(brukerId: String, skjemanr: String, spraak: String, tittel: String, tema: String, id: Long? = null
											, innsendingsid: String? = null, soknadsStatus: SoknadsStatus = SoknadsStatus.Opprettet
											, vedleggsListe: List<VedleggDto>? = null, ettersendingsId: String? = null
											): DokumentSoknadDto {

	// Lag varianter av hoveddokument
	val vedleggDtoPdf = lagVedlegg(1L, skjemanr, tittel, OpplastingsStatus.LASTET_OPP, true,"/litenPdf.pdf"  )
	val vedleggDtoJson = lagVedlegg(2L, skjemanr, tittel, OpplastingsStatus.LASTET_OPP, true,"/sanity.json"  )

	val vedleggDtoList = listOf(vedleggDtoPdf, vedleggDtoJson) + (vedleggsListe ?: emptyList())

	return DokumentSoknadDto(id, innsendingsid, ettersendingsId, brukerId, skjemanr, tittel, tema, spraak,
		soknadsStatus, LocalDateTime.now(), LocalDateTime.now(), if (soknadsStatus == SoknadsStatus.Innsendt) LocalDateTime.now() else null, vedleggDtoList)
}

fun oppdaterDokumentSoknad(dokumentSoknadDto: DokumentSoknadDto): DokumentSoknadDto {
	val vedleggDto = lastOppDokumentTilVedlegg(dokumentSoknadDto.vedleggsListe[0])
	val vedleggDtoListe = if (dokumentSoknadDto.vedleggsListe.size>1) listOf(dokumentSoknadDto.vedleggsListe[1]) else listOf()
	return DokumentSoknadDto(dokumentSoknadDto.id, dokumentSoknadDto.innsendingsId, dokumentSoknadDto.ettersendingsId,
		dokumentSoknadDto.brukerId, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tittel, dokumentSoknadDto.tema,
		dokumentSoknadDto.spraak, SoknadsStatus.Opprettet, dokumentSoknadDto.opprettetDato, LocalDateTime.now(),
		null, listOf(vedleggDto) + vedleggDtoListe)
}

fun lagVedlegg(id: Long? = null, vedleggsnr: String, tittel: String
							 , opplastingsStatus: OpplastingsStatus = OpplastingsStatus.IKKE_VALGT
							 , erHoveddokument: Boolean = false, vedleggsNavn: String? = null): VedleggDto {
	return VedleggDto(id, vedleggsnr, tittel, tittel, "Beskrivelse", UUID.randomUUID().toString()
		, if (vedleggsNavn != null && vedleggsNavn.contains(".pdf")) "application/pdf" else "application/json"
		, if (opplastingsStatus == OpplastingsStatus.LASTET_OPP && vedleggsNavn != null) getBytesFromFile(vedleggsNavn) else null
		, erHoveddokument, if (vedleggsNavn == null || (vedleggsNavn != null && vedleggsNavn.contains(".pdf"))) false else true, true, true
		, if (erHoveddokument) "https://cdn.sanity.io/files/gx9wf39f/soknadsveiviser-p/1b736c8e28abcb80f654166318f130e5ed2a0aad.pdf" else null
		,opplastingsStatus, LocalDateTime.now())
}


fun lastOppDokumentTilVedlegg(vedleggDto: VedleggDto) =
	VedleggDto(vedleggDto.id, vedleggDto.vedleggsnr, vedleggDto.tittel, vedleggDto.tittel, vedleggDto.beskrivelse, UUID.randomUUID().toString(),
		"application/pdf", getBytesFromFile("/litenPdf.pdf"), true, erVariant = false,
		true, true, vedleggDto.skjemaurl, OpplastingsStatus.LASTET_OPP, LocalDateTime.now())

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
