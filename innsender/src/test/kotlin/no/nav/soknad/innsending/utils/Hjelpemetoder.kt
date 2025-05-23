package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.service.SoknadServiceTest
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.BEARER
import no.nav.soknad.innsending.util.Skjema
import no.nav.soknad.innsending.util.mapping.mapTilMimetype
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
			brukerId: String = "12345678901",
			skjemanr: String = "NAV 55-00.60",
			spraak: String = "no",
			tittel: String = "Testskjema",
			tema: String = "BID",
			id: Long? = null,
			innsendingsid: String? = null,
			soknadsStatus: SoknadsStatusDto? = SoknadsStatusDto.Opprettet,
			vedleggsListe: List<VedleggDto>? = null,
			ettersendingsId: String? = null,
			opprettetDato: OffsetDateTime? = OffsetDateTime.now(),
			soknadstype: SoknadType = SoknadType.soknad,
			innsendtDato: OffsetDateTime? = if (soknadsStatus == SoknadsStatusDto.Innsendt) OffsetDateTime.now() else null
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
				innsendtDato = innsendtDato,
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
			opplastingsStatus: OpplastingsStatusDto = OpplastingsStatusDto.IkkeValgt,
			erHoveddokument: Boolean = false,
			vedleggsNavn: String? = null,
			label: String? = null,
			erVariant: Boolean? = false
		): VedleggDto =
			lagVedleggDto(
				vedleggsnr = vedleggsnr,
				tittel = tittel,
				mimeType = if (opplastingsStatus == OpplastingsStatusDto.LastetOpp)
					(if (vedleggsNavn != null && vedleggsNavn.contains(".pdf"))
						Mimetype.applicationSlashPdf.value
					else
						Mimetype.applicationSlashJson.value)
				else null,
				if (opplastingsStatus == OpplastingsStatusDto.LastetOpp && vedleggsNavn != null)
					getBytesFromFile(vedleggsNavn)
				else null,
				id, erHoveddokument, erVariant = erVariant, erPakrevd = false, label = label
			)


		fun lagVedleggDto(
			vedleggsnr: String,
			tittel: String,
			mimeType: String? = "application/pdf",
			fil: ByteArray?,
			id: Long? = null,
			erHoveddokument: Boolean? = true,
			erVariant: Boolean? = false,
			erPakrevd: Boolean? = true,
			label: String? = null,
			formioId: String? = null,
		): VedleggDto {
			return VedleggDto(
				tittel = tittel,
				label = label ?: tittel,
				erHoveddokument = erHoveddokument!!,
				erVariant = erVariant!!,
				mimetype = mapTilMimetype(mimeType),
				erPakrevd = erPakrevd!!,
				opplastingsStatus = if (fil != null) OpplastingsStatusDto.LastetOpp else OpplastingsStatusDto.IkkeValgt,
				opprettetdato = OffsetDateTime.now(),
				id = id,
				vedleggsnr = vedleggsnr,
				beskrivelse = "Beskrivelse",
				uuid = UUID.randomUUID().toString(),
				document = fil,
				skjemaurl = if (erHoveddokument) "https://cdn.sanity.io/files/gx9wf39f/soknadsveiviser-p/1b736c8e28abcb80f654166318f130e5ed2a0aad.pdf" else null,
				formioId = formioId,
				erPdfa = false
			)

		}

		fun createHeaders(token: String, map: Map<String, String>? = mapOf()): HttpHeaders {
			val headers = HttpHeaders()
			headers.contentType = MediaType.APPLICATION_JSON
			headers.add(HttpHeaders.AUTHORIZATION, "$BEARER$token")
			headers.add(Constants.CORRELATION_ID, UUID.randomUUID().toString())
			map?.forEach { (headerName, headerValue) -> headers.add(headerName, headerValue) }
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

		fun lagFilDtoMedFil(vedleggDto: VedleggDto, fil: ByteArray = getBytesFromFile("/litenPdf.pdf")): FilDto {
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

