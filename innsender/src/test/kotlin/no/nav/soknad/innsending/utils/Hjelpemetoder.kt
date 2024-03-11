package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.SoknadServiceTest
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.BEARER
import no.nav.soknad.innsending.util.Skjema
import no.nav.soknad.pdfutilities.AntallSider
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.time.OffsetDateTime
import java.util.*

class Hjelpemetoder {
	companion object {
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
			opprettetDato: OffsetDateTime? = OffsetDateTime.now(),
			soknadstype: SoknadType = SoknadType.soknad
		): DokumentSoknadDto {
			val vedleggDtoPdf = lagVedleggDto(
				vedleggsnr = skjemanr,
				tittel = tittel,
				mimeType = "application/pdf",
				fil = getBytesFromFile("/litenPdf.pdf"),
				erHoveddokument = true,
				erVariant = false
			)
			val vedleggDtoJson = lagVedleggDto(
				vedleggsnr = skjemanr,
				tittel = tittel,
				mimeType = "application/json",
				fil = getBytesFromFile("/__files/sanity.json"),
				erHoveddokument = true,
				erVariant = true
			)

			val vedleggDtoList = vedleggsListe ?: listOf(vedleggDtoPdf, vedleggDtoJson)
			return DokumentSoknadDto(
				brukerId = brukerId,
				skjemanr = skjemanr,
				tittel = tittel,
				tema = tema,
				status = soknadsStatus!!,
				opprettetDato = opprettetDato ?: OffsetDateTime.now(),
				vedleggsListe = vedleggDtoList,
				id = id,
				innsendingsId = innsendingsid ?: UUID.randomUUID().toString(),
				ettersendingsId = ettersendingsId,
				spraak = spraak,
				endretDato = OffsetDateTime.now(),
				innsendtDato = null,
				soknadstype = soknadstype,
				skjemaPath = Skjema.createSkjemaPathFromSkjemanr(skjemanr),
				visningsType = VisningsType.fyllUt,
				applikasjon = "application"
			)
		}


		fun lagVedlegg(
			id: Long? = null,
			vedleggsnr: String,
			tittel: String,
			opplastingsStatus: OpplastingsStatusDto = OpplastingsStatusDto.ikkeValgt,
			erHoveddokument: Boolean = false,
			vedleggsNavn: String? = null,
			label: String? = null,
			erVariant: Boolean? = false
		): VedleggDto =
			lagVedleggDto(
				vedleggsnr, tittel,
				if (opplastingsStatus == OpplastingsStatusDto.lastetOpp)
					(if (vedleggsNavn != null && vedleggsNavn.contains(".pdf")) "application/pdf" else "application/json") else null,
				if (opplastingsStatus == OpplastingsStatusDto.lastetOpp && vedleggsNavn != null) getBytesFromFile(
					vedleggsNavn
				) else null,
				id, erHoveddokument, erVariant = erVariant, erPakrevd = false, label = label
			)


		fun lagVedleggDto(
			vedleggsnr: String,
			tittel: String,
			mimeType: String?,
			fil: ByteArray?,
			id: Long? = null,
			erHoveddokument: Boolean? = true,
			erVariant: Boolean? = false,
			erPakrevd: Boolean? = true,
			label: String? = null,
			formioId: String? = null,
		): VedleggDto {
			return VedleggDto(
				tittel,
				label ?: tittel,
				erHoveddokument!!,
				erVariant!!,
				"application/pdf".equals(mimeType, true),
				erPakrevd!!,
				if (fil != null) OpplastingsStatusDto.lastetOpp else OpplastingsStatusDto.ikkeValgt,
				OffsetDateTime.now(),
				id,
				vedleggsnr,
				"Beskrivelse",
				UUID.randomUUID().toString(),
				Mimetype.applicationSlashPdf,
				fil,
				if (erHoveddokument) "https://cdn.sanity.io/files/gx9wf39f/soknadsveiviser-p/1b736c8e28abcb80f654166318f130e5ed2a0aad.pdf" else null,
				formioId = formioId
			)

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

		fun lagFilDtoMedFil(vedleggDto: VedleggDto): FilDto {
			val fil = getBytesFromFile("/litenPdf.pdf")
			return FilDto(
				vedleggDto.id!!,
				id = null,
				filnavn = "OpplastetFil.pdf",
				mimetype = Mimetype.applicationSlashPdf,
				storrelse = fil.size,
				antallsider = AntallSider().finnAntallSider(fil),
				data = fil,
				opprettetdato = OffsetDateTime.now()
			)
		}

		fun writeBytesToFile(byteArray: ByteArray, filePath: String) {
			val outputStream = FileOutputStream(filePath)
			outputStream.write(byteArray)
			outputStream.close()
		}

	}
}

